package io.github.jqssun.airplay.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.jqssun.airplay.ui.MirraPage
import io.github.jqssun.airplay.ui.Pill
import io.github.jqssun.airplay.ui.VideoSurfaceView
import io.github.jqssun.airplay.ui.requestFocusUntilLanded
import io.github.jqssun.airplay.ui.theme.Mirra
import io.github.jqssun.airplay.ui.theme.Nunito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Browse a shared folder on the mac (File Sharing / SMB), play media from it, or copy files to the tv. */
@Composable
fun FilesScreen(onExit: () -> Unit) {
    val vm: FilesViewModel = viewModel()
    val ctx = LocalContext.current
    val stage by vm.stage.collectAsState()
    var playing by remember { mutableStateOf<MacEntry?>(null) }
    var viewing by remember { mutableStateOf<Pair<List<MacEntry>, Int>?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
    LaunchedEffect(Unit) {
        vm.intents.collect {
            try {
                ctx.startActivity(it)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, "No app on this TV can open that file", Toast.LENGTH_LONG).show()
            }
        }
    }
    DisposableEffect(stage) {
        if (stage == FilesStage.PICK_MAC) vm.startDiscovery()
        onDispose { vm.stopDiscovery() }
    }

    playing?.let { entry ->
        BackHandler { playing = null }
        MacPlayer(entry, onClose = { playing = null })
        return
    }
    viewing?.let { (images, index) ->
        BackHandler { viewing = null }
        ImageViewer(images, index, onClose = { viewing = null })
        return
    }

    BackHandler { if (!vm.back()) onExit() }
    val login by vm.login.collectAsState()
    val title = when (stage) {
        FilesStage.BROWSE -> login?.let { it.share ?: it.label } ?: "Mac files"
        else -> "Mac files"
    }
    val backFocus = remember { FocusRequester() }
    MirraPage(
        title = title,
        onBack = { if (!vm.back()) onExit() },
        backFocus = backFocus,
        actions = {
            if (stage == FilesStage.BROWSE) {
                Pill("Refresh", Icons.Rounded.Refresh, onClick = { vm.refresh() })
                Spacer(Modifier.width(12.dp))
                Pill("Change Mac", Icons.Rounded.LinkOff, onClick = { vm.forget() })
            }
        }
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
            when (stage) {
                FilesStage.PICK_MAC -> PickMac(vm, backFocus)
                FilesStage.LOGIN -> Login(vm)
                FilesStage.PICK_SHARE -> PickShare(vm)
                FilesStage.BROWSE -> Browser(
                    vm,
                    onPlay = { playing = it },
                    onView = { list, i -> viewing = list to i },
                )
            }
        }
    }
}

// --- setup ---

