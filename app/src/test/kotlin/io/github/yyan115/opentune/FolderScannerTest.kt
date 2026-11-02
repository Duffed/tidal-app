package io.github.yyan115.opentune

import io.github.yyan115.opentune.data.FolderScanner
import io.github.yyan115.opentune.data.TreeEntry
import io.github.yyan115.opentune.data.TreeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScannerTest {
    private fun file(id: String, name: String, mime: String? = "audio/flac") = TreeEntry(id, name, mime, false)
    private fun folder(id: String, name: String) = TreeEntry(id, name, "vnd.android.document/directory", true)
    private fun reader(vararg tree: Pair<String, List<TreeEntry>>) = TreeReader { parent -> tree.toMap()[parent].orEmpty() }

    @Test fun walksFoldersInNameOrderAndFindsAudioInEachOne() {
        val scanner = FolderScanner(reader(
            "root" to listOf(folder("b", "Beta"), file("r1", "root.flac"), folder("a", "alpha")),
            "a" to listOf(file("a1", "two.mp3", "audio/mpeg"), file("a2", "one.flac")),
            "b" to listOf(file("b1", "song.m4a", "audio/mp4"), folder("bb", "inner")),
            "bb" to listOf(file("bb1", "deep.ogg", "audio/ogg")),
        ))
        assertEquals(listOf("r1", "a2", "a1", "b1", "bb1"), scanner.audioFiles("root").map { it.documentId }.toList())
    }

    @Test fun recognisesAudioByExtensionWhenTheProviderReportsAGenericType() {
        assertTrue(FolderScanner.isAudio(file("1", "Track.FLAC", "application/octet-stream")))
        assertTrue(FolderScanner.isAudio(file("2", "track.opus", null)))
        assertFalse(FolderScanner.isAudio(file("3", "cover.jpg", "image/jpeg")))
        assertFalse(FolderScanner.isAudio(file("4", "notes.txt", "text/plain")))
    }

    @Test fun skipsHiddenFilesAndFolders() {
        val scanner = FolderScanner(reader(
            "root" to listOf(file("h", "._resource.flac"), folder("hid", ".thumbnails"), file("ok", "ok.flac")),
            "hid" to listOf(file("t", "thumb.flac")),
        ))
        assertEquals(listOf("ok"), scanner.audioFiles("root").map { it.documentId }.toList())
    }

    @Test fun stopsAtTheDepthAndFileLimits() {
        val deep = FolderScanner(reader("root" to listOf(folder("f1", "one")), "f1" to listOf(folder("f2", "two")), "f2" to listOf(file("x", "x.flac"))), maxDepth = 1)
        assertEquals(emptyList<String>(), deep.audioFiles("root").map { it.documentId }.toList())
        val many = FolderScanner(reader("root" to (1..10).map { file("$it", "$it.flac") }), maxFiles = 4)
        assertEquals(4, many.audioFiles("root").count())
    }

    @Test fun anEmptyTreeGivesNothing() {
        assertEquals(0, FolderScanner(reader()).audioFiles("root").count())
    }
}
