package io.github.yyan115.opentune.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.ArtistIdentity

@androidx.media3.common.util.UnstableApi
@Composable
internal fun ArtistCorrectionControls(id: String, name: String, ui: ArtistCorrectionUi, controller: ArtistCorrections, online: Boolean) {
    var reporting by rememberSaveable(id) { mutableStateOf(false) }
    var selecting by rememberSaveable(id) { mutableStateOf(false) }
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing), label = "artist-identity-chevron")
    var options by remember { mutableStateOf(false) }
    val result = ui.result
    val choose = { selecting = true; controller.search(id, name) }
    Surface(Modifier.fillMaxWidth().padding(top = 16.dp).testTag("artist-correction-card"),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(if (result == null) OpenTuneIcons.AccountCircle else OpenTuneIcons.Check,
                        null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.artist_correction_card_title), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result?.artist?.displayName(name) ?: stringResource(R.string.artist_correction_card_prompt),
                        style = MaterialTheme.typography.titleMedium)
                    if (result != null) Text(stringResource(R.string.artist_correction_selected, result.artist.name),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(OpenTuneIcons.KeyboardArrowDown,
                        stringResource(if (expanded) R.string.artist_correction_collapse else R.string.artist_correction_expand),
                        Modifier.graphicsLayer { rotationZ = chevronRotation })
                }
                if (result != null) Box {
                    IconButton(onClick = { options = true }) {
                        Icon(OpenTuneIcons.MoreVert, stringResource(R.string.artist_correction_options), Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.artist_correction_refresh)) },
                            leadingIcon = { Icon(OpenTuneIcons.Refresh, null) }, enabled = online && !ui.scanning,
                            onClick = { options = false; controller.choose(id, result.artist) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.artist_correction_reset)) },
                            leadingIcon = { Icon(OpenTuneIcons.Close, null) },
                            onClick = { options = false; controller.reset(id) })
                    }
                }
            }
            AnimatedVisibility(visible = expanded,
                enter = expandVertically(tween(280, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(280, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(180))) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(value = ui.searchTitles, enabled = !ui.scanning, role = Role.Switch,
                onValueChange = { controller.searchTitles(id, it) }).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.artist_correction_search_titles))
                    Text(stringResource(R.string.artist_correction_search_titles_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = ui.searchTitles, onCheckedChange = null, enabled = !ui.scanning)
            }
            if (ui.scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.progress, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { controller.cancel(id) }) { Icon(OpenTuneIcons.Close, stringResource(R.string.action_cancel)) }
                }
            } else {
                if (result != null) Text(stringResource(R.string.artist_correction_counts, result.confirmed.size, result.possible.size + result.unverified.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TuneTonalButton(onClick = choose, enabled = online, modifier = Modifier.fillMaxWidth()) {
                    Icon(OpenTuneIcons.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (result == null) R.string.artist_correction_action else R.string.artist_correction_change))
                }
            }
            if (result != null) {
                if (result.confirmed.isEmpty()) Text(stringResource(R.string.artist_correction_no_matches), style = MaterialTheme.typography.bodySmall)
                if (result.possible.isNotEmpty() || result.unverified.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().toggleable(value = ui.showUnverified, role = Role.Switch,
                        onValueChange = { controller.showUnverified(id, it) }).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.artist_correction_show_unverified), Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = ui.showUnverified, onCheckedChange = null)
                }
            }
            TextButton(onClick = { reporting = true }) {
                Icon(OpenTuneIcons.Info, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.catalog_report_title))
            }
            if (!online) Text(stringResource(R.string.artist_correction_offline), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!selecting) ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            }
            if (ui.scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
    if (reporting) {
        val url = "https://tidal.com/browse/artist/$id"
        val reference = result?.artist?.let {
            stringResource(R.string.catalog_report_reference, it.name, "https://musicbrainz.org/artist/${it.id}")
        }.orEmpty()
        CatalogReportDialog(url, stringResource(R.string.catalog_report_artist, name, url) + reference) { reporting = false }
    }
    if (selecting) ArtistIdentityDialog(name, ui,
        onSearch = { controller.search(id, it) }, onMore = { controller.search(id, ui.query, more = true) },
        onChoose = { controller.stopSearch(id); selecting = false; controller.choose(id, it) },
        onDismiss = { controller.stopSearch(id); selecting = false })
}

@Composable
internal fun ArtistIdentityDialog(
    initialName: String, ui: ArtistCorrectionUi, onSearch: (String) -> Unit,
    onMore: () -> Unit, onChoose: (ArtistIdentity) -> Unit, onDismiss: () -> Unit,
) {
    var query by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val uri = LocalUriHandler.current
    val keyboard = LocalSoftwareKeyboardController.current
    val search = { if (query.isNotBlank() && !ui.searching) { keyboard?.hide(); onSearch(query.trim()) } }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("artist-identity-dialog"),
        title = { Text(stringResource(R.string.artist_correction_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.artist_correction_explanation), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        singleLine = true, shape = RoundedCornerShape(12.dp), label = { Text(stringResource(R.string.artist_correction_name)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                        trailingIcon = { IconButton(onClick = search, enabled = query.isNotBlank() && !ui.searching) { Icon(OpenTuneIcons.Search, stringResource(R.string.nav_search)) } })
                    if (ui.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    ui.error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
                    if (ui.searched && !ui.searching && ui.error == null && ui.candidates.isEmpty()) Text(stringResource(R.string.artist_correction_empty), Modifier.padding(top = 12.dp))
                }
                items(ui.candidates, key = { it.id }) { artist ->
                    val shownName = artist.displayName(ui.query.ifBlank { initialName })
                    Surface(onClick = { onChoose(artist) }, shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().testTag("artist-candidate-${artist.id}")) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(shownName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Icon(OpenTuneIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (shownName != artist.name) Text(artist.name, style = MaterialTheme.typography.bodyMedium)
                            val detail = listOf(artist.description, artist.country, artist.lifeSpan).filter(String::isNotBlank).joinToString(" · ")
                            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val aliases = artist.aliases.filter { !it.equals(shownName, true) && !it.equals(artist.name, true) }
                            if (aliases.isNotEmpty()) Text(stringResource(R.string.artist_correction_aliases, aliases.take(3).joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { uri.openUri("https://musicbrainz.org/artist/${artist.id}") }, contentPadding = PaddingValues(0.dp)) {
                                Icon(OpenTuneIcons.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.artist_correction_details), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (ui.candidates.size < ui.totalCandidates) item {
                    TuneOutlinedButton(onClick = onMore, enabled = !ui.searching, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}
