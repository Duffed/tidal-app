package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a page needs from the rest of the app. */
@UnstableApi
class PageContext(
    val state: AppState, val vm: MainViewModel, val pages: PagesViewModel,
    val scope: CoroutineScope, val onMenu: (Track) -> Unit, val source: String = "",
    val correction: ArtistCorrectionUi = ArtistCorrectionUi(),
)

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PageScreen(route: PageRoute, state: AppState, vm: MainViewModel, pages: PagesViewModel, onMenu: (Track) -> Unit) {
    val entries by pages.state.collectAsStateWithLifecycle()
    val entry = entries[route.key] ?: PageUi()
    val scope = rememberCoroutineScope()
    val corrections by pages.corrections.state.collectAsStateWithLifecycle()
    val artistId = route.query["artistId"]
    val correction = artistId?.let { corrections[pages.corrections.key(it)] } ?: ArtistCorrectionUi()
    LaunchedEffect(artistId, state.signedIn) { artistId?.let(pages.corrections::load) }
    val context = PageContext(state, vm, pages, scope, onMenu, route.title, correction)
    LaunchedEffect(route.key, state.signedIn, state.settings.offline) { pages.load(route) }
    val page = entry.page?.let { correctedArtistPage(it, correction) }
    PullToRefreshBox(isRefreshing = entry.loading && page != null, onRefresh = { pages.load(route, force = true); correction.result?.let { result -> artistId?.let { pages.corrections.choose(it, result.artist) } } }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("page-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (page == null) {
                item(key = "state") {
                    if (entry.error != null) ErrorCard(entry.error) { pages.load(route, force = true) }
                    else Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                val headerless = page.modules.none { it is ArtistHeader || it is AlbumHeader || it is MixHeader }
                if (headerless) item(key = "title") {
                    Text(page.title.ifBlank { route.title }, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = AppFonts.title(40, 46))
                }
                pageContent(page, entry, route.key, context, viewAll = headerless && page.modules.size == 1)
                if (page.modules.isEmpty()) item(key = "empty") { EmptyState(OpenTuneIcons.SearchOff, stringResource(R.string.page_empty_title), stringResource(R.string.page_empty_text)) }
            }
        }
    }
}

@Composable
internal fun correctedArtistPage(original: PageData, correction: ArtistCorrectionUi): PageData {
    return run {
        val result = correction.result
        val header = original.modules.filterIsInstance<ArtistHeader>().firstOrNull()
        if (result == null || header == null) original else {
            val modules = buildList<PageModule> {
                add(header)
                add(TrackListModule("corrected-tracks", stringResource(R.string.artist_correction_confirmed), result.confirmed, showAll = true))
                if (correction.showUnverified) {
                    if (result.possible.isNotEmpty()) add(TrackListModule("possible-tracks", stringResource(R.string.artist_correction_possible), result.possible, showAll = true))
                    if (result.unverified.isNotEmpty()) add(TrackListModule("unverified-tracks", stringResource(R.string.artist_correction_unverified), result.unverified, showAll = true))
                }
                // Identity correction replaces song lists, not the rest of the original artist page.
                addAll(original.modules.filterNot { it is ArtistHeader || it is TrackListModule })
            }
            original.copy(modules = modules)
        }
    }
}

