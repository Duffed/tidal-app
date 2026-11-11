package io.github.yyan115.opentune.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.OpenTuneApp
import io.github.yyan115.opentune.R
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.UpdatesCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class UpdatesUi(val releases: List<Release> = emptyList(), val loading: Boolean = true, val error: String? = null)

@UnstableApi
@Composable
fun UpdatesScreen(state: AppState, vm: MainViewModel) {
    val app = LocalContext.current.applicationContext as OpenTuneApp
    val account = app.tidal.accountId
    var refresh by remember { mutableIntStateOf(0) }
    val offline = state.settings.offline
    val entry by produceState(UpdatesUi(), account, offline, refresh) {
        value = UpdatesUi()
        val cached = withContext(Dispatchers.IO) { app.library.cache(account, "updates-feed") }
        val releases = cached?.let { runCatching { UpdatesCodec.releases(JSONObject(it)) }.getOrNull() }.orEmpty()
        value = UpdatesUi(releases)
        if (offline || !state.signedIn) {
            value = UpdatesUi(releases, false, app.getString(if (offline) R.string.page_error_offline else R.string.page_error_signed_out))
            return@produceState
        }
        try {
            val json = app.tidal.updatesJson()
            val fresh = UpdatesCodec.releases(json)
            withContext(Dispatchers.IO) { app.library.cache(account, "updates-feed", json.toString()) }
            value = UpdatesUi(fresh, false)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            value = UpdatesUi(releases, false, app.pageError(error))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SectionTitle(stringResource(R.string.nav_updates), if (!entry.loading && !offline) stringResource(R.string.library_refresh) else null) { refresh++ }
            Text(stringResource(R.string.updates_description), Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (entry.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
        entry.error?.let { message -> item { ErrorCard(message) { refresh++ } } }
        items(entry.releases, key = { it.id }) { release -> ReleaseRow(release) { vm.open(release) } }
        if (!entry.loading && entry.error == null && entry.releases.isEmpty()) item {
            EmptyState(OpenTuneIcons.Notifications, stringResource(R.string.updates_empty), stringResource(R.string.updates_empty_text))
        }
    }
}
