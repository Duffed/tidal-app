package io.github.yyan115.opentune.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yyan115.opentune.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The documents of the licences page, in list order, with the asset file that holds each one. */
val LicenceDocuments: Map<SettingsPage, String> = linkedMapOf(
    SettingsPage.Notices to "THIRD_PARTY_NOTICES.md", SettingsPage.Gpl to "LICENSE", SettingsPage.Apache to "Apache-2.0.txt",
    SettingsPage.Inter to "Inter-OFL.txt", SettingsPage.Serif to "Instrument-Serif-OFL.txt", SettingsPage.Space to "Space-Grotesk-OFL.txt", SettingsPage.Mono to "JetBrains-Mono-OFL.txt",
)

private val FontDocuments = setOf(SettingsPage.Inter, SettingsPage.Serif, SettingsPage.Space, SettingsPage.Mono)

/** The licences and notices of the app as a list. A row opens its document on its own page. */
@Composable
fun LicencesPage(onOpen: (SettingsPage) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("licences-list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(LicenceDocuments.keys.toList()) { page ->
            SettingRow(OpenTuneIcons.Description, stringResource(page.title), if (page in FontDocuments) stringResource(R.string.licenses_ofl) else null,
                { onOpen(page) }, trailing = { Icon(OpenTuneIcons.ChevronRight, null) })
        }
    }
}

/** One licence document. The notices are a table of libraries with links, and the other documents are plain text that can be selected. */
@Composable
fun LicenceDocumentPage(page: SettingsPage) {
    val context = LocalContext.current
    val file = LicenceDocuments.getValue(page)
    val text by produceState<String?>(null, file) { value = withContext(Dispatchers.IO) { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } } }
    Column(Modifier.fillMaxSize().testTag("licence-document").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val document = text
        if (document == null) CircularProgressIndicator(Modifier.padding(top = 16.dp))
        else if (page != SettingsPage.Notices) SelectionContainer { Text(document, style = MaterialTheme.typography.bodySmall) }
        else {
            val links = remember { Regex("\\[([^]]+)]\\(([^)]+)\\)") }
            document.split("\n\n").forEach { paragraph ->
                when {
                    paragraph.startsWith("# ") -> Unit // The top bar already supplies the title.
                    paragraph.startsWith("## ") -> Text(paragraph.removePrefix("## "), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                    paragraph.startsWith("| ") -> paragraph.lines().drop(2).forEach { row ->
                        val cells = row.trim().trim('|').split('|').map(String::trim)
                        if (cells.size == 3) Column {
                            Text(cells[0], style = MaterialTheme.typography.titleSmall)
                            Text(cells[2], style = MaterialTheme.typography.bodySmall)
                            links.find(cells[1])?.let { link ->
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.groupValues[2]))) } }, contentPadding = PaddingValues(0.dp)) {
                                    Text(link.groupValues[1])
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    else -> Text(links.replace(paragraph) { it.groupValues[1] }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
