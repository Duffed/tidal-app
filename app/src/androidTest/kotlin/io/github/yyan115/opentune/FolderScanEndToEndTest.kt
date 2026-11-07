package io.github.yyan115.opentune

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.DocumentKey
import io.github.yyan115.opentune.data.LocalFolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Scans a real folder tree through a document provider: nested folders, a cover picture, files that are not audio, hidden folders,
 * a storage that goes away and a file that is deleted. The provider of the debug build serves a private folder.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class FolderScanEndToEndTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as OpenTuneApp
    private val base get() = File(app.filesDir, "documents-test")
    private val authority get() = "${app.packageName}.testdocuments"
    private val tree get() = DocumentsContract.buildTreeDocumentUri(authority, "root")
    private lateinit var folder: LocalFolder

    private fun flac(path: String) = File(base, path).also { it.parentFile!!.mkdirs(); InstrumentationRegistry.getInstrumentation().context.assets.open("night-walk.flac").use { input -> it.outputStream().use(input::copyTo) } }

    @Before fun buildTheTree() {
        base.deleteRecursively()
        flac("Album/01 Night Walk.flac"); flac("Album/02 Second Walk.flac"); flac("Other/Solo.flac"); flac(".hidden/Secret.flac")
        File(base, "notes.txt").writeText("not audio")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.CYAN) }.compress(Bitmap.CompressFormat.JPEG, 90, File(base, "Album/cover.jpg").outputStream())
        app.library.folders().forEach { app.library.removeFolder(it.id) }
        folder = app.library.addFolder(tree.toString(), "Test library")
    }

    @After fun removeTheTree() {
        app.library.removeFolder(folder.id)
        app.library.localTracks().filter { it.localUri!!.contains(authority) }.forEach { app.library.removeLocal(it.id) }
        base.deleteRecursively()
    }

    private fun scan() {
        app.folders.rescan(app.library.folders().first { it.id == folder.id })
        val end = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < end && app.folders.scans.value[folder.id]?.running != false) Thread.sleep(100)
        assertTrue("The scan did not finish", app.folders.scans.value[folder.id]?.running != true)
    }

    private fun tracks() = app.library.folderTrackIds(folder.id).mapNotNull(app.library::get)
    private fun fileName(track: io.github.yyan115.opentune.core.Track) = DocumentsContract.getDocumentId(Uri.parse(track.localUri!!)).substringAfterLast('/')

    @Test fun aScanFindsNestedFilesWithTheirFormatAndCoverAndSkipsTheRest() {
        scan()
        val found = tracks()
        assertEquals("Audio in nested folders only, without the hidden folder and the text file", setOf("01 Night Walk.flac", "02 Second Walk.flac", "Solo.flac"), found.map(::fileName).toSet())
        assertTrue(found.all { it.quality!!.startsWith("FLAC · 24-bit · 96.0 kHz") })
        assertTrue("The files of the album take its cover", found.filter { fileName(it).startsWith("0") }.all { it.artwork != null && File(Uri.parse(it.artwork).path!!).isFile })
        assertNull("A file in a folder without a cover has none", found.first { fileName(it) == "Solo.flac" }.artwork)
        assertEquals(3, app.library.folders().first { it.id == folder.id }.trackCount)
    }

    @Test fun aStorageThatGoesAwayKeepsTheFilesAndAFileThatIsDeletedLeaves() {
        scan()
        assertEquals(3, tracks().size)
        File(base, ".offline").writeText("")
        scan()
        assertEquals("The scan failed", true, app.folders.scans.value[folder.id]?.failed)
        assertEquals("A folder that cannot be read keeps its files", 3, tracks().size)
        File(base, ".offline").delete()
        File(base, "Album/02 Second Walk.flac").delete()
        scan()
        assertEquals(setOf("01 Night Walk.flac", "Solo.flac"), tracks().map(::fileName).toSet())
    }

    @Test fun theSameFileFoundByHandAndByTheFolderIsOneTrack() = runBlocking {
        scan()
        val scanned = tracks().first { fileName(it) == "01 Night Walk.flac" }
        val byHand = app.importer.import(DocumentsContract.buildDocumentUri(authority, "root/Album/01 Night Walk.flac"), direct = true)
        assertEquals(scanned.id, byHand.id)
        assertEquals(3, app.library.localTracks().count { it.localUri!!.contains(authority) })
        scan()
        assertEquals("A file that was added by hand stays in the library with its folder gone", true, app.library.isDirect(scanned.id))
        assertEquals(DocumentKey.of(scanned.localUri!!).let(app.library::sourceFor)?.trackId, scanned.id)
    }
}
