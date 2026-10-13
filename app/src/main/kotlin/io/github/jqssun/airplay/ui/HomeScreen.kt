package io.github.jqssun.airplay.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
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

// the home screen is laid out on a fixed 960x540 canvas and scaled to the display
private const val DESIGN_W = 960f
private const val DESIGN_H = 540f

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
            }
            content.animateTo(1f, tween(500))
        }
        onIntroDone()
    }

    val settingsFocus = remember { FocusRequester() }
    val tv = isTv()
    LaunchedEffect(Unit) { if (tv) settingsFocus.requestFocusUntilLanded() }

    val network by rememberNetworkLabel()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Mirra.Background)
            .safeDrawingPadding()
    ) {
        val landscape = maxWidth >= maxHeight
        val k = if (landscape) min(maxWidth / DESIGN_W.dp, maxHeight / DESIGN_H.dp) else maxWidth / 420.dp
        val density = LocalDensity.current
        // scale dp and sp together so the layout keeps its proportions on any display
        CompositionLocalProvider(LocalDensity provides Density(density.density * k, 1f)) {
            if (landscape) {
                Box(Modifier.align(Alignment.Center).size(DESIGN_W.dp, DESIGN_H.dp)) {
                    Characters(
                        charIn = charIn.value,
                        cable = cable.value,
                        smileScale = { smile.value },
                        excitedAlpha = { excited.value },
                        connecting = status == HomeStatus.CONNECTING && anims,
                        modifier = Modifier.fillMaxSize()
                    )
                    Hero(
                        deviceName, status, network, anims,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 96.dp)
                            .width(560.dp)
                            .graphicsLayer { alpha = content.value },
                        onRetry = onRetry,
                        onShowScreen = onShowScreen,
                    )
                    Pill(
                        text = stringResource(R.string.tab_settings),
                        icon = Icons.Rounded.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 22.dp, end = 32.dp)
                            .focusRequester(settingsFocus)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, bottom = 22.dp)
                    ) {
                        Steps(deviceName, Modifier.graphicsLayer { alpha = content.value })
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SameWifiHint(Modifier.weight(1f).graphicsLayer { alpha = content.value })
                            Pill(
                                text = stringResource(R.string.home_cant_find, deviceName),
                                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                                onClick = onOpenHelp
                            )
                        }
                    }
                }
            } else {
                // portrait phones: stacked layout without the characters
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                        .graphicsLayer { alpha = content.value },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Pill(
                        text = stringResource(R.string.tab_settings),
                        icon = Icons.Rounded.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier.align(Alignment.End).focusRequester(settingsFocus)
                    )
                    Spacer(Modifier.height(40.dp))
                    Hero(deviceName, status, network, anims, Modifier.fillMaxWidth(), onRetry, onShowScreen)
                    Spacer(Modifier.height(40.dp))
                    Steps(deviceName, Modifier.fillMaxWidth(), vertical = true)
                    Spacer(Modifier.height(28.dp))
                    SameWifiHint(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Pill(
                        text = stringResource(R.string.home_cant_find, deviceName),
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        onClick = onOpenHelp
                    )
                }
            }
        }
    }
}