/** Adds the rows of a page. [viewAll] lists every item of the single module instead of a preview. */
@UnstableApi
fun LazyListScope.pageContent(page: PageData, entry: PageUi, routeKey: String, ctx: PageContext, viewAll: Boolean) {
    page.modules.forEachIndexed { index, module ->
        val key = "$routeKey:${module.id}:$index"
        when (module) {
            is ArtistHeader -> item(key = key) { ArtistHeaderRow(module, ctx, routeKey, page) }
            is AlbumHeader -> item(key = key) { AlbumHeaderRow(module, ctx, routeKey, page) }
            is MixHeader -> item(key = key) { MixHeaderRow(module, ctx, routeKey, page) }
            is TextModule -> item(key = key) {
                Text(module.text, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is LinksModule -> item(key = key) { LinksRow(module, ctx) }
            is CarouselModule -> carousel(module, entry, routeKey, ctx, key, viewAll)
            is TrackListModule -> trackList(module, entry, routeKey, ctx, key, page.showsAllTracks(module, viewAll))
        }
    }
}

@UnstableApi
private fun LazyListScope.carousel(module: CarouselModule, entry: PageUi, routeKey: String, ctx: PageContext, key: String, viewAll: Boolean) {
    val items = module.items + entry.moreReleases[module.id].orEmpty()
    if (viewAll) {
        val columns = if (module.layout == CarouselLayout.VIDEOS) 1 else 2
        itemsIndexed(items.chunked(columns), key = { row, _ -> "$key:row$row" }) { _, row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { release -> PageCard(release, Modifier.weight(1f), fill = true) { ctx.vm.open(release) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (module.dataPath != null && items.size < module.total) item(key = "$key:more") { LoadMore(routeKey, module, entry, ctx) }
    } else item(key = key) {
        Column {
            SectionTitle(module.title, module.showMore?.let { stringResource(R.string.action_view_all) }) { module.showMore?.let { ctx.vm.openPage(PageRoute.path(it, module.title)) } }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(items, key = { position, release -> "${release.type}:${release.id}:$position" }) { _, release ->
                    PageCard(release) { ctx.vm.open(release) }
                }
            }
        }
    }
}

@UnstableApi
private fun LazyListScope.trackList(module: TrackListModule, entry: PageUi, routeKey: String, ctx: PageContext, key: String, viewAll: Boolean) {
    val tracks = module.tracks + entry.moreTracks[module.id].orEmpty()
    if (module.title.isNotBlank()) item(key = "$key:title") {
        SectionTitle(module.title, if (!viewAll && (module.showMore != null)) stringResource(R.string.action_view_all) else null) {
            module.showMore?.let { ctx.vm.openPage(PageRoute.path(it, module.title)) }
        }
    }
    val shown = if (viewAll || module.numbered || module.showAll) tracks else tracks.take(PREVIEW_TRACKS)
    itemsIndexed(shown, key = { position, track -> "$key:${track.id}:$position" }) { position, track ->
        TrackRow(track, ctx.state.playback.track?.id == track.id, onPlay = {
            ctx.scope.launch {
                val queue = if (ctx.correction.result != null) ctx.correction.playbackTracks()
                    else ctx.pages.allTracks(routeKey, module)
                val start = if (ctx.correction.result != null) queue.indexOfFirst { it.id == track.id } else position
                if (start in queue.indices) ctx.vm.play(queue, start, ctx.source)
            }
        }, onMenu = { ctx.onMenu(track) },
            index = if (module.numbered) position else null)
    }
    if (viewAll && module.dataPath != null && tracks.size < module.total) item(key = "$key:more") { LoadMore(routeKey, module, entry, ctx) }
}

@UnstableApi
@Composable
private fun LoadMore(routeKey: String, module: PageModule, entry: PageUi, ctx: PageContext) {
    val loading = module.id in entry.loadingMore
    TextButton(onClick = { ctx.pages.loadMore(routeKey, module) }, enabled = !loading, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp) else Text(stringResource(R.string.action_load_more))
    }
}

@UnstableApi
@Composable
private fun LinksRow(module: LinksModule, ctx: PageContext) {
    Column {
        if (module.title.isNotBlank()) SectionTitle(module.title)
        if (module.cloud) FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            module.links.forEach { link -> LinkPill(link.title) { ctx.vm.openPage(PageRoute.path(link.path, link.title)) } }
        } else module.links.forEach { link ->
            Row(Modifier.fillMaxWidth().clickable { ctx.vm.openPage(PageRoute.path(link.path, link.title)) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(link.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(OpenTuneIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LinkPill(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontFamily = AppFonts.body, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** A release shown as a card. Artists are round and videos are wide. */
@Composable
fun PageCard(release: Release, modifier: Modifier = Modifier, fill: Boolean = false, onClick: () -> Unit) {
    val video = release.type == ReleaseType.VIDEO
    val width = if (fill) Modifier else Modifier.width(if (video) 220.dp else 152.dp)
    Column(modifier.then(width).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            if (release.type == ReleaseType.ARTIST) Artwork(release.artwork, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape))
            else Artwork(release.artwork, null, Modifier.fillMaxWidth().aspectRatio(if (video) 16f / 9f else 1f))
            if (video) Icon(OpenTuneIcons.PlayArrow, stringResource(R.string.video_play), Modifier.align(Alignment.Center).size(36.dp).background(androidx.compose.ui.graphics.Color(0x99000000), CircleShape).padding(6.dp),
                tint = androidx.compose.ui.graphics.Color.White)
        }
        val round = release.type == ReleaseType.ARTIST
        Text(release.title, (if (round) Modifier.fillMaxWidth() else Modifier).padding(top = 10.dp), fontFamily = AppFonts.body, fontSize = 12.sp, lineHeight = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (round) androidx.compose.ui.text.style.TextAlign.Center else null)
        val detail = when (release.type) {
            ReleaseType.ARTIST -> ""
            ReleaseType.MIX -> release.subtitle
            ReleaseType.VIDEO -> listOf(release.artist, release.subtitle).filter(String::isNotBlank).joinToString(" · ")
            else -> release.artist.ifBlank { release.subtitle }
        }
        if (detail.isNotBlank()) Text(detail, Modifier.padding(top = 3.dp), fontFamily = AppFonts.body, fontSize = 10.sp, lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (release.type == ReleaseType.MIX) 2 else 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun QualityBadges(tags: List<String>, modifier: Modifier = Modifier) {
    val labels = buildList {
        // Dolby Atmos mixes are not badged: TIDAL sends this client their stereo mix.
        if ("HIRES_LOSSLESS" in tags) add(stringResource(R.string.badge_hires)) else if (stringResource(R.string.badge_lossless) in tags) add(stringResource(R.string.badge_lossless))
    }
    if (labels.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            Text(label, Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                fontFamily = AppFonts.body, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpandableText(text: String, source: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    var expanded by rememberSaveable(text.hashCode()) { mutableStateOf(false) }
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
        if (text.length > 220) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) stringResource(R.string.text_show_less) else stringResource(R.string.text_read_more)) }
        if (expanded && source.isNotBlank()) Text(stringResource(R.string.page_source, source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Keep the page compact; the complete biography and attribution live in a scrollable dialog. */
@Composable
internal fun ArtistBiography(name: String, text: String, source: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    var open by rememberSaveable(name, text) { mutableStateOf(false) }
    Column(modifier) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.text_read_more))
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        modifier = Modifier.testTag("artist-biography-dialog"),
        title = { Text(name) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (source.isNotBlank()) Text(stringResource(R.string.page_source, source),
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_close)) } })
}

private fun firstTracks(page: PageData): TrackListModule? = page.modules.filterIsInstance<TrackListModule>().firstOrNull()

@UnstableApi
private fun playFromPage(ctx: PageContext, routeKey: String, page: PageData, shuffle: Boolean) {
    if (ctx.correction.result != null) {
        ctx.vm.playTracks(ctx.correction.playbackTracks(), shuffle, ctx.source)
        return
    }
    val module = firstTracks(page) ?: return
    ctx.scope.launch { ctx.vm.playTracks(ctx.pages.allTracks(routeKey, module), shuffle, ctx.source) }
}

@UnstableApi
@Composable
private fun ArtistHeaderRow(header: ArtistHeader, ctx: PageContext, routeKey: String, page: PageData) {
    val following = ctx.state.artists.any { it.id == header.artist.id }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Artwork(header.artist.artwork, header.artist.title, Modifier.fillMaxWidth().aspectRatio(1.35f))
        Text(header.artist.title, Modifier.padding(top = 18.dp), style = AppFonts.title(42, 48))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TuneButton(onClick = { if (ctx.correction.result != null) playFromPage(ctx, routeKey, page, false) else ctx.vm.playArtistTopTracks(header.artist.id) },
                enabled = (ctx.correction.result == null || ctx.correction.playbackTracks().isNotEmpty()), modifier = Modifier.weight(1f)) {
                Icon(OpenTuneIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_play))
            }
            TuneTonalButton(onClick = { if (ctx.correction.result != null) playFromPage(ctx, routeKey, page, true) else ctx.vm.playArtistTopTracks(header.artist.id, shuffle = true) }, enabled = (ctx.correction.result == null || ctx.correction.playbackTracks().isNotEmpty())) { Icon(OpenTuneIcons.Shuffle, stringResource(R.string.artist_shuffle)) }
            if (ctx.correction.result == null) TuneOutlinedButton(onClick = { ctx.vm.playArtistRadio(header.artist.id) }) { Icon(OpenTuneIcons.Radio, stringResource(R.string.artist_radio)) }
        }
        if (ctx.state.signedIn && ctx.correction.result == null) TuneOutlinedButton(onClick = { ctx.vm.toggleFollow(header.artist) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(if (following) OpenTuneIcons.Check else OpenTuneIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (following) stringResource(R.string.artist_following) else stringResource(R.string.artist_follow))
        }
        ArtistBiography(header.artist.title, header.bio, header.bioSource, Modifier.padding(top = 14.dp))
        ArtistCorrectionControls(header.artist.id, header.artist.title, ctx.correction, ctx.pages.corrections,
            online = ctx.state.signedIn && !ctx.state.settings.offline)
    }
}

@UnstableApi
@Composable
private fun AlbumHeaderRow(header: AlbumHeader, ctx: PageContext, routeKey: String, page: PageData) {
    val saved = ctx.state.albums.any { it.id == header.album.id }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(header.album.artwork, header.album.title, Modifier.size(248.dp))
        Text(header.album.title, Modifier.padding(top = 20.dp).fillMaxWidth(), style = MaterialTheme.typography.headlineLarge)
        Text(header.artistName, Modifier.padding(top = 6.dp).fillMaxWidth().then(
            if (header.artistId.isNotBlank()) Modifier.clickable { ctx.vm.open(Release(header.artistId, header.artistName, "", type = ReleaseType.ARTIST)) } else Modifier),
            color = MaterialTheme.colorScheme.primary)
        val facts = listOfNotNull(header.releaseDate.take(4).takeIf(String::isNotBlank),
            header.trackCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.track_count, it, it) },
            header.durationMs.takeIf { it > 0 }?.let { stringResource(R.string.album_minutes, (it / 60_000).coerceAtLeast(1).toInt()) }).joinToString(" · ")
        if (facts.isNotBlank()) Text(facts, Modifier.padding(top = 4.dp).fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        QualityBadges(header.tags, Modifier.fillMaxWidth().padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuneButton(onClick = { playFromPage(ctx, routeKey, page, false) }, modifier = Modifier.weight(1f)) { Icon(OpenTuneIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_play)) }
            TuneTonalButton(onClick = { playFromPage(ctx, routeKey, page, true) }) { Icon(OpenTuneIcons.Shuffle, stringResource(R.string.album_shuffle)) }
            TuneOutlinedButton(onClick = {
                firstTracks(page)?.let { module -> ctx.scope.launch { ctx.pages.allTracks(routeKey, module).filterNot(Track::onDevice).forEach { ctx.vm.download(it) } } }
            }) { Icon(OpenTuneIcons.Download, stringResource(R.string.album_download_all)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (ctx.state.signedIn) TextButton(onClick = { ctx.vm.favoriteAlbum(header.album) }) {
                Icon(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (saved) stringResource(R.string.album_saved) else stringResource(R.string.album_save))
            }
            TextButton(onClick = { ctx.vm.showCredits("album", header.album.id, header.album.title) }) { Icon(OpenTuneIcons.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.album_credits)) }
        }
        ExpandableText(header.review, header.reviewSource, Modifier.fillMaxWidth().padding(top = 6.dp))
        if (header.copyright.isNotBlank()) Text(header.copyright, Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@UnstableApi
@Composable
private fun MixHeaderRow(header: MixHeader, ctx: PageContext, routeKey: String, page: PageData) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(header.mix.artwork, header.mix.title, Modifier.size(248.dp))
        Text(header.mix.title, Modifier.padding(top = 20.dp).fillMaxWidth(), style = MaterialTheme.typography.headlineLarge)
        if (header.subtitle.isNotBlank()) Text(header.subtitle, Modifier.padding(top = 6.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TuneButton(onClick = { playFromPage(ctx, routeKey, page, false) }, modifier = Modifier.weight(1f)) { Icon(OpenTuneIcons.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_play)) }
            TuneTonalButton(onClick = { playFromPage(ctx, routeKey, page, true) }) { Icon(OpenTuneIcons.Shuffle, stringResource(R.string.album_shuffle)) }
        }
    }
}

private const val PREVIEW_TRACKS = 5
