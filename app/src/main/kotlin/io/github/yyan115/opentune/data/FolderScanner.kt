package io.github.yyan115.opentune.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * An entry of a folder tree. [documentId] is the identifier that the document provider gives it.
 * [modified] and [size] tell whether a file changed since it was read. A provider that does not report them gives zero.
 */
data class TreeEntry(val documentId: String, val name: String, val mimeType: String?, val isDirectory: Boolean, val modified: Long = 0, val size: Long = 0)

/** Lists the entries of one folder in a tree. A folder that cannot be read throws an [IOException]. The production reader uses the system document providers. */
fun interface TreeReader { fun children(parentId: String): List<TreeEntry> }

/** Reads a folder tree that the listener granted through the system folder picker. */
class DocumentTreeReader(private val context: Context, private val tree: Uri) : TreeReader {
    override fun children(parentId: String): List<TreeEntry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE)
        val result = mutableListOf<TreeEntry>()
        // A provider that is not available, such as a card that was removed, answers with nothing at all. That differs from an empty folder.
        val cursor = try { context.contentResolver.query(children, columns, null, null, null) }
        catch (error: Exception) { throw IOException("The folder could not be read.", error) }
        cursor?.use {
            while (it.moveToNext()) {
                val mime = it.getString(2)
                result += TreeEntry(it.getString(0), it.getString(1).orEmpty(), mime, mime == DocumentsContract.Document.MIME_TYPE_DIR, if (it.isNull(3)) 0 else it.getLong(3), if (it.isNull(4)) 0 else it.getLong(4))
            }
        } ?: throw IOException("The folder is not available.")
        return result
    }
}

/** An audio file of a scan, with the cover picture that lies in the same folder. */
data class ScannedFile(val entry: TreeEntry, val cover: TreeEntry?)

/** Finds the audio files in a folder tree, in the order of a depth-first walk. */
class FolderScanner(private val reader: TreeReader, private val maxDepth: Int = 24, private val maxFiles: Int = 200_000) {
    /** The folders below the root that could not be read. A scan with failures is incomplete. Valid once the sequence ended. */
    val failed = mutableListOf<String>()

    /** The root folder must be readable. A failure of a folder below it is recorded in [failed] and the walk goes on. */
    fun audioFiles(rootId: String): Sequence<ScannedFile> = sequence {
        failed.clear()
        var found = 0
        val pending = ArrayDeque<Pair<String, Int>>().apply { add(rootId to 0) }
        while (pending.isNotEmpty()) {
            val (folder, depth) = pending.removeLast()
            val entries = try { reader.children(folder).sortedBy { it.name.lowercase() } }
            catch (error: IOException) { if (folder == rootId) throw error else { failed += folder; continue } }
            // The stack is last in, first out. Folders go on in reverse so that the walk reads them in name order.
            for (entry in entries.asReversed()) if (entry.isDirectory && depth < maxDepth && !entry.name.startsWith(".")) pending.addLast(entry.documentId to depth + 1)
            val cover = entries.firstOrNull { !it.isDirectory && isCover(it) }
            for (entry in entries) if (!entry.isDirectory && isAudio(entry)) { if (++found > maxFiles) return@sequence; yield(ScannedFile(entry, cover)) }
        }
    }

    companion object {
        private val EXTENSIONS = setOf("flac", "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "aif", "aiff", "alac", "wma", "ape", "wv", "mka")
        private val COVER_NAMES = setOf("cover", "folder", "front", "album", "albumart", "artwork")
        private val COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

        /** Audio by its type, or by its extension when the provider reports a generic type. Hidden files are skipped. */
        fun isAudio(entry: TreeEntry): Boolean {
            if (entry.name.startsWith(".")) return false
            if (entry.mimeType?.startsWith("audio/") == true) return true
            return entry.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
        }

        /** The picture that stands for a folder of music, by the names that music software uses for it. */
        fun isCover(entry: TreeEntry): Boolean =
            !entry.name.startsWith(".") && entry.name.substringAfterLast('.', "").lowercase() in COVER_EXTENSIONS && entry.name.substringBeforeLast('.').lowercase() in COVER_NAMES
    }
}
