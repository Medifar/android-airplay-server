package io.github.jqssun.airplay.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.Mirra
import io.github.jqssun.airplay.ui.theme.Nunito
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import kotlin.math.min

enum class HomeStatus { READY, CONNECTING, MIRRORING, ERROR, STOPPED }

// approved figma frame; every position below is in its design pixels
private const val FRAME_W = 1920f
private const val FRAME_H = 1080f

// cable stroke from the cleanup handoff, in frame coordinates, kept as a path so it can draw on and carry the connecting dot
private const val CABLE_PATH =
    "M317 509.471C371.713 538.147 428.705 566.824 470.879 549.618C509.634 534.706 536.99 499.147 516.473 476.206C50" +
    "0.515 455.559 472.019 467.029 459.481 489.971C441.243 523.235 464.04 574.853 492.536 604.676C587.144 705.618 8" +
    "51.588 722.824 1043.08 717.088C1216.34 713.647 1320.07 690.706 1371.36 657.441C1399.85 639.088 1399.85 613.853" +
    " 1379.34 601.235C1359.96 588.618 1338.3 603.529 1341.72 623.029C1345.14 654 1379.34 670.059 1404.41 660.882C14" +
    "50.01 647.118 1479.64 603.529 1502.44 554.206"

@Composable
fun MirraHome(
    deviceName: String,
    status: HomeStatus,
    playIntro: Boolean,
    onIntroDone: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onRetry: () -> Unit,
    onShowScreen: () -> Unit,
) {
    val anims = animationsEnabled()
    val intro = playIntro && anims
    val charIn = remember { Animatable(if (intro) 0f else 1f) }
    val cable = remember { Animatable(if (intro) 0f else 1f) }
    val smile = remember { Animatable(1f) }
    val excited = remember { Animatable(1f) }
    val eyesLift = remember { Animatable(0f) }
    val content = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (intro) {
            charIn.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            cable.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
            coroutineScope {
                launch {
                    smile.animateTo(1.08f, tween(140))
                    smile.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
                launch {
                    excited.animateTo(0f, tween(110))
                    excited.animateTo(1f, tween(170))
                }
                launch {
                    // eyes lift as the cable connects, then settle
                    eyesLift.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                    delay(250)
                    eyesLift.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
                }
            }
            content.animateTo(1f, tween(500))
        }
        onIntroDone()
    }

    val settingsFocus = remember { FocusRequester() }
    val tv = isTv()
    LaunchedEffect(Unit) { if (tv) settingsFocus.requestFocusUntilLanded() }

    val network by rememberNetworkLabel()
    val dotColor by animateColorAsState(
        when (status) {
            HomeStatus.READY -> Mirra.Lavender
            HomeStatus.CONNECTING, HomeStatus.MIRRORING -> Mirra.Orange
            HomeStatus.ERROR, HomeStatus.STOPPED -> Mirra.Gray
        }, label = "dot"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Mirra.Background),
        contentAlignment = Alignment.Center
    ) {
        val fit = min(maxWidth.value / FRAME_W, maxHeight.value / FRAME_H)
        if (fit <= 0f) return@BoxWithConstraints
        val base = LocalDensity.current
        // one design pixel = one dp, then a single uniform fit; font scale is locked to keep the approved ratios
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * fit, fontScale = 1f),
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            Box(Modifier.requiredSize(FRAME_W.dp, FRAME_H.dp).clipToBounds()) {
                // illustrations
                Cable(
                    progress = { cable.value },
                    connecting = status == HomeStatus.CONNECTING && anims,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    Modifier
                        .place(109.091f, 206.589f, 258.373f, 443.018f)
                        .graphicsLayer {
                            alpha = charIn.value
                            translationX = -40.dp.toPx() * (1f - charIn.value)
                        }
                ) {
                    Art(R.drawable.mirra_phone_character)
                    Art(R.drawable.mirra_phone_eyes, Modifier.matchParentSize().graphicsLayer {
                        translationY = -10.dp.toPx() * eyesLift.value
                    })
                    Art(R.drawable.mirra_phone_excited, Modifier.matchParentSize().graphicsLayer { alpha = excited.value })
                }
                Box(
                    Modifier
                        .place(1469.86f, 136.578f, 450.14f, 560.085f)
                        .graphicsLayer {
                            alpha = charIn.value
                            translationX = 40.dp.toPx() * (1f - charIn.value)
                        }
                ) {
                    Art(R.drawable.mirra_tv_character)
                    Art(R.drawable.mirra_tv_eyes, Modifier.matchParentSize().graphicsLayer {
                        translationY = -16.dp.toPx() * eyesLift.value
                    })
                    Art(
                        R.drawable.mirra_tv_smile,
                        Modifier.matchParentSize().graphicsLayer {
                            scaleX = smile.value
                            scaleY = smile.value
                            transformOrigin = TransformOrigin(0.593f, 0.583f)
                        }
                    )
                }

                // copy, fades in after the cable connects
                Box(Modifier.matchParentSize().graphicsLayer { alpha = content.value }) {
                    if (deviceName == "Mirra") {
                        Art(R.drawable.mirra_mirra_wordmark, Modifier.place(712f, 315.622f, 492.632f, 154.942f))
                    } else {
                        // custom receiver names can't use the outlined wordmark
                        val size = when {
                            deviceName.length <= 8 -> 150f
                            deviceName.length <= 14 -> 104f
                            else -> 76f
                        }
                        Label(
                            deviceName, 360f, 315.622f, 1200f, 154.942f, size, FontWeight.Black, 154.942f,
                            centered = true
                        )
                    }
                    StatusDot(dotColor, pulse = status == HomeStatus.READY && anims)
                    Label(statusText(status), 796.899f, 489f, 760f, 57.386f, 45.909f, FontWeight.Normal, 57.386f)

                    Label(stringResource(R.string.home_on_iphone), 59.713f, 768.969f, 500f, 50f, 36.727f, FontWeight.ExtraBold, 49.352f)
                    Step(1, 59.713f, R.drawable.mirra_control_center, 163.062f, 842.423f, 81.531f, 86.079f, 275.598f,
                        stringResource(R.string.step_control_center), stringResource(R.string.step_control_center_desc))
                    Divider(600.574f)
                    Step(2, 664.880f, R.drawable.mirra_screen_mirroring, 763.636f, 848.735f, 82.679f, 73.454f, 873.876f,
                        stringResource(R.string.step_screen_mirroring), stringResource(R.string.step_screen_mirroring_desc))
                    Divider(1224.115f)
                    Step(3, 1270.048f, R.drawable.mirra_receiver, 1364.211f, 845.866f, 81.531f, 79.192f, 1476.747f,
                        deviceName, stringResource(R.string.step_select_desc))

                    Art(R.drawable.mirra_wifi_lavender, Modifier.place(60f, 976.272f, 44.714f, 34.175f))
                    Label(network.text, 120.002f, 977.149f, 900f, 33.298f, 24.535f, FontWeight.Bold, 33.298f)
                }

                // actions stay visible during the intro so the remote always has a target
                HomeAction(
                    label = stringResource(R.string.tab_settings),
                    icon = R.drawable.mirra_settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.place(1658.78f, 40.77f, 218.13f, 64.22f).focusRequester(settingsFocus),
                    iconStart = 30.407f, iconW = 41.34f, iconH = 42.465f, gap = 20.674f, textSize = 27f
                )
                HomeAction(
                    label = stringResource(R.string.home_cant_find, deviceName),
                    icon = R.drawable.mirra_help,
                    onClick = onOpenHelp,
                    modifier = Modifier.place(1601.37f, 965.829f, 280.14f, 64.22f),
                    iconStart = 24.659f, iconW = 41.34f, iconH = 45.909f, gap = 17.222f, textSize = 22f
                )
                when (status) {
                    HomeStatus.ERROR, HomeStatus.STOPPED -> CenterAction(stringResource(R.string.btn_try_again), onRetry)
                    HomeStatus.MIRRORING -> CenterAction(stringResource(R.string.btn_show_screen), onShowScreen)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun statusText(status: HomeStatus): String = stringResource(
    when (status) {
        HomeStatus.READY -> R.string.status_ready
        HomeStatus.CONNECTING -> R.string.status_connecting
        HomeStatus.MIRRORING -> R.string.status_mirroring
        HomeStatus.ERROR -> R.string.status_error
        HomeStatus.STOPPED -> R.string.status_stopped
    }
)

private fun Modifier.place(x: Float, y: Float, w: Float, h: Float): Modifier =
    offset(x.dp, y.dp).size(w.dp, h.dp)

@Composable
private fun BoxScope.Art(@DrawableRes res: Int, modifier: Modifier = Modifier.matchParentSize()) {
    Image(painterResource(res), contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds)
}

@Composable
private fun Label(
    text: String, x: Float, y: Float, w: Float, h: Float, size: Float, weight: FontWeight, line: Float,
    color: Color = Mirra.Cream, centered: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = Modifier.place(x, y, w, h),
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        style = homeText(size, weight, line, color, centered)
    )
}

private fun homeText(size: Float, weight: FontWeight, line: Float, color: Color, centered: Boolean = false) = TextStyle(
    fontFamily = Nunito, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, color = color,
    textAlign = if (centered) TextAlign.Center else TextAlign.Left,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
)

@Composable
private fun BoxScope.Step(
    number: Int, badgeX: Float, @DrawableRes icon: Int, iconX: Float, iconY: Float, iconW: Float, iconH: Float,
    textX: Float, title: String, desc: String,
) {
    Box(Modifier.place(badgeX, 853.900f, 63.158f, 63.124f).background(Mirra.Orange, CircleShape))
    Label("$number", badgeX + 9.187f, 853.900f, 44.785f, 63.124f, 32.136f, FontWeight.ExtraBold, 63.124f, Mirra.Background, centered = true)
    Art(icon, Modifier.place(iconX, iconY, iconW, iconH))
    Label(title, textX, 847.014f, 300f, 40f, 28.693f, FontWeight.ExtraBold, 39.022f)
    Label(desc, textX, 887.014f, 320f, 38f, 27.545f, FontWeight.Normal, 37.875f, Mirra.CreamSecondary)
}

@Composable
private fun Divider(x: Float) {
    Box(Modifier.place(x, 842.423f, 1.2f, 87.226f).background(Mirra.Border))
}

@Composable
private fun StatusDot(color: Color, pulse: Boolean) {
    // the ring animates around a solid dot so the label never flickers
    val ring = if (pulse) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)),
            label = "ring"
        )
    } else null
    Box(Modifier.place(737f, 500f, 36f, 36f)) {
        Canvas(Modifier.matchParentSize()) {
            ring?.value?.let { p ->
                drawCircle(color.copy(alpha = 0.5f * (1f - p)), radius = size.minDimension / 2.3f * (1f + 1.4f * p))
            }
        }
        Image(
            painterResource(R.drawable.mirra_status_dot), contentDescription = null,
            modifier = Modifier.matchParentSize(), contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(color)
        )
    }
}