@Composable
private fun PickMac(vm: FilesViewModel, backFocus: FocusRequester) {
    val macs by vm.macs.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var address by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(macs.isNotEmpty()) {
        if (macs.isNotEmpty() && !focusedOnce) { focusedOnce = first.requestFocusUntilLanded() }
    }
    LaunchedEffect(Unit) { if (macs.isEmpty()) backFocus.requestFocusUntilLanded() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Hint(
                "On your Mac: System Settings → General → Sharing → turn on File Sharing, and add the folder " +
                    "you want to see here. Under Options, turn on Windows File Sharing for your account. " +
                    "Then pick your Mac below."
            )
        }
        if (macs.isEmpty()) {
            item { Hint("Looking for Macs on this network…", muted = true) }
        }
        itemsIndexed(macs, key = { _, m -> m.name }) { i, mac ->
            EntryRow(
                icon = Icons.Rounded.LaptopMac,
                title = mac.name,
                subtitle = "File Sharing",
                onClick = { vm.chooseMac(mac) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            Hint("Or type the Mac's address (System Settings → Sharing → File Sharing shows it, e.g. 192.168.1.20):", muted = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    singleLine = true,
                    placeholder = { Text("192.168.1.20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.chooseAddress(address) }),
                    modifier = Modifier.width(420.dp),
                )
                Spacer(Modifier.width(16.dp))
                Pill("Connect", null, onClick = { vm.chooseAddress(address) })
            }
        }
        item { Status(busy, error) }
    }
}

@Composable
private fun Login(vm: FilesViewModel) {
    val target by vm.target.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var user by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val userFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { userFocus.requestFocusUntilLanded() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hint("Sign in to ${target?.second ?: "your Mac"} with your Mac account (the short user name, as in your home folder's name).")
        OutlinedTextField(
            value = user, onValueChange = { user = it }, singleLine = true,
            label = { Text("User name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.width(520.dp).focusRequester(userFocus),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it }, singleLine = true,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!busy) vm.signIn(user, password) }),
            modifier = Modifier.width(520.dp),
        )
        Row { Pill("Sign in", null, onClick = { if (!busy && user.isNotBlank()) vm.signIn(user, password) }) }
        Hint("Mirra keeps this login on the TV so next time it opens your files straight away.", muted = true)
        Status(busy, error)
    }
}

@Composable
private fun PickShare(vm: FilesViewModel) {
    val shares by vm.shares.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var manual by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(shares) { if (shares.isNotEmpty()) first.requestFocusUntilLanded() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Hint(
                if (shares.isEmpty()) "Type the name of the shared folder (as listed under File Sharing on the Mac)."
                else "Which shared folder should Mirra open?"
            )
        }
        itemsIndexed(shares) { i, name ->
            EntryRow(
                icon = Icons.Rounded.FolderShared, title = name, subtitle = null,
                onClick = { vm.chooseShare(name) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = manual, onValueChange = { manual = it }, singleLine = true,
                    label = { Text("Shared folder name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (manual.isNotBlank()) vm.chooseShare(manual) }),
                    modifier = Modifier.width(420.dp),
                )
                Spacer(Modifier.width(16.dp))
                Pill("Open", null, onClick = { if (manual.isNotBlank()) vm.chooseShare(manual) })
            }
        }
        item { Status(busy, error) }
    }
}

// --- browsing ---

@Composable
private fun Browser(
    vm: FilesViewModel,
    onPlay: (MacEntry) -> Unit,
    onView: (List<MacEntry>, Int) -> Unit,
) {
    val ctx = LocalContext.current
    val path by vm.path.collectAsState()
    val entries by vm.entries.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val listState = rememberLazyListState()
    val first = remember { FocusRequester() }

    // land on the first row whenever a folder finishes loading
    LaunchedEffect(path, entries) {
        if (entries.isNotEmpty()) {
            listState.scrollToItem(0)
            first.requestFocusUntilLanded()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "/" + path,
            fontFamily = Nunito, fontSize = 18.sp, color = Mirra.CreamSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        transfer?.let { t -> TransferBar(t, onCancel = { vm.cancelTransfer() }) }
        Status(busy && entries.isEmpty(), error)
        if (!busy && error == null && entries.isEmpty()) {
            Hint("This folder is empty. Drop files into it on your Mac, then press Refresh.", muted = true)
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(entries, key = { _, e -> e.path }) { i, entry ->
                val open = {
                    when (entry.kind) {
                        FileKind.FOLDER -> vm.open(entry.path)
                        FileKind.VIDEO, FileKind.AUDIO -> onPlay(entry)
                        FileKind.IMAGE -> {
                            val images = entries.filter { it.kind == FileKind.IMAGE }
                            onView(images, images.indexOf(entry))
                        }
                        FileKind.APK, FileKind.OTHER -> vm.openWithApp(entry)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryRow(
                        icon = iconFor(entry.kind),
                        title = entry.name,
                        subtitle = if (entry.isDir) null else Formatter.formatShortFileSize(ctx, entry.size),
                        onClick = open,
                        modifier = Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    )
                    if (!entry.isDir) {
                        Spacer(Modifier.width(12.dp))
                        Pill("Save to TV", Icons.Rounded.Download, onClick = { vm.saveToTv(entry) })
                    }
                }
            }
        }
    }
}

private fun iconFor(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Rounded.Folder
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.APK -> Icons.Rounded.Android
    FileKind.OTHER -> Icons.Rounded.Description
}

@Composable
private fun TransferBar(t: Transfer, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(Mirra.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Copying ${t.name}", fontFamily = Nunito, fontWeight = FontWeight.Bold, color = Mirra.Cream,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { t.progress }, color = Mirra.Orange, trackColor = Mirra.Border,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(16.dp))
        Pill("Cancel", null, onClick = onCancel)
    }
}

// --- shared bits ---

@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Mirra.SurfaceHigh else Mirra.Surface, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Mirra.Lavender else Mirra.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Mirra.Orange else Mirra.Lavender, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            title, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Mirra.Cream,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (subtitle != null) {
            Spacer(Modifier.width(16.dp))
            Text(subtitle, fontFamily = Nunito, fontSize = 16.sp, color = Mirra.CreamSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun Hint(text: String, muted: Boolean = false) {
    Text(
        text, fontFamily = Nunito, fontSize = 18.sp, lineHeight = 26.sp,
        color = if (muted) Mirra.CreamSecondary else Mirra.Cream,
        modifier = Modifier.padding(bottom = 6.dp).widthIn(max = 1100.dp)
    )
}

@Composable
private fun Status(busy: Boolean, error: String?) {
    if (busy) {
        CircularProgressIndicator(color = Mirra.Orange, modifier = Modifier.padding(vertical = 12.dp).size(36.dp))
    }
    if (error != null) {
        Text(
            error, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Mirra.Orange,
            modifier = Modifier.padding(vertical = 10.dp).widthIn(max = 1100.dp)
        )
    }
}

// --- player ---

@OptIn(UnstableApi::class)
private fun buildPlayer(ctx: Context): ExoPlayer =
    ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(MacDataSource.Factory()))
        .build()

@Composable
private fun MacPlayer(entry: MacEntry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val player = remember(entry.path) {
        buildPlayer(ctx).apply {
            setMediaItem(MediaItem.fromUri(MacDataSource.uriFor(entry.path)))
            prepare()
            playWhenReady = true
        }
    }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) onClose()
            }
            override fun onPlayerError(e: PlaybackException) {
                error = if (e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                ) "This TV can't play this file's format. Use Save to TV and open it with another player."
                else "Playback failed: ${FilesViewModel.explain(e)}"
            }
        }
        player.addListener(listener)
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    LaunchedEffect(overlayTick, isPlaying) {
        overlay = true
        if (isPlaying) { delay(3500); overlay = false }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusUntilLanded() }
    val seek = { delta: Long ->
        player.seekTo((player.currentPosition + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE))
        overlayTick++
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play(); overlayTick++; true
                    }
                    Key.MediaPlay -> { player.play(); overlayTick++; true }
                    Key.MediaPause -> { player.pause(); overlayTick++; true }
                    Key.DirectionLeft, Key.MediaRewind -> { seek(-10_000L); true }
                    Key.DirectionRight, Key.MediaFastForward -> { seek(10_000L); true }
                    Key.DirectionUp, Key.DirectionDown -> { overlayTick++; true }
                    else -> false
                }
            }
            .clickable { if (player.isPlaying) player.pause() else player.play(); overlayTick++ }
    ) {
        if (entry.kind == FileKind.AUDIO) {
            Icon(Icons.Rounded.MusicNote, null, tint = Mirra.Lavender, modifier = Modifier.size(160.dp))
        } else {
            VideoSurfaceView(
                onSurfaceAvailable = { s: Surface -> player.setVideoSurface(s) },
                onSurfaceDestroyed = { s: Surface -> player.clearVideoSurface(s) },
                aspectRatio = aspect,
            )
        }
        if (buffering && error == null) CircularProgressIndicator(color = Mirra.Orange, modifier = Modifier.size(64.dp))
        error?.let {
            Text(it, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Mirra.Cream,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp)).padding(24.dp))
        }
        if (overlay || entry.kind == FileKind.AUDIO) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 48.dp, vertical = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Mirra.Cream,
                        modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(entry.name, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                        color = Mirra.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${clock(position)} / ${clock(duration)}", fontFamily = Nunito, fontSize = 18.sp, color = Mirra.CreamSecondary)
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                    color = Mirra.Orange, trackColor = Mirra.Border, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("OK play/pause · ◀ ▶ 10 s · Back to close", fontFamily = Nunito, fontSize = 15.sp, color = Mirra.CreamSecondary)
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// --- images ---

@Composable
private fun ImageViewer(images: List<MacEntry>, start: Int, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, images.lastIndex)) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val entry = images[index]
    LaunchedEffect(entry.path) {
        bitmap = null
        error = null
        val result = withContext(Dispatchers.IO) { runCatching { decode(entry) } }
        result.onSuccess { bitmap = it ?: run { error = "Can't show this image"; null } }
            .onFailure { error = FilesViewModel.explain(it) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusUntilLanded() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { if (index > 0) index--; true }
                    Key.DirectionRight -> { if (index < images.lastIndex) index++; true }
                    Key.DirectionCenter, Key.Enter -> { onClose(); true }
                    else -> false
                }
            }
            .clickable { if (index < images.lastIndex) index++ else onClose() }
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = entry.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize())
        }
        if (bitmap == null && error == null) CircularProgressIndicator(color = Mirra.Orange)
        error?.let { Text(it, fontFamily = Nunito, fontSize = 22.sp, color = Mirra.Cream) }
        Text(
            "${entry.name}  ·  ${index + 1}/${images.size}",
            fontFamily = Nunito, fontSize = 16.sp, color = Mirra.CreamSecondary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

private const val MAX_IMAGE_BYTES = 80L * 1024 * 1024
private const val MAX_IMAGE_EDGE = 3840

private fun decode(entry: MacEntry): Bitmap? {
    if (entry.size > MAX_IMAGE_BYTES) throw java.io.IOException("Image is too large to show")
    val bytes = MacShare.open(entry.path).use { f -> f.inputStream.use { it.readBytes() } }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > MAX_IMAGE_EDGE || bounds.outHeight / sample > MAX_IMAGE_EDGE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}
