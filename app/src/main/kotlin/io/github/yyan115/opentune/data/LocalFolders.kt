package io.github.yyan115.opentune.data

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.github.yyan115.opentune.OpenTuneApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** The state of a folder scan. [found] counts the audio files seen so far. [failed] means that the folder could not be read. */
data class ScanState(val found: Int = 0, val running: Boolean = true, val failed: Boolean = false)

/** Folders on the device that hold music. Each one is scanned when it is added and again when it is older than a day. */
@androidx.media3.common.util.UnstableApi
class LocalFolders(private val app: OpenTuneApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val mutable = MutableStateFlow<Map<String, ScanState>>(emptyMap())
    val scans = mutable.asStateFlow()

    /** Keeps access to a folder that the listener picked, adds it and scans it. */
    fun add(tree: Uri): LocalFolder {
        app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val folder = app.library.addFolder(tree.toString(), displayName(tree))
        rescan(folder)
        return folder
    }

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        mutable.update { it - id }
        app.library.folders().firstOrNull { it.id == id }?.let { runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(it.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        app.library.removeFolder(id)
    }

    fun rescan(folder: LocalFolder) {
        if (jobs[folder.id]?.isActive == true) return
        mutable.update { it + (folder.id to ScanState()) }
        jobs[folder.id] = scope.launch {
            try {
                scan(folder)
                mutable.update { it - folder.id }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("OpenTune", "Scan of ${folder.name} failed: ${error.javaClass.simpleName}")
                mutable.update { it + (folder.id to ScanState(running = false, failed = true)) }
            }
        }
    }

    /** Scans every folder that was not scanned during the last [maxAgeMs]. */
    fun rescanStale(maxAgeMs: Long = 24 * 3600_000L) {
        val now = System.currentTimeMillis()
        app.library.folders().filter { now - it.scannedAt > maxAgeMs }.forEach(::rescan)
    }

    private suspend fun scan(folder: LocalFolder) {
        val tree = Uri.parse(folder.uri)
        val scanner = FolderScanner(DocumentTreeReader(app, tree))
        val seen = linkedSetOf<String>()
        for (entry in scanner.audioFiles(DocumentsContract.getTreeDocumentId(tree))) {
            kotlin.coroutines.coroutineContext.ensureActive()
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
            val id = LocalImporter.idFor(uri)
            if (app.library.get(id) == null) runCatching { app.importer.import(uri) }.onFailure { if (it is CancellationException) throw it else continue }
            seen += id
            if (seen.size % 10 == 0) mutable.update { it + (folder.id to ScanState(found = seen.size)) }
        }
        app.library.recordScan(folder.id, seen)
    }

    private fun displayName(tree: Uri): String = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        app.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: tree.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Music"
}
