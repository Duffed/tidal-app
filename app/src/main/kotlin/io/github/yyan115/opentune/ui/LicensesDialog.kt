package io.github.yyan115.opentune.ui

import io.github.yyan115.opentune.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The documents of the dialog, in the order of its tabs. */
private val Documents = listOf("THIRD_PARTY_NOTICES.md", "LICENSE", "Apache-2.0.txt", "Inter-OFL.txt", "Instrument-Serif-OFL.txt", "Space-Grotesk-OFL.txt", "JetBrains-Mono-OFL.txt")
private val Tabs = listOf(R.string.licenses_notices, R.string.licenses_gpl, R.string.licenses_apache, R.string.licenses_inter, R.string.licenses_serif, R.string.licenses_space, R.string.licenses_mono)

/** The licences and notices of the app. The tabs show at once and the text of a tab loads when the tab opens. */
@Composable
fun LicensesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableIntStateOf(0) }
    val document by produceState<Pair<Int, String>?>(null, selected) {
        val index = selected
        value = index to withContext(Dispatchers.IO) { context.assets.open("licenses/${Documents[index]}").bufferedReader().use { it.readText() } }
    }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.licenses_title)) }, text = {
        Column {
            // The tabs stay on one line and scroll sideways, so that they keep their size whatever the language.
            LazyRow(Modifier.testTag("licence-tabs"), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(Tabs) { index, title -> FilterChip(selected == index, { selected = index }, label = { Text(stringResource(title)) }) }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            key(selected) {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val text = document?.takeIf { it.first == selected }?.second
                    if (text == null) CircularProgressIndicator(Modifier.padding(top = 16.dp))
                    else if (selected != 0) SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                    else {
                        val links = remember { Regex("\\[([^]]+)]\\(([^)]+)\\)") }
                        text.split("\n\n").forEach { paragraph ->
                            when {
                                paragraph.startsWith("# ") -> Unit // The dialog already supplies the title.
                                paragraph.startsWith("## ") -> Text(paragraph.removePrefix("## "), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
                                paragraph.startsWith("| ") -> paragraph.lines().drop(2).forEach { row ->
                                    val cells = row.trim().trim('|').split('|').map(String::trim)
                                    if (cells.size == 3) Column {
                                        Text(cells[0], style = MaterialTheme.typography.titleSmall)
                                        Text(cells[2], style = MaterialTheme.typography.bodySmall)
                                        links.find(cells[1])?.let { link ->
                                            TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.groupValues[2]))) } }) {
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
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } })
}
