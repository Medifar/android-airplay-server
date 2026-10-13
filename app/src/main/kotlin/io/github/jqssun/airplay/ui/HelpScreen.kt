package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.Mirra
import io.github.jqssun.airplay.ui.theme.Nunito

@Composable
fun HelpScreen(deviceName: String, onBack: () -> Unit) {
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { back.requestFocusUntilLanded() }
    val network by rememberNetworkLabel()
    val tips = listOf(
        stringResource(R.string.help_tip_same_wifi),
        stringResource(R.string.help_tip_keep_open, deviceName),
        stringResource(R.string.help_tip_toggle_wifi),
        stringResource(R.string.help_tip_vpn),
        stringResource(R.string.help_tip_builtin_cast),
        stringResource(R.string.help_tip_isolation),
    )

    MirraPage(title = stringResource(R.string.home_cant_find, deviceName), onBack = onBack, backFocus = back) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 8.dp)
        ) {
            tips.forEachIndexed { i, tip ->
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(30.dp).background(Mirra.Orange, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Mirra.Background)
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(tip, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = Mirra.Cream, lineHeight = 26.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(network.icon, contentDescription = null, tint = Mirra.Lavender, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.help_this_tv, network.text),
                    fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Mirra.CreamSecondary
                )
            }
        }
    }
}

// full-screen page with a back pill and a large title, shared by help/settings/logs
@Composable
fun MirraPage(
    title: String,
    onBack: () -> Unit,
    backFocus: FocusRequester? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mirra.Background)
            .safeDrawingPadding()
            .padding(top = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(
                text = stringResource(R.string.btn_back),
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onBack,
                modifier = if (backFocus != null) Modifier.focusRequester(backFocus) else Modifier
            )
            Spacer(Modifier.width(24.dp))
            Text(
                title, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 30.sp,
                color = Mirra.Cream, modifier = Modifier.weight(1f), maxLines = 1
            )
            actions()
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
