@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.apnaaudioplayer.io.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.apnaaudioplayer.io.R
import com.apnaaudioplayer.io.ui.theme.Accent
import com.apnaaudioplayer.io.ui.theme.IconMuted
import com.apnaaudioplayer.io.ui.theme.Surface1
import com.apnaaudioplayer.io.ui.theme.Surface2
import com.apnaaudioplayer.io.ui.theme.TextBody
import com.apnaaudioplayer.io.ui.theme.TextMuted

/** Board "1 · First run". */
@Composable
fun PermissionScreen(onAllow: () -> Unit, onNotNow: () -> Unit) {
    val allow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { allow.requestFocus() } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(21.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtTile(size = 84.dp, corner = 21.dp, iconSize = 39.dp, iconTint = Accent, background = Surface2)
        Column(
            Modifier.widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 25.sp),
                color = TextBody,
                textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            PillButton(stringResource(R.string.allow_access), onAllow, primary = true, modifier = Modifier.focusRequester(allow))
            PillButton(stringResource(R.string.not_now), onNotNow, primary = false)
        }
    }
}

@Composable
fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtTile(size = 84.dp, corner = 21.dp, iconSize = 39.dp, iconTint = IconMuted, background = Surface1)
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium, color = TextMuted)
    }
}

/** Board "4 · Empty state". */
@Composable
fun EmptyScreen(onScanAgain: () -> Unit) {
    val scan = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { scan.requestFocus() } }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp),
        horizontalArrangement = Arrangement.spacedBy(60.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(195.dp).clip(RoundedCornerShape(24.dp)).background(Surface1),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.StorageDrive, contentDescription = null, modifier = Modifier.size(90.dp), tint = IconMuted)
        }
        Column(Modifier.width(450.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.empty_body),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    color = TextBody,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(R.string.empty_step_1, R.string.empty_step_2, R.string.empty_step_3)
                    .forEachIndexed { i, step -> Step(i + 1, stringResource(step)) }
            }
            PillButton(stringResource(R.string.scan_again), onScanAgain, primary = true, modifier = Modifier.focusRequester(scan))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
            Text("$number", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp))
    }
}