@Composable
private fun Cable(progress: () -> Float, connecting: Boolean, modifier: Modifier) {
    val path = remember { PathParser().parsePathString(CABLE_PATH).toPath() }
    val measure = remember { PathMeasure().apply { setPath(path, false) } }
    val partial = remember { Path() }
    val travel = if (connecting) {
        rememberInfiniteTransition(label = "travel").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "dot"
        )
    } else null
    Canvas(modifier) {
        scale(size.width / FRAME_W, size.height / FRAME_H, pivot = Offset.Zero) {
            val t = progress()
            val stroke = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (t >= 1f) {
                drawPath(path, Mirra.Orange, style = stroke)
            } else if (t > 0f) {
                partial.reset()
                measure.getSegment(0f, measure.length * t, partial, true)
                drawPath(partial, Mirra.Orange, style = stroke)
            }
            travel?.value?.let { d ->
                val p = measure.getPosition(measure.length * d)
                drawCircle(
                    Brush.radialGradient(listOf(Mirra.Orange.copy(alpha = 0.75f), Color.Transparent), center = p, radius = 30f),
                    radius = 30f, center = p
                )
                drawCircle(Mirra.Cream, radius = 9f, center = p)
            }
        }
    }
}

@Composable
private fun HomeAction(
    label: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier,
    iconStart: Float, iconW: Float, iconH: Float, gap: Float, textSize: Float,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .mirraFocus(shape)
            .background(Mirra.Surface, shape)
            .border(1.2.dp, Mirra.Border, shape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = iconStart.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconW.dp, iconH.dp))
        Spacer(Modifier.width(gap.dp))
        BasicText(
            label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = homeText(textSize, FontWeight.ExtraBold, textSize * 1.5f, Mirra.Cream)
        )
    }
}

