package io.github.jqssun.airplay.files

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** Where to reach the mac and how to log in; share is null until one is picked. */
data class MacLogin(
    val host: String,
    val label: String,
    val user: String,
    val password: String,
    val share: String? = null,
)

data class MacEntry(
    val name: String,
    /** path inside the share, '/'-separated, no leading slash */
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedMs: Long,
) {
    val kind: FileKind get() = if (isDir) FileKind.FOLDER else FileKind.of(name)
}

enum class FileKind {
    FOLDER, VIDEO, AUDIO, IMAGE, APK, OTHER;

    companion object {
        private val video = setOf("mp4", "m4v", "mkv", "mov", "webm", "avi", "ts", "m2ts", "mpg", "mpeg", "3gp", "flv", "wmv")
        private val audio = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "alac", "aif", "aiff", "wma")
        private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

        fun of(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
            in video -> VIDEO
            in audio -> AUDIO
            in image -> IMAGE
            "apk" -> APK
            else -> OTHER
        }
    }
}

/**
 * One SMB session to the mac's File Sharing, shared by the browser and the media data source.
 * Every call blocks; run them off the main thread.
 */
object MacShare {
    private const val TAG = "MacShare"

    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(20, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            // android ships a stripped jce provider without md4; use bouncycastle directly for ntlm
            .withSecurityProvider(BCSecurityProvider())
            .withMultiProtocolNegotiate(true)
            .build()
    )

    private var login: MacLogin? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var disk: DiskShare? = null

    @Synchronized
    fun use(newLogin: MacLogin?) {
        if (newLogin == login) return
        close()
        login = newLogin
    }

    @Synchronized
    fun close() {
        runCatching { disk?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        disk = null; session = null; connection = null
    }

    @Synchronized
    private fun session(): Session {
        val l = login ?: throw IOException("Not connected to a Mac")
        session?.let { s -> if (connection?.isConnected == true) return s }
        close()
        val c = client.connect(l.host)
        connection = c
        val s = c.authenticate(AuthenticationContext(l.user, l.password.toCharArray(), ""))
        session = s
        return s
    }

    @Synchronized
    private fun disk(): DiskShare {
        disk?.let { d -> if (d.isConnected && connection?.isConnected == true) return d }
        val name = login?.share ?: throw IOException("No shared folder selected")
        val share = session().connectShare(name) as? DiskShare
            ?: throw IOException("\"$name\" is not a folder share")
        disk = share
        return share
    }

    /** Checks the login and lists the visible folder shares (hidden `$` shares are left out). */
    @Synchronized
    fun shares(): List<String> {
        val s = session()
        return try {
            ServerService(SMBTransportFactories.SRVSVC.getTransport(s)).shares1
                .filter { it.type and 0xFF == 0 && !it.netName.endsWith("$") }
                .map { it.netName }
                .sortedBy { it.lowercase() }
        } catch (e: Exception) {
            Log.w(TAG, "share enumeration failed", e)
            emptyList()
        }
    }

    fun list(path: String): List<MacEntry> = retrying {
        disk().list(smbPath(path))
            .asSequence()
            .filter { it.fileName != "." && it.fileName != ".." && !it.fileName.startsWith(".") }
            .filter { it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value == 0L }
            .map {
                MacEntry(
                    name = it.fileName,
                    path = if (path.isEmpty()) it.fileName else "$path/${it.fileName}",
                    isDir = it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L,
                    size = it.endOfFile,
                    modifiedMs = it.lastWriteTime.toEpochMillis(),
                )
            }
            .sortedWith(compareBy<MacEntry> { !it.isDir }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun open(path: String): File = retrying {
        disk().openFile(
            smbPath(path),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
    }

    // a stale connection (mac slept, wifi blipped) fails once; reconnect and try again
    private fun <T> retrying(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "smb call failed, reconnecting", e)
        close()
        block()
    }

    private fun smbPath(path: String) = path.replace('/', '\\')
}