@Composable
private fun Hero(
    deviceName: String,
    status: HomeStatus,
    network: NetworkLabel,
    anims: Boolean,
    modifier: Modifier,
    onRetry: () -> Unit,
    onShowScreen: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor by animateColorAsState(
                when (status) {
                    HomeStatus.READY -> Mirra.Lavender
                    HomeStatus.CONNECTING, HomeStatus.MIRRORING -> Mirra.Orange
                    HomeStatus.ERROR, HomeStatus.STOPPED -> Mirra.Muted
                }, label = "dot"
            )
            StatusDot(dotColor, pulse = status == HomeStatus.READY && anims)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(
                    when (status) {
                        HomeStatus.READY -> R.string.status_ready
                        HomeStatus.CONNECTING -> R.string.status_connecting
                        HomeStatus.MIRRORING -> R.string.status_mirroring
                        HomeStatus.ERROR -> R.string.status_error
                        HomeStatus.STOPPED -> R.string.status_stopped
                    }
                ),
                fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = Mirra.Cream
            )
        }
        // shrink long names so they stay on one line
        val titleSize = when {
            deviceName.length <= 8 -> 96.sp
            deviceName.length <= 14 -> 70.sp
            else -> 52.sp
        }
        Text(
            text = deviceName,
            fontFamily = Nunito,
            fontWeight = FontWeight.Black,
            fontSize = titleSize,
            lineHeight = titleSize * 1.08f,
            color = Mirra.Cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Mirra.Cream,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        when (status) {
            HomeStatus.ERROR, HomeStatus.STOPPED -> Pill(
                text = stringResource(R.string.btn_try_again),
                icon = Icons.Rounded.Refresh,
                onClick = onRetry
            )
            HomeStatus.MIRRORING -> Pill(
                text = stringResource(R.string.btn_show_screen),
                icon = Icons.Rounded.Cast,
                onClick = onShowScreen
            )
            else -> NetworkRow(network)
        }
    }
}

@Composable
private fun NetworkRow(network: NetworkLabel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(network.icon, contentDescription = null, tint = Mirra.Lavender, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(network.text, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Mirra.Cream)
    }
}

@Composable
private fun StatusDot(color: Color, pulse: Boolean) {
    // the ring animates around a solid dot so the label never flickers
    val ring = if (pulse) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
            label = "ring"
        )
    } else null
    Canvas(Modifier.size(18.dp)) {
        val r = size.minDimension / 2
        ring?.value?.let { p -> drawCircle(color.copy(alpha = 0.55f * (1f - p)), radius = r * (1f + 1.5f * p)) }
        drawCircle(color, radius = r)
    }
}