@Composable
private fun BoxScope.CenterAction(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = 560.dp)
            .height(65.42.dp)
            .mirraFocus(shape)
            .background(Mirra.Surface, shape)
            .border(1.2.dp, Mirra.Border, shape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, maxLines = 1, style = homeText(27f, FontWeight.ExtraBold, 41f, Mirra.Cream))
    }
}

// --- shared mirra controls ---

@Composable
fun Modifier.mirraFocus(shape: Shape = CircleShape, width: Dp = 3.dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "focusScale")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(width, if (focused) Mirra.Lavender else Color.Transparent, shape)
}

@Composable
fun Pill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .mirraFocus(shape, 2.5.dp)
            .clip(shape)
            .background(Mirra.Surface)
            .border(1.dp, Mirra.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Mirra.Cream, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Mirra.Cream)
    }
}

@Composable
fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

// --- network label ---

data class NetworkLabel(val icon: ImageVector, val text: String)

@Composable
fun rememberNetworkLabel(): State<NetworkLabel> {
    val ctx = LocalContext.current
    val wifi = stringResource(R.string.net_wifi)
    val ethernet = stringResource(R.string.net_ethernet)
    val none = stringResource(R.string.net_none)
    return produceState(NetworkLabel(Icons.Rounded.Wifi, wifi), ctx) {
        while (true) {
            value = readNetwork(ctx, wifi, ethernet, none)
            delay(5000)
        }
    }
}

@Suppress("DEPRECATION")
private fun readNetwork(ctx: Context, wifi: String, ethernet: String, none: String): NetworkLabel {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return NetworkLabel(Icons.Rounded.WifiOff, none)
    val net = cm.activeNetwork
    val caps = net?.let { cm.getNetworkCapabilities(it) } ?: return NetworkLabel(Icons.Rounded.WifiOff, none)
    val ip = runCatching {
        cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
    }.getOrNull()
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            NetworkLabel(Icons.Rounded.SettingsEthernet, listOfNotNull(ethernet, ip).joinToString(" · "))
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            // the ssid needs location permission; fall back to the ip address without it
            val ssid = runCatching {
                ctx.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
            }.getOrNull()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            NetworkLabel(Icons.Rounded.Wifi, listOfNotNull(wifi, ssid ?: ip).joinToString(" · "))
        }
        else -> NetworkLabel(Icons.Rounded.Wifi, listOfNotNull(wifi, ip).joinToString(" · "))
    }
}
