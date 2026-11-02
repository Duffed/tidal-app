package io.github.yyan115.opentune.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** An entry of a folder tree. [documentId] is the identifier that the document provider gives it. */
data class TreeEntry(val documentId: String, val name: String, val mimeType: String?, val isDirectory: Boolean)

/** Lists the entries of one folder in a tree. The production reader uses the system document providers. */
fun interface TreeReader { fun children(parentId: String): List<TreeEntry> }

/** Reads a folder tree that the listener granted through the system folder picker. */
class DocumentTreeReader(private val context: Context, private val tree: Uri) : TreeReader {
    override fun children(parentId: String): List<TreeEntry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val result = mutableListOf<TreeEntry>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val mime = cursor.getString(2)
                result += TreeEntry(cursor.getString(0), cursor.getString(1).orEmpty(), mime, mime == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return result
    }
}

/** Finds the audio files in a folder tree, in the order of a depth-first walk. */
class FolderScanner(private val reader: TreeReader, private val maxDepth: Int = 24, private val maxFiles: Int = 200_000) {
    fun audioFiles(rootId: String): Sequence<TreeEntry> = sequence {
        var found = 0
        val pending = ArrayDeque<Pair<String, Int>>().apply { add(rootId to 0) }
        while (pending.isNotEmpty()) {
            val (folder, depth) = pending.removeLast()
            val entries = reader.children(folder).sortedBy { it.name.lowercase() }
            // The stack is last in, first out. Folders go on in reverse so that the walk reads them in name order.
            for (entry in entries.asReversed()) if (entry.isDirectory && depth < maxDepth && !entry.name.startsWith(".")) pending.addLast(entry.documentId to depth + 1)
            for (entry in entries) if (!entry.isDirectory && isAudio(entry)) { if (++found > maxFiles) return@sequence; yield(entry) }
        }
    }

    companion object {
        private val EXTENSIONS = setOf("flac", "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "aif", "aiff", "alac", "wma", "ape", "wv", "mka")

        /** Audio by its type, or by its extension when the provider reports a generic type. Hidden files are skipped. */
        fun isAudio(entry: TreeEntry): Boolean {
            if (entry.name.startsWith(".")) return false
            if (entry.mimeType?.startsWith("audio/") == true) return true
            return entry.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
        }
    }
}