@Composable
private fun Steps(deviceName: String, modifier: Modifier, vertical: Boolean = false) {
    val steps = listOf(
        Triple(stringResource(R.string.step_control_center), stringResource(R.string.step_control_center_desc), StepIcon.CONTROL_CENTER),
        Triple(stringResource(R.string.step_screen_mirroring), stringResource(R.string.step_screen_mirroring_desc), StepIcon.MIRRORING),
        Triple(deviceName, stringResource(R.string.step_select_desc), StepIcon.AIRPLAY),
    )
    Column(modifier) {
        Text(
            stringResource(R.string.home_on_iphone),
            fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Mirra.Cream
        )
        Spacer(Modifier.height(16.dp))
        if (vertical) {
            steps.forEachIndexed { i, (title, desc, icon) ->
                if (i > 0) Spacer(Modifier.height(16.dp))
                Step(i + 1, title, desc, icon, Modifier.fillMaxWidth())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                steps.forEachIndexed { i, (title, desc, icon) ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .padding(horizontal = 18.dp)
                                .width(1.dp)
                                .height(44.dp)
                                .background(Mirra.Cream.copy(alpha = 0.18f))
                        )
                    }
                    Step(i + 1, title, desc, icon, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, desc: String, icon: StepIcon, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(Mirra.Orange, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Mirra.Background)
        }
        Spacer(Modifier.width(16.dp))
        StepGlyph(icon, Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                title, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                color = Mirra.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                desc, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                color = Mirra.CreamSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SameWifiHint(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = Mirra.Cream, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.home_same_wifi),
            fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Mirra.CreamSecondary
        )
    }
}

// --- focus & pills ---

@Composable
fun Modifier.mirraFocus(shape: Shape = CircleShape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(2.5.dp, if (focused) Mirra.Cream else Color.Transparent, shape)
}

@Composable
fun Pill(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .mirraFocus(shape)
            .clip(shape)
            .background(Mirra.Surface)
            .border(1.dp, Mirra.Cream.copy(alpha = 0.1f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Mirra.Cream, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mirra.Cream)
    }
}

@Composable
fun animationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

// --- characters & cable, drawn in 960x540 design units ---

private fun Path.quadTo(x0: Float, y0: Float, cx: Float, cy: Float, x: Float, y: Float) =
    cubicTo(x0 + 2f / 3f * (cx - x0), y0 + 2f / 3f * (cy - y0), x + 2f / 3f * (cx - x), y + 2f / 3f * (cy - y), x, y)

private fun arcPath(x0: Float, y0: Float, cx: Float, cy: Float, x: Float, y: Float) =
    Path().apply { moveTo(x0, y0); quadTo(x0, y0, cx, cy, x, y) }

private fun cablePath() = Path().apply {
    moveTo(163f, 262f)
    cubicTo(200f, 280f, 228f, 284f, 241f, 268f)
    cubicTo(254f, 252f, 262f, 236f, 248f, 235f)
    cubicTo(232f, 234f, 226f, 256f, 236f, 274f)
    cubicTo(256f, 310f, 310f, 352f, 410f, 360f)
    cubicTo(520f, 370f, 630f, 360f, 676f, 332f)
    cubicTo(694f, 320f, 702f, 304f, 689f, 302f)
    cubicTo(675f, 300f, 668f, 318f, 682f, 327f)
    cubicTo(706f, 340f, 730f, 312f, 752f, 290f)
}

@Composable
private fun Characters(
    charIn: Float,
    cable: Float,
    smileScale: () -> Float,
    excitedAlpha: () -> Float,
    connecting: Boolean,
    modifier: Modifier,
) {
    val path = remember { cablePath() }
    val measure = remember { PathMeasure().apply { setPath(path, false) } }
    val partial = remember { Path() }
    val travel = if (connecting) {
        rememberInfiniteTransition(label = "travel").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "dot"
        )
    } else null

    Canvas(modifier) {
        val u = size.width / DESIGN_W
        scale(u, u, pivot = Offset.Zero) {
            val slide = 28f * (1f - charIn)

            // cable first, so the characters' fills cover its ends
            if (cable > 0f) {
                val stroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (cable >= 1f) {
                    drawPath(path, Mirra.Orange, style = stroke)
                } else {
                    partial.reset()
                    measure.getSegment(0f, measure.length * cable, partial, true)
                    drawPath(partial, Mirra.Orange, style = stroke)
                }
            }
            travel?.value?.let { t ->
                val p = measure.getPosition(measure.length * t)
                drawCircle(
                    Brush.radialGradient(listOf(Mirra.Orange.copy(alpha = 0.7f), Color.Transparent), center = p, radius = 18f),
                    radius = 18f, center = p
                )
                drawCircle(Mirra.Cream, radius = 5f, center = p)
            }

            translate(left = -slide) { drawPhone(charIn, excitedAlpha()) }
            translate(left = slide) { drawTv(charIn, smileScale()) }
        }
    }
}

