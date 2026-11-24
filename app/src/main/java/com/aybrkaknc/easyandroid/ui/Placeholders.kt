package com.aybrkaknc.easyandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aybrkaknc.easyandroid.R

enum class BackupsSegment { APPS, MESSAGES, CALLS, FOLDERS }

@Composable
private fun EmptyState(icon: Painter, tint: Color, text: String, buttons: List<String> = emptyList()) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(44.dp), tint = tint)
        }
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        buttons.forEach { label ->
            FilledTonalButton({ showSoon(context) }, Modifier.alpha(0.4f)) { Text(label) }
        }
    }
}

/** Messages and call logs: the list and its action are there, the data is not. */
@Composable
fun ListPlaceholderScreen(calls: Boolean) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.placeholder_selected),
                Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall,
            )
            if (calls) {
                EmptyState(rememberVectorPainter(Icons.Default.Call), Category.CALLS, stringResource(R.string.placeholder_text))
            } else {
                EmptyState(painterResource(R.drawable.ic_message), Category.MESSAGES, stringResource(R.string.placeholder_text))
            }
        }
        ExtendedFloatingActionButton(
            { showSoon(context) },
            Modifier.align(Alignment.BottomEnd).padding(16.dp).alpha(0.4f),
        ) {
            Icon(painterResource(R.drawable.ic_restart), null)
            Text(stringResource(R.string.backup_options), Modifier.padding(start = 12.dp))
        }
    }
}

/** Folder setups: two tabs and an empty state, like the screen they follow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBackupsScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        PrimaryTabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.folders_local)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.folders_cloud)) })
        }
        if (tab == 0) {
            EmptyState(
                painterResource(R.drawable.ic_folder), Category.FOLDERS, stringResource(R.string.folders_empty),
                listOf(stringResource(R.string.folders_new), stringResource(R.string.folders_from_cloud)),
            )
        } else {
            EmptyState(painterResource(R.drawable.ic_folder), Category.FOLDERS, stringResource(R.string.folders_cloud_empty))
        }
    }
}
