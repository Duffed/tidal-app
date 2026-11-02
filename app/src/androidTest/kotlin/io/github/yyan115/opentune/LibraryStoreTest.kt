package io.github.yyan115.opentune

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.Track
import io.github.yyan115.opentune.data.LibraryStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private inline fun withStore(name: String, block: (LibraryStore) -> Unit) {
        val store = LibraryStore(context, name)
        try { block(store) } finally { store.close() }
    }

    @Test fun upgradesExistingLibraryWithoutLosingTracksOrPlaylists() {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val track = Track("local:migration", "Original track", "Original artist", localUri = "content://original/audio/1")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, payload TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, played INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, title TEXT NOT NULL)")
            old.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE)")
            old.execSQL("INSERT INTO tracks VALUES (?,?,1,123)", arrayOf(track.id, JsonCodec.encodeTrack(track).toString()))
            old.execSQL("INSERT INTO playlists VALUES ('local:existing','Existing playlist')")
            old.execSQL("INSERT INTO playlist_tracks VALUES ('local:existing',?,0)", arrayOf(track.id))
            old.version = 1
        }
        try { withStore(name) { store ->
            assertEquals(track, store.get(track.id))
            assertEquals(track.id, store.favorites().single().id)
            assertEquals(track.id, store.recent().single().id)
            assertEquals(track.id, store.playlistTracks("local:existing").single().id)
            store.cache("account-a", "favorites", JsonCodec.encodeTracks(listOf(track)))
            assertNull(store.cache("account-b", "favorites"))
        } } finally { context.deleteDatabase(name) }
    }

    @Test fun pendingFavoritesKeepTheLatestChoiceAcrossRestarts() {
        val name = "pending-favorites-test.db"
        context.deleteDatabase(name)
        try {
            withStore(name) { store ->
                store.queueFavorite("account-a", "42", true)
                store.queueFavorite("account-a", "42", false)
                store.favoriteSynced("account-a", "42", true)
                assertEquals(mapOf("42" to false), store.pendingFavorites("account-a"))
                assertTrue(store.pendingFavorites("account-b").isEmpty())
                store.cache("account-a", "favorites", "[]")
            }
            withStore(name) { store ->
                assertEquals(mapOf("42" to false), store.pendingFavorites("account-a"))
                assertEquals("[]", store.cache("account-a", "favorites"))
                store.favoriteSynced("account-a", "42", false)
                assertTrue(store.pendingFavorites("account-a").isEmpty())
                store.clearCache("account-a")
                assertNull(store.cache("account-a", "favorites"))
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun scanningAFolderKeepsItsTracksAndDropsTheOnesThatDisappeared() {
        val name = "folders-test.db"
        context.deleteDatabase(name)
        try { withStore(name) { store ->
            fun track(id: String) = Track("local:$id", "Track $id", "Artist", localUri = "content://tree/$id")
            listOf("a", "b", "c").forEach { store.save(track(it)) }
            val folder = store.addFolder("content://tree/music", "Music")
            assertEquals(folder, store.addFolder("content://tree/music", "Music"))
            assertEquals(0L, folder.scannedAt)
            store.recordScan(folder.id, setOf("local:a", "local:b"), at = 1_000)
            assertEquals(setOf("local:a", "local:b"), store.folderTrackIds(folder.id))
            assertEquals(2, store.folders().single().trackCount)
            assertEquals(1_000L, store.folders().single().scannedAt)
            // The second scan no longer finds b, and finds c.
            store.recordScan(folder.id, setOf("local:a", "local:c"), at = 2_000)
            assertEquals(setOf("local:a", "local:c"), store.folderTrackIds(folder.id))
            assertNull("A track that left the folder leaves the library", store.get("local:b"))
            assertNotNull(store.get("local:a"))
        } } finally { context.deleteDatabase(name) }
    }

    @Test fun removingAFolderKeepsTracksThatAnotherFolderHolds() {
        val name = "folders-shared-test.db"
        context.deleteDatabase(name)
        try { withStore(name) { store ->
            listOf("shared", "only-first").forEach { store.save(Track("local:$it", it, "Artist", localUri = "content://tree/$it")) }
            val first = store.addFolder("content://tree/first", "First")
            val second = store.addFolder("content://tree/second", "Second")
            store.recordScan(first.id, setOf("local:shared", "local:only-first"))
            store.recordScan(second.id, setOf("local:shared"))
            store.removeFolder(first.id)
            assertEquals(listOf(second.id), store.folders().map { it.id })
            assertNull(store.get("local:only-first"))
            assertNotNull("Another folder still holds this track", store.get("local:shared"))
            store.removeFolder(second.id)
            assertNull(store.get("local:shared"))
            assertTrue(store.folders().isEmpty())
        } } finally { context.deleteDatabase(name) }
    }

    @Test fun upgradesToTheFolderTablesFromVersionThree() {
        val name = "migration-folders.db"
        context.deleteDatabase(name)
        withStore(name) { it.folders() }
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, payload TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, played INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE playlists (id TEXT PRIMARY KEY, title TEXT NOT NULL)")
            old.execSQL("CREATE TABLE playlist_tracks (playlist_id TEXT NOT NULL, track_id TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,track_id))")
            old.execSQL("CREATE TABLE cloud_cache (account TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, updated INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account,kind))")
            old.execSQL("CREATE TABLE pending_favorites (account TEXT NOT NULL, track_id TEXT NOT NULL, favorite INTEGER NOT NULL, PRIMARY KEY(account,track_id))")
            old.execSQL("CREATE TABLE plays (id INTEGER PRIMARY KEY AUTOINCREMENT, track_id TEXT NOT NULL, played_at INTEGER NOT NULL, listened_ms INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE pending_scrobbles (id INTEGER PRIMARY KEY AUTOINCREMENT, listened_at INTEGER NOT NULL, payload TEXT NOT NULL)")
            old.version = 3
        }
        try { withStore(name) { store ->
            assertTrue(store.folders().isEmpty())
            val folder = store.addFolder("content://tree/upgraded", "Upgraded")
            assertEquals("Upgraded", store.folders().single().name)
            store.removeFolder(folder.id)
        } } finally { context.deleteDatabase(name) }
    }
}
