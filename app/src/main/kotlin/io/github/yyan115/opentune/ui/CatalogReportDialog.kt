package io.github.yyan115.opentune.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.R

@Composable
internal fun CatalogReportDialog(targetUrl: String, initialText: String, onDismiss: () -> Unit) {
    var text by rememberSaveable(targetUrl) { mutableStateOf(initialText) }
    var copied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val label = stringResource(R.string.catalog_report_title)
    fun copyReport() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(label, text))
        copied = true
    }
    fun open(url: String) { failed = runCatching { uri.openUri(url) }.isFailure }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.catalog_report_explanation))
                OutlinedTextField(text, { text = it; copied = false }, Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    label = { Text(stringResource(R.string.catalog_report_details)) }, minLines = 5)
                TextButton(onClick = { copyReport() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.catalog_report_copy)) }
                TextButton(onClick = { open(targetUrl) }) { Text(stringResource(R.string.catalog_report_open_tidal)) }
                if (copied) Text(stringResource(R.string.catalog_report_copied), style = MaterialTheme.typography.bodySmall)
                if (failed) Text(stringResource(R.string.catalog_report_open_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { copyReport(); open("https://support.tidal.com/hc/en-us/requests/new?ticket_form_id=360000036038") }, enabled = text.isNotBlank()) {
            Text(stringResource(R.string.catalog_report_support))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } })
}
