package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.media3.common.util.UnstableApi
import io.github.yyan115.opentune.core.*

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun OpenTuneRoot(vm: MainViewModel, state: AppState, initialDestination: Destination = Destination.Home, initialPlayer: Boolean = false, initialSettings: Boolean = false, initialLyrics: Boolean = false) {
    // The position changes twice a second. Only the mini player reads it, so the rest of the screen does not recompose.
    val position = LocalPlaybackPosition.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val pagesVm: PagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var destinationName by rememberSaveable { mutableStateOf(initialDestination.name) }
    var libraryFilter by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(initialSettings) }
    var login by rememberSaveable { mutableStateOf(false) }
    var nowPlaying by rememberSaveable { mutableStateOf(initialPlayer) }
    var queue by rememberSaveable { mutableStateOf(false) }
    var trackMenu by remember { mutableStateOf<Track?>(null) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var newPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var playlistTitle by remember { mutableStateOf("") }
    var cloudPlaylist by remember { mutableStateOf(false) }
    var playlistPosition by remember { mutableStateOf<Int?>(null) }
    var editPlaylist by remember { mutableStateOf(false) }
    var deletePlaylist by remember { mutableStateOf(false) }
    var removeTrack by remember { mutableStateOf<Track?>(null) }
    var exportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationMenu by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val largeText = LocalDensity.current.fontScale > 1.3f
    val rail = landscape && !largeText
    // TIDAL is the main way to use the app. Without an account the app offers the music on the device and a search over it.
    val destinations = if (state.signedIn) listOf(Destination.Home, Destination.Search, Destination.Library, Destination.Device) else listOf(Destination.Device, Destination.Search)
    val destination = Destination.entries.firstOrNull { it.name == destinationName }?.takeIf { it in destinations } ?: destinations.first()
    val select: (Destination) -> Unit = { destinationName = it.name; settings = false; vm.closeAll() }
    val welcome = !state.settings.welcomeDone && !state.signedIn
    val snackbar = remember { SnackbarHostState() }
    val inDetail = state.detail != null || state.routes.isNotEmpty()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.import(uris) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val exporter = rememberLauncherForActivityResult(ExportContract()) { uri ->
        val encoded = exportTarget
        exportTarget = null
        if (uri != null && encoded != null) vm.export(JsonCodec.decodeTrack(org.json.JSONObject(encoded)), uri)
    }
    val importAudio = { importer.launch(arrayOf("audio/*")) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.addDeviceFolder(uri) }
    val addFolder = { folderPicker.launch(null) }
    val openLogin = { login = true }
    val openTrackMenu: (Track) -> Unit = { trackMenu = it; playlistPosition = null }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(state.signedIn) { if (state.signedIn) { login = false; vm.finishWelcome() } }
    BackHandler(enabled = state.detail != null || settings || state.routes.isNotEmpty()) { if (settings) settings = false else vm.closeDetail() }

    if (welcome) WelcomeScreen(onSignIn = openLogin, onLocalOnly = vm::finishWelcome)
    else Row(Modifier.fillMaxSize().then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier)) {
    if (rail) OpenTuneRail(destinations, destination, select)
    Scaffold(modifier = Modifier.weight(1f),
        topBar = {
            val title = if (settings) stringResource(R.string.nav_settings) else state.detail?.let {
                when (it.type) { ReleaseType.ALBUM -> stringResource(R.string.type_album); ReleaseType.ARTIST -> stringResource(R.string.type_artist); ReleaseType.PLAYLIST -> stringResource(R.string.type_playlist); ReleaseType.MIX -> stringResource(R.string.type_mix); ReleaseType.VIDEO -> stringResource(R.string.type_video); ReleaseType.FOLDER -> stringResource(R.string.type_folder) }
            } ?: stringResource(destination.title)
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = if (settings || inDetail || largeText) 8.dp else 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings || inDetail) IconButton(onClick = { if (settings) settings = false else vm.closeDetail() }) { Icon(OpenTuneIcons.ArrowBack, stringResource(R.string.action_back)) }
                    else if (largeText) IconButton(onClick = { navigationMenu = true }) { Icon(OpenTuneIcons.Menu, stringResource(R.string.nav_open)) }
                    if (!settings && !inDetail && !largeText) {
                        Icon(OpenTuneIcons.Brand, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                    }
                    Text(stringResource(R.string.brand_name), Modifier.weight(1f), fontFamily = AppFonts.body, fontSize = 11.sp, lineHeight = 16.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    if (!settings) {
                        if (destination == Destination.Library && !inDetail && state.signedIn && !state.settings.offline) IconButton(onClick = vm::sync, enabled = !state.syncing) { Icon(OpenTuneIcons.Refresh, stringResource(R.string.library_refresh)) }
                        state.detail?.takeIf { state.routes.isEmpty() }?.let { release ->
                            if (vm.canEdit(release)) IconButton(onClick = { playlistTitle = release.title; editPlaylist = true }) { Icon(OpenTuneIcons.Edit, stringResource(R.string.playlist_edit)) }
                            if (release.type == ReleaseType.PLAYLIST && state.signedIn && !release.id.startsWith("local:") && !vm.canEdit(release)) IconButton(onClick = { vm.favoritePlaylist(release) }) {
                                val saved = state.playlists.any { it.id == release.id }
                                Icon(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder, if (saved) stringResource(R.string.playlist_unsave) else stringResource(R.string.playlist_save))
                            }
                            if (release.type == ReleaseType.ALBUM && state.signedIn) IconButton(onClick = { vm.favoriteAlbum(release) }) {
                                val saved = state.albums.any { it.id == release.id }
                                Icon(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder, if (saved) stringResource(R.string.album_unsave) else stringResource(R.string.album_save))
                            }
                        }
                        if (state.settings.offline) Icon(OpenTuneIcons.CloudOff, stringResource(R.string.status_offline), Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { settings = true }) { Icon(OpenTuneIcons.Settings, stringResource(R.string.nav_settings), Modifier.size(22.dp)) }
                    }
                }
                if (!inDetail || settings) Text(title,
                    Modifier.padding(start = 24.dp, end = 24.dp, top = if (largeText) 2.dp else 6.dp, bottom = 14.dp),
                    style = if (largeText) AppFonts.title(30, 38) else if (destination == Destination.Home && !settings) AppFonts.title(52, 58) else AppFonts.title(44, 50))
            }
        },
        bottomBar = {
            Column {
                if (state.playback.track != null) MiniPlayer(state.playback, position, onOpen = { nowPlaying = true }, onPlay = vm::togglePlay, onNext = vm::next)
                if (!settings && !landscape && !largeText) OpenTuneNavigation(destinations, destination, select)
                else if (state.playback.track != null) Spacer(Modifier.navigationBarsPadding())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                settings -> SettingsScreen(state, vm, openLogin)
                state.routes.isNotEmpty() -> PageScreen(state.routes.last(), state, vm, pagesVm, openTrackMenu)
                state.detail != null -> DetailScreen(state, vm, { track, index -> trackMenu = track; playlistPosition = index })
                destination == Destination.Home -> ListenScreen(state, vm, importAudio, openLogin, { filter -> libraryFilter = filter; destinationName = Destination.Library.name },
                    { destinationName = Destination.Device.name }, openTrackMenu, { nowPlaying = true })
                destination == Destination.Search -> SearchScreen(state, vm, openLogin, openTrackMenu)
                destination == Destination.Library -> LibraryScreen(state, vm, openLogin, { newPlaylistTrack = null; playlistTitle = ""; newPlaylist = true }, openTrackMenu,
                    libraryFilter, { libraryFilter = it })
                else -> OnDeviceScreen(state, vm, importAudio, addFolder, openLogin, { newPlaylistTrack = null; playlistTitle = ""; newPlaylist = true }, openTrackMenu)
            }
            state.exportProgress?.let { percent -> Surface(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.export_progress, percent), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            } }
        }
    }
    }

    if (navigationMenu) ModalBottomSheet(onDismissRequest = { navigationMenu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            destinations.forEach { item -> NavigationDrawerItem(selected = destination == item, label = { Text(stringResource(item.title)) },
                icon = { Icon(item.icon, null) }, modifier = Modifier.padding(horizontal = 12.dp),
                onClick = { select(item); navigationMenu = false }) }
        }
    }

    if (login) LoginSheet(state, vm, onClose = { login = false; vm.cancelLogin() })
    if (nowPlaying && state.playback.track != null) NowPlayingScreen(state, vm, onClose = { nowPlaying = false }, onQueue = { queue = true }, onMenu = openTrackMenu, onDownload = { track ->
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.download(track)
    }, onAlbum = { track -> vm.open(Release(track.albumId, track.album, track.artist, track.artwork)); nowPlaying = false },
        onArtist = { track -> vm.open(Release(track.artistId, track.artist.substringBefore(","), "", type = ReleaseType.ARTIST)); nowPlaying = false }, initialLyrics = initialLyrics)
    if (queue) QueueSheet(state, vm) { queue = false }
    trackMenu?.let { track ->
        ModalBottomSheet(onDismissRequest = { trackMenu = null; playlistPosition = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Artwork(track.artwork, null, Modifier.size(52.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    IconButton(onClick = { trackMenu = null }) { Icon(OpenTuneIcons.Close, stringResource(R.string.track_menu_close)) }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                val saved = state.favorites.any { it.id == track.id }
                SettingRow(if (saved) OpenTuneIcons.Favorite else OpenTuneIcons.FavoriteBorder, if (saved) stringResource(R.string.track_unsave) else stringResource(R.string.track_save), onClick = { vm.favorite(track); trackMenu = null })
                SettingRow(OpenTuneIcons.PlaylistPlay, stringResource(R.string.track_play_next), onClick = { vm.queue(track, true); trackMenu = null })
                SettingRow(OpenTuneIcons.QueueMusic, stringResource(R.string.track_add_queue), onClick = { vm.queue(track); trackMenu = null })
                SettingRow(OpenTuneIcons.PlaylistAdd, stringResource(R.string.playlist_add), onClick = { addToPlaylist = track; trackMenu = null })
                state.detail?.takeIf { vm.canEdit(it) }?.let { release ->
                    playlistPosition?.let { position ->
                        if (position > 0) SettingRow(OpenTuneIcons.ArrowUpward, stringResource(R.string.track_move_up), onClick = { vm.moveInPlaylist(release, position, -1); trackMenu = null; playlistPosition = null })
                        if (position < state.detailTracks.lastIndex) SettingRow(OpenTuneIcons.KeyboardArrowDown, stringResource(R.string.track_move_down), onClick = { vm.moveInPlaylist(release, position, 1); trackMenu = null; playlistPosition = null })
                        SettingRow(OpenTuneIcons.PlaylistRemove, stringResource(R.string.track_remove_from_playlist), onClick = {
                            vm.removeFromPlaylist(release, track, position); trackMenu = null; playlistPosition = null
                        })
                    }
                }
                if (!track.isLocal) SettingRow(OpenTuneIcons.Download, stringResource(R.string.action_download), onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.download(track); trackMenu = null
                })
                if (track.albumId.isNotBlank()) SettingRow(OpenTuneIcons.Album, stringResource(R.string.track_go_album), onClick = {
                    vm.open(Release(track.albumId, track.album, track.artist, track.artwork)); nowPlaying = false; trackMenu = null
                })
                if (track.artistId.isNotBlank() && !track.id.startsWith("local:")) SettingRow(OpenTuneIcons.Person, stringResource(R.string.track_go_artist), onClick = {
                    vm.open(Release(track.artistId, track.artist.substringBefore(","), "", type = ReleaseType.ARTIST)); nowPlaying = false; trackMenu = null
                })
                if (!track.id.startsWith("local:") && state.signedIn && !state.settings.offline) {
                    SettingRow(OpenTuneIcons.Radio, stringResource(R.string.track_radio), onClick = { vm.playTrackRadio(track); trackMenu = null })
                    SettingRow(OpenTuneIcons.Info, stringResource(R.string.album_credits), onClick = { vm.showCredits("track", track.id, "${track.title} · ${track.artist}"); trackMenu = null })
                }
                if (!track.id.startsWith("local:")) SettingRow(OpenTuneIcons.Share, stringResource(R.string.track_share), onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "https://tidal.com/browse/track/${track.id}"), resources.getString(R.string.track_share_title))); trackMenu = null
                })
                if (track.isLocal) SettingRow(OpenTuneIcons.RemoveCircleOutline, stringResource(R.string.track_remove_local), onClick = { removeTrack = track; trackMenu = null })
                val localCopy = state.local.firstOrNull { it.id == track.id } ?: track.takeIf(Track::isLocal)
                if (localCopy != null) SettingRow(OpenTuneIcons.SaveAlt, stringResource(R.string.track_export), onClick = {
                    try {
                        exportTarget = JsonCodec.encodeTrack(localCopy).toString()
                        exporter.launch(vm.app.exporter.describe(localCopy)); trackMenu = null; nowPlaying = false
                    } catch (_: Exception) { vm.notify(resources.getString(R.string.track_unreadable)) }
                })
            }
        }
    }
    state.video?.let { video -> VideoPlayerOverlay(video, state.settings.offline, vm::closeVideo) }
    state.credits?.let { credits -> CreditsSheet(credits, onClose = vm::dismissCredits, onPerson = { person ->
        vm.dismissCredits(); nowPlaying = false
        vm.open(Release(person.id, person.name, "", type = ReleaseType.ARTIST))
    }) }
    addToPlaylist?.let { track ->
        AlertDialog(onDismissRequest = { addToPlaylist = null }, title = { Text(stringResource(R.string.playlist_add)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (state.localPlaylists.isEmpty()) Text(stringResource(R.string.playlist_none))
                state.localPlaylists.forEach { playlist -> TextButton(onClick = { vm.addToPlaylist(playlist, track); addToPlaylist = null }) { Text(playlist.title) } }
                if (!track.id.startsWith("local:")) state.playlists.filter(vm::canEdit).forEach { playlist ->
                    TextButton(onClick = { vm.addToCloudPlaylist(playlist, track); addToPlaylist = null }) { Text(stringResource(R.string.playlist_on_tidal, playlist.title)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { newPlaylistTrack = track; playlistTitle = ""; addToPlaylist = null; newPlaylist = true }) { Text(stringResource(R.string.playlist_new)) } }, dismissButton = { TextButton(onClick = { addToPlaylist = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
    if (newPlaylist) AlertDialog(onDismissRequest = { newPlaylist = false }, title = { Text(stringResource(R.string.playlist_new)) }, text = {
        Column {
            OutlinedTextField(playlistTitle, { playlistTitle = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
            if (state.signedIn && !state.settings.offline && newPlaylistTrack?.id?.startsWith("local:") != true) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(cloudPlaylist, { cloudPlaylist = it }); Text(stringResource(R.string.playlist_create_tidal))
            }
        }
    }, confirmButton = { TextButton(enabled = playlistTitle.isNotBlank(), onClick = {
        if (cloudPlaylist && state.signedIn && newPlaylistTrack?.id?.startsWith("local:") != true) vm.createCloudPlaylist(playlistTitle, newPlaylistTrack) else vm.createPlaylist(playlistTitle, newPlaylistTrack)
        playlistTitle = ""; cloudPlaylist = false; newPlaylist = false; newPlaylistTrack = null
    }) { Text(stringResource(R.string.action_create)) } },
        dismissButton = { TextButton(onClick = { newPlaylist = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (editPlaylist) state.detail?.let { release -> AlertDialog(onDismissRequest = { editPlaylist = false }, title = { Text(stringResource(R.string.playlist_edit)) }, text = {
        Column {
            OutlinedTextField(playlistTitle, { playlistTitle = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
            TextButton(onClick = { editPlaylist = false; deletePlaylist = true }) { Text(stringResource(R.string.playlist_delete), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = playlistTitle.isNotBlank(), onClick = { vm.renamePlaylist(release, playlistTitle); editPlaylist = false; playlistTitle = "" }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { editPlaylist = false }) { Text(stringResource(R.string.action_cancel)) } }) }
    if (deletePlaylist) state.detail?.let { release -> AlertDialog(onDismissRequest = { deletePlaylist = false }, title = { Text(stringResource(R.string.playlist_delete_title, release.title)) },
        text = { Text(stringResource(R.string.playlist_delete_text)) },
        confirmButton = { TextButton(onClick = { vm.deletePlaylist(release); deletePlaylist = false }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { deletePlaylist = false }) { Text(stringResource(R.string.action_cancel)) } }) }
    removeTrack?.let { track -> AlertDialog(onDismissRequest = { removeTrack = null }, title = { Text(stringResource(R.string.track_remove_title)) },
        text = { Text(if (track.id.startsWith("local:")) stringResource(R.string.track_remove_local_text) else stringResource(R.string.track_remove_download_text)) },
        confirmButton = { TextButton(onClick = { vm.removeLocal(track); removeTrack = null }) { Text(stringResource(R.string.action_remove)) } },
        dismissButton = { TextButton(onClick = { removeTrack = null }) { Text(stringResource(R.string.action_cancel)) } }) }
}
