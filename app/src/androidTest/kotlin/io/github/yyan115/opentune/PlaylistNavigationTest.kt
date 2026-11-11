package io.github.yyan115.opentune

import androidx.lifecycle.ViewModelStore
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.Release
import io.github.yyan115.opentune.core.ReleaseType
import io.github.yyan115.opentune.ui.MainViewModel
import io.github.yyan115.opentune.ui.PageRoute
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class PlaylistNavigationTest {
    @Test fun playlistFromRoutedSearchPageIsVisibleAndBackRestoresPage() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as OpenTuneApp
        instrument.runOnMainSync {
            val store = ViewModelStore()
            val vm = MainViewModel(app)
            store.put("test", vm)
            try {
                val route = PageRoute.path("pages/search-results", "Search results")
                vm.openPage(route)
                val playlist = Release("6fe6454e-e7d1-4494-9d59-c7d4db99a1b7", "Techno Essentials", "", type = ReleaseType.PLAYLIST)
                vm.open(playlist)
                assertEquals(playlist, vm.state.value.detail)
                assertTrue("Page routes must not cover the opened playlist", vm.state.value.routes.isEmpty())
                vm.closeDetail()
                assertNull(vm.state.value.detail)
                assertEquals(listOf(route), vm.state.value.routes)
            } finally { store.clear() }
        }
    }
}
