package io.github.yyan115.opentune

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.test.runner.AndroidJUnitRunner

/** UI tests must not depend on a new GitHub release appearing during the run. */
@UnstableApi
class OpenTuneTestRunner : AndroidJUnitRunner() {
    private var originalCheckUpdates: Boolean? = null
    override fun onStart() {
        val app = targetContext.applicationContext as OpenTuneApp
        originalCheckUpdates = app.settings.state.value.checkUpdates
        app.settings.update { it.copy(checkUpdates = false) }
        super.onStart()
    }
    override fun finish(resultCode: Int, results: Bundle?) {
        originalCheckUpdates?.let { enabled ->
            (targetContext.applicationContext as OpenTuneApp).settings.update { it.copy(checkUpdates = enabled) }
        }
        super.finish(resultCode, results)
    }
}
