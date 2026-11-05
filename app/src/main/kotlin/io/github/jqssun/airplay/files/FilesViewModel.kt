package io.github.jqssun.airplay.files

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

enum class FilesStage { PICK_MAC, LOGIN, PICK_SHARE, BROWSE }

/** A mac advertising File Sharing (_smb._tcp) on the local network. */
data class FoundMac(val name: String, val service: NsdServiceInfo)

data class Transfer(val name: String, val progress: Float)

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _stage = MutableStateFlow(FilesStage.PICK_MAC)
    val stage = _stage.asStateFlow()

    private val _macs = MutableStateFlow<List<FoundMac>>(emptyList())
    val macs = _macs.asStateFlow()

    /** host/label chosen on the pick screen, waiting for a login */
    private val _target = MutableStateFlow<Pair<String, String>?>(null)
    val target = _target.asStateFlow()

    private val _shares = MutableStateFlow<List<String>>(emptyList())
    val shares = _shares.asStateFlow()

    private val _login = MutableStateFlow<MacLogin?>(null)
    val login = _login.asStateFlow()

    private val _path = MutableStateFlow("")
    val path = _path.asStateFlow()

    private val _entries = MutableStateFlow<List<MacEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer = _transfer.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _intents = Channel<Intent>(Channel.BUFFERED)
    val intents = _intents.receiveAsFlow()

    private var listJob: Job? = null
    private var transferJob: Job? = null

    init {
        val saved = loadLogin()
        if (saved?.share != null) {
            _login.value = saved
            MacShare.use(saved)
            _stage.value = FilesStage.BROWSE
            refresh()
        }
    }

    // --- discovery ---

    private var discovery: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        if (discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "smb discovery failed: $errorCode")
                discovery = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                val list = _macs.value
                if (list.none { it.name == info.serviceName }) {
                    _macs.value = (list + FoundMac(info.serviceName, info)).sortedBy { it.name.lowercase() }
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _macs.value = _macs.value.filterNot { it.name == info.serviceName }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "discoverServices", it); discovery = null }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }

    @Suppress("DEPRECATION")
    fun chooseMac(mac: FoundMac) {
        _error.value = null
        _busy.value = true
        nsd.resolveService(mac.service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                _busy.value = false
                _error.value = "Couldn't reach ${mac.name}. Try entering its address instead."
            }
            override fun onServiceResolved(info: NsdServiceInfo) {
                _busy.value = false
                val host = info.host?.hostAddress
                if (host == null) {
                    _error.value = "Couldn't reach ${mac.name}. Try entering its address instead."
                    return
                }
                goToLogin(host, mac.name)
            }
        })
    }

    fun chooseAddress(address: String) {
        val host = address.trim().removePrefix("smb://").trimEnd('/')
        if (host.isEmpty()) return
        goToLogin(host, host)
    }

    private fun goToLogin(host: String, label: String) {
        _error.value = null
        _target.value = host to label
        _stage.value = FilesStage.LOGIN
    }

    // --- login and share ---

    fun signIn(user: String, password: String) {
        val (host, label) = _target.value ?: return
        val attempt = MacLogin(host = host, label = label, user = user.trim(), password = password)
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MacShare.use(attempt)
                    MacShare.shares()
                }
            }
            _busy.value = false
            result.onSuccess { shares ->
                _login.value = attempt
                _shares.value = shares
                if (shares.size == 1) chooseShare(shares.first()) else _stage.value = FilesStage.PICK_SHARE
            }.onFailure {
                Log.w(TAG, "sign in failed", it)
                MacShare.use(null)
                _error.value = explain(it)
            }
        }
    }

    fun chooseShare(name: String) {
        val base = _login.value ?: return
        val l = base.copy(share = name.trim())
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MacShare.use(l)
                    MacShare.list("")
                }
            }
            _busy.value = false
            result.onSuccess {
                _login.value = l
                saveLogin(l)
                _path.value = ""
                _entries.value = it
                _stage.value = FilesStage.BROWSE
                stopDiscovery()
            }.onFailure {
                _error.value = "Couldn't open \"$name\": ${explain(it)}"
            }
        }
    }

    fun back(): Boolean = when (_stage.value) {
        FilesStage.PICK_MAC -> false
        FilesStage.LOGIN -> { _error.value = null; _stage.value = FilesStage.PICK_MAC; true }
        FilesStage.PICK_SHARE -> { _error.value = null; _stage.value = FilesStage.LOGIN; true }
        FilesStage.BROWSE -> {
            val p = _path.value
            if (p.isEmpty()) false else { open(p.substringBeforeLast('/', "")); true }
        }
    }

    /** Forget the saved mac and start over. */
    fun forget() {
        transferJob?.cancel()
        prefs.edit().clear().apply()
        MacShare.use(null)
        _login.value = null
        _entries.value = emptyList()
        _path.value = ""
        _error.value = null
        _stage.value = FilesStage.PICK_MAC
    }

    // --- browsing ---

    fun open(path: String) {
        _path.value = path
        refresh()
    }

    fun refresh() {
        val path = _path.value
        listJob?.cancel()
        _busy.value = true
        _error.value = null
        listJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { MacShare.list(path) } }
            if (_path.value != path) return@launch
            _busy.value = false
            result.onSuccess { _entries.value = it }
                .onFailure {
                    Log.w(TAG, "list failed", it)
                    _entries.value = emptyList()
                    _error.value = explain(it)
                }
        }
    }

    // --- copying to the tv ---

    /** Copy into Download/Mirra so other apps (and the file manager) can see it. */
    fun saveToTv(entry: MacEntry) = copy(entry) { name, size, input ->
        val where = publish(name, size, input)
        _messages.trySend("Saved to $where")
    }

    /** Files mirra can't show itself: pull a private copy and hand it to whichever app opens it. */
    fun openWithApp(entry: MacEntry) = copy(entry) { name, size, input ->
        val ctx = getApplication<Application>()
        val dir = File(ctx.cacheDir, "opened").apply { deleteRecursively(); mkdirs() }
        val dest = File(dir, name)
        dest.outputStream().use { pump(input, it, size) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dest)
        val mime = mimeOf(name)
        _intents.trySend(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        _transfer.value = null
    }

    private fun copy(entry: MacEntry, sink: suspend (String, Long, InputStream) -> Unit) {
        if (_transfer.value != null) return
        _transfer.value = Transfer(entry.name, 0f)
        transferJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MacShare.open(entry.path).use { f ->
                        f.inputStream.use { sink(entry.name, entry.size, it) }
                    }
                }
            }
            _transfer.value = null
            result.onFailure {
                if (it is kotlinx.coroutines.CancellationException) return@onFailure
                Log.w(TAG, "copy failed", it)
                _messages.trySend("Couldn't copy ${entry.name}: ${explain(it)}")
            }
        }
    }

    private fun pump(input: InputStream, out: OutputStream, size: Long) {
        val buf = ByteArray(256 * 1024)
        var done = 0L
        var lastReport = 0L
        while (true) {
            if (transferJob?.isCancelled == true) throw kotlinx.coroutines.CancellationException()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            if (size > 0 && done - lastReport > 512 * 1024) {
                lastReport = done
                _transfer.value = _transfer.value?.copy(progress = (done.toFloat() / size).coerceIn(0f, 1f))
            }
        }
    }

    private fun publish(name: String, size: Long, input: InputStream): String {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Mirra")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("No room in Downloads")
            try {
                resolver.openOutputStream(uri)!!.use { pump(input, it, size) }
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "${Environment.DIRECTORY_DOWNLOADS}/Mirra"
        }
        // before android 10 public storage needs a runtime permission; keep it in mirra's own folder
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Mirra").apply { mkdirs() }
        File(dir, name).outputStream().use { pump(input, it, size) }
        return dir.absolutePath
    }

    // --- persistence ---

    private fun loadLogin(): MacLogin? {
        val host = prefs.getString(K_HOST, null) ?: return null
        return MacLogin(
            host = host,
            label = prefs.getString(K_LABEL, host) ?: host,
            user = prefs.getString(K_USER, "") ?: "",
            password = prefs.getString(K_PASS, "") ?: "",
            share = prefs.getString(K_SHARE, null),
        )
    }

    private fun saveLogin(l: MacLogin) {
        prefs.edit()
            .putString(K_HOST, l.host)
            .putString(K_LABEL, l.label)
            .putString(K_USER, l.user)
            .putString(K_PASS, l.password)
            .putString(K_SHARE, l.share)
            .apply()
    }

    override fun onCleared() {
        stopDiscovery()
    }

    companion object {
        private const val TAG = "FilesViewModel"
        private const val PREFS = "mac_files"
        private const val K_HOST = "host"
        private const val K_LABEL = "label"
        private const val K_USER = "user"
        private const val K_PASS = "password"
        private const val K_SHARE = "share"

        fun mimeOf(name: String): String =
            if (name.endsWith(".apk", true)) "application/vnd.android.package-archive"
            else MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "*/*"

        fun explain(e: Throwable): String {
            val msg = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
            return when {
                "STATUS_LOGON_FAILURE" in msg -> "Wrong user name or password. On the Mac, also turn on Windows File Sharing for this account (File Sharing → Options)."
                "STATUS_ACCESS_DENIED" in msg -> "This account isn't allowed to open that folder."
                "STATUS_BAD_NETWORK_NAME" in msg -> "That shared folder doesn't exist on the Mac."
                "STATUS_OBJECT_NAME_NOT_FOUND" in msg || "STATUS_OBJECT_PATH_NOT_FOUND" in msg -> "That file or folder is gone."
                e is java.net.ConnectException || "Connection refused" in msg ->
                    "The Mac refused the connection. Check that File Sharing is on."
                e is java.net.SocketTimeoutException || e is java.net.UnknownHostException || "timed out" in msg ->
                    "The Mac didn't answer. Is it awake and on the same network?"
                else -> e.message ?: e.javaClass.simpleName
            }
        }
    }
}
