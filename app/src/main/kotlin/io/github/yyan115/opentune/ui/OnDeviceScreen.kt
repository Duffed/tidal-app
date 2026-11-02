package io.github.yyan115.opentune.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.ReleaseType
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.LocalFolder
import io.github.yyan115.opentune.downloadErrorText

/**
 * Everything that is stored on the device: tracks downloaded from TIDAL, audio files that the listener added,
 * the folders that OpenTune keeps scanning, and playlists made on the device.
 */
@UnstableApi
@Composable
fun OnDeviceScreen(state: AppState, vm: MainViewModel, onAddFiles: () -> Unit, onAddFolder: () -> Unit, onLogin: () -> Unit, onNewPlaylist: () -> Unit, onMenu: (Track) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf<LocalFolder?>(null) }
    val active = state.downloads.filter { !it.state.isFinished || it.state == WorkInfo.State.FAILED }
    val fromTidal = state.local.filterNot { it.id.startsWith("local:") }
    val files = state.local.filter { it.id.startsWith("local:") }
    val shown = when (filter) { 1 -> fromTidal; 2 -> files; else -> state.local }
    val sourceLabel = stringResource(R.string.nav_device)
    val onThisDevice = stringResource(R.string.library_on_this_device)
    val filters = listOf(R.string.device_filter_all, R.string.device_filter_tidal, R.string.device_filter_files).map { stringResource(it) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().testTag("device-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TuneOutlinedButton(onClick = onAddFiles, modifier = Modifier.weight(1f)) { Icon(OpenTuneIcons.AudioFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.device_add_files), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TuneOutlinedButton(onClick = onAddFolder, modifier = Modifier.weight(1f)) { Icon(OpenTuneIcons.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.device_add_folder), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (!state.signedIn) item { ConnectCard(onLogin) }
        if (active.isNotEmpty()) item { SectionTitle(stringResource(R.string.device_downloading)) }
        items(active, key = { it.id }) { work ->
            val trackId = work.tags.firstOrNull { it.startsWith("track:") }?.removePrefix("track:")
            val track = state.downloadTracks[trackId]
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(OpenTuneIcons.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track?.title ?: stringResource(R.string.downloads_unnamed), style = MaterialTheme.typography.titleSmall)
                        Text(when (work.state) {
                            WorkInfo.State.RUNNING -> stringResource(R.string.download_progress, work.progress.getInt("progress", 0))
                            WorkInfo.State.FAILED -> context.downloadErrorText(work.outputData.getString("error"))
                            WorkInfo.State.BLOCKED -> stringResource(R.string.download_waiting)
                            else -> if (state.settings.downloadOnWifi) stringResource(R.string.download_queued_wifi) else stringResource(R.string.download_queued)
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!work.state.isFinished) IconButton(onClick = { vm.cancelDownload(work) }) { Icon(OpenTuneIcons.Close, stringResource(R.string.download_cancel)) }
                    else if (track != null) TextButton(onClick = { vm.download(track) }) { Text(stringResource(R.string.action_retry)) }
                }
                if (work.state == WorkInfo.State.RUNNING) LinearProgressIndicator(progress = { work.progress.getInt("progress", 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
        if (state.downloads.any { it.state.isFinished }) item { TextButton(onClick = vm::clearDownloadHistory, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.downloads_clear)) } }

        if (state.deviceFolders.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.device_folders)) }
            items(state.deviceFolders, key = { "folder:${it.id}" }) { folder ->
                val scan = state.scans[folder.id]
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(OpenTuneIcons.Folder, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            scan?.failed == true -> stringResource(R.string.device_folder_unavailable)
                            scan?.running == true -> stringResource(R.string.device_folder_scanning, scan.found)
                            else -> pluralStringResource(R.plurals.track_count, folder.trackCount, folder.trackCount)
                        }, style = MaterialTheme.typography.bodySmall, color = if (scan?.failed == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.rescanDeviceFolder(folder.id) }, enabled = scan?.running != true) { Icon(OpenTuneIcons.Refresh, stringResource(R.string.device_folder_rescan, folder.name), Modifier.size(20.dp)) }
                    IconButton(onClick = { removing = folder }) { Icon(OpenTuneIcons.RemoveCircleOutline, stringResource(R.string.device_folder_remove, folder.name), Modifier.size(20.dp)) }
                }
            }
        }

        if (state.localPlaylists.isNotEmpty() || state.local.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.label_playlists), stringResource(R.string.playlist_new), onNewPlaylist) }
            if (state.localPlaylists.isEmpty()) item { Text(stringResource(R.string.device_playlists_hint), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.localPlaylists, key = { "playlist:${it.id}" }) { playlist ->
                ReleaseRow(Release(playlist.id, playlist.title, pluralStringResource(R.plurals.track_count, playlist.trackIds.size, playlist.trackIds.size), type = ReleaseType.PLAYLIST)) {
                    vm.open(Release(playlist.id, playlist.title, onThisDevice, type = ReleaseType.PLAYLIST))
                }
            }
        }

        if (state.local.isEmpty()) {
            if (active.isEmpty()) item { EmptyState(OpenTuneIcons.AudioFile, stringResource(R.string.device_empty_title), stringResource(R.string.device_empty_text), stringResource(R.string.device_add_folder), onAddFolder) }
        } else {
            item { LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters.size) { index -> FilterTab(filter == index, { filter = index }, filters[index]) }
            } }
            item { SectionTitle(pluralStringResource(R.plurals.track_count, shown.size, shown.size), if (shown.size > 1) stringResource(R.string.action_shuffle) else null) { vm.playTracks(shown, shuffle = true, source = sourceLabel) } }
            items(shown, key = Track::id) { track -> TrackRow(track, state.playback.track?.id == track.id, { vm.play(shown, shown.indexOf(track), sourceLabel) }, { onMenu(track) }) }
            if (shown.isEmpty()) item { Text(stringResource(R.string.device_filter_empty), Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    removing?.let { folder ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.device_folder_remove_title, folder.name)) },
            text = { Text(stringResource(R.string.device_folder_remove_text)) },
            confirmButton = { TextButton(onClick = { vm.removeDeviceFolder(folder.id); removing = null }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