private fun DrawScope.drawPhone(alpha: Float, excited: Float) {
    val cream = Mirra.Cream.copy(alpha = alpha)
    val lav = Mirra.Lavender.copy(alpha = alpha * excited)
    drawLine(lav, Offset(84f, 119f), Offset(104f, 137f), 6f, StrokeCap.Round)
    drawLine(lav, Offset(108f, 107f), Offset(116f, 129f), 6f, StrokeCap.Round)
    rotate(9f, pivot = Offset(120f, 232f)) {
        translate(120f, 232f) {
            val topLeft = Offset(-44f, -80f)
            val body = Size(88f, 160f)
            drawRoundRect(Mirra.Background.copy(alpha = alpha), topLeft, body, CornerRadius(18f))
            drawRoundRect(cream, topLeft, body, CornerRadius(18f), style = Stroke(6f))
            drawLine(cream, Offset(-9f, -65f), Offset(9f, -65f), 5f, StrokeCap.Round)
            drawLine(cream, Offset(-50f, -46f), Offset(-50f, -32f), 4f, StrokeCap.Round)
            val eye = Stroke(4.5f, cap = StrokeCap.Round)
            drawPath(arcPath(-23f, -16f, -15f, -4f, -7f, -16f), cream, style = eye)
            drawPath(arcPath(5f, -18f, 13f, -6f, 21f, -18f), cream, style = eye)
            drawPath(
                arcPath(-26f, 10f, -2f, 30f, 24f, 4f),
                Mirra.Orange.copy(alpha = alpha), style = Stroke(11f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.drawTv(alpha: Float, smileScale: Float) {
    val cream = Mirra.Cream.copy(alpha = alpha)
    rotate(-13f, pivot = Offset(885f, 212f)) {
        translate(885f, 212f) {
            val topLeft = Offset(-145f, -118f)
            val body = Size(290f, 236f)
            drawPath(
                Path().apply { moveTo(-100f, 118f); lineTo(-112f, 142f); lineTo(210f, 142f) },
                cream, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawRoundRect(Mirra.Background.copy(alpha = alpha), topLeft, body, CornerRadius(30f))
            drawRoundRect(cream, topLeft, body, CornerRadius(30f), style = Stroke(7f))
            val eye = Stroke(13f, cap = StrokeCap.Round)
            drawPath(arcPath(-84f, -44f, -62f, -14f, -40f, -44f), cream, style = eye)
            drawPath(arcPath(-8f, -46f, 14f, -16f, 36f, -46f), cream, style = eye)
            scale(smileScale, pivot = Offset(-14f, 40f)) {
                drawPath(
                    arcPath(-104f, 24f, -30f, 78f, 80f, -2f),
                    Mirra.Orange.copy(alpha = alpha), style = Stroke(30f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

private enum class StepIcon { CONTROL_CENTER, MIRRORING, AIRPLAY }

@Composable
private fun StepGlyph(icon: StepIcon, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 40f
        scale(u, u, pivot = Offset.Zero) {
            val line = Stroke(2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (icon) {
                StepIcon.CONTROL_CENTER -> {
                    drawRoundRect(Mirra.Cream, Offset(3f, 3f), Size(34f, 34f), CornerRadius(9f), style = line)
                    drawCircle(Mirra.Cream, radius = 5f, center = Offset(15f, 14f))
                    drawRoundRect(Mirra.Cream.copy(alpha = 0.45f), Offset(9f, 22f), Size(22f, 9f), CornerRadius(4.5f))
                }
                StepIcon.MIRRORING -> {
                    drawRoundRect(Mirra.Cream, Offset(3f, 5f), Size(22f, 19f), CornerRadius(5f), style = line)
                    drawRoundRect(Mirra.Background, Offset(13f, 14f), Size(24f, 21f), CornerRadius(5f))
                    drawRoundRect(Mirra.Cream, Offset(13f, 14f), Size(24f, 21f), CornerRadius(5f), style = line)
                }
                StepIcon.AIRPLAY -> {
                    drawPath(
                        Path().apply {
                            moveTo(14f, 29f); lineTo(8f, 29f)
                            cubicTo(5f, 29f, 3f, 27f, 3f, 24f); lineTo(3f, 9f)
                            cubicTo(3f, 6f, 5f, 4f, 8f, 4f); lineTo(32f, 4f)
                            cubicTo(35f, 4f, 37f, 6f, 37f, 9f); lineTo(37f, 24f)
                            cubicTo(37f, 27f, 35f, 29f, 32f, 29f); lineTo(26f, 29f)
                        },
                        Mirra.Cream, style = line
                    )
                    drawPath(
                        Path().apply { moveTo(20f, 22f); lineTo(29f, 35f); lineTo(11f, 35f); close() },
                        Mirra.Cream, style = Stroke(2f, join = StrokeJoin.Round)
                    )
                    drawPath(
                        Path().apply { moveTo(20f, 22f); lineTo(29f, 35f); lineTo(11f, 35f); close() },
                        Mirra.Cream
                    )
                }
            }
        }
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
