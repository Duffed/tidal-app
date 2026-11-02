package io.github.yyan115.opentune.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.opentune.R

/** The first screen after installation. TIDAL is the main way to use the app. Local music is available without an account. */
@Composable
fun WelcomeScreen(onSignIn: () -> Unit, onLocalOnly: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("welcome"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Icon(OpenTuneIcons.Brand, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.app_name), Modifier.padding(top = 20.dp), style = AppFonts.title(52, 58), textAlign = TextAlign.Center)
            Text(stringResource(R.string.welcome_tagline), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TuneButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-sign-in")) { Text(stringResource(R.string.login_button)) }
            TextButton(onClick = onLocalOnly, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag("welcome-local")) { Text(stringResource(R.string.welcome_local)) }
            Text(stringResource(R.string.welcome_note), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
