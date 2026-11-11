package io.github.yyan115.opentune.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MusicBrainzTest {
    @Test fun artistSearchPreservesTheStageNameThatExplainsTheMatch() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"count":1,"artists":[{"id":"georgia","name":"Georgia Meek","aliases":[{"name":"MEEK"}]}]}"""))
            val result = MusicBrainzClient(baseUrl = server.url("/").toString(), intervalMs = 0).search("Meek")
            assertEquals("MEEK", result.artists.single().json().optJSONArray("aliases")?.optString(0))
            assertEquals("MEEK", result.artists.single().displayName("Meek"))
            assertEquals("Georgia Meek", result.artists.single().name)
            assertEquals(result.artists.single(), ArtistIdentity.fromJson(result.artists.single().json()))
        }
    }

    @Test fun exactStageNamesRankAheadOfPartialNames() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"count":2,"artists":[{"id":"mill","name":"Meek Mill"},{"id":"georgia","name":"Georgia Meek","aliases":[{"name":"MEEK"}]}]}"""))
            val result = MusicBrainzClient(baseUrl = server.url("/").toString(), intervalMs = 0).search("Meek")
            assertEquals("georgia", result.artists.first().id)
        }
    }

    @Test fun sameNameCandidatesKeepDistinctIdentitiesAndNoAuthentication() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"count":2,"artists":[
                {"id":"one","name":"Ghost","country":"SE","disambiguation":"metal band","type":"Group","life-span":{"begin":"2006"}},
                {"id":"two","name":"Ghost","country":"JP","disambiguation":"psych rock"}]}"""))
            val client = MusicBrainzClient(baseUrl = server.url("/").toString(), intervalMs = 0)
            val found = client.search("Ghost")
            assertEquals(listOf("one", "two"), found.artists.map { it.id })
            assertEquals("Group · metal band", found.artists[0].description)
            assertEquals("2006", found.artists[0].lifeSpan)
            val request = server.takeRequest()
            assertEquals("(artist:\"Ghost\" OR alias:\"Ghost\")", request.requestUrl!!.queryParameter("query"))
            assertNull(request.getHeader("Authorization"))
            assertTrue(request.getHeader("User-Agent")!!.startsWith("OpenTune/"))
        }
    }

    @Test fun recordingPaginationUsesReturnedCountAndRequiresSelectedArtistCredit() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"recording-count":2,"recordings":[{"id":"a","title":"Song","length":180000,"isrcs":["USAAA0000001"],"artist-credit":[{"artist":{"id":"chosen"}}]}]}"""))
            server.enqueue(MockResponse().setBody("""{"recording-count":2,"recordings":[{"id":"b","title":"Other","artist-credit":[{"artist":{"id":"other"}}]}]}"""))
            val client = MusicBrainzClient(baseUrl = server.url("/").toString(), intervalMs = 0)
            assertEquals(listOf("a"), client.recordings("chosen").map { it.id })
            assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
            val next = server.takeRequest().requestUrl!!
            assertEquals("1", next.queryParameter("offset"))
            assertEquals("isrcs+artist-credits", next.queryParameter("inc"))
        }
    }

    @Test fun truncatedCatalogueIsAnErrorInsteadOfASuccessfulEmptySelection() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"recording-count":2,"recordings":[]}"""))
            try {
                MusicBrainzClient(baseUrl = server.url("/").toString(), intervalMs = 0).recordings("chosen")
                fail("Expected an incomplete catalogue error")
            } catch (_: IOException) { }
        }
    }

    @Test fun collaborationsMatchIndividualCreditsWithoutSplittingArtistNames() {
        val matcher = ArtistRecordingMatcher(listOf(ArtistRecording("reka-recording", "Todo Avaricia", 361000, emptySet())),
            ArtistIdentity("reka", "Reka"))
        val track = JsonCodec.track(JSONObject("""{"id":1,"title":"Todo Avaricia","duration":362,"artists":[{"id":1,"name":"Reka"},{"id":2,"name":"Imperial Black Unit"}]}"""))
        assertEquals(ArtistMatch.Possible, matcher.match(track))
        assertEquals(ArtistMatch.Possible, matcher.match(JsonCodec.decodeTrack(JsonCodec.encodeTrack(track))))
        val unrelated = JsonCodec.track(JSONObject("""{"id":2,"title":"Todo Avaricia","duration":362,"artists":[{"name":"Reka, Another Band"}]}"""))
        assertEquals(ArtistMatch.Unverified, matcher.match(unrelated))
        assertEquals(ArtistMatch.Unverified, matcher.match(track.copy(title = "Todo Avaricia (Live)")))
    }

    @Test fun onlyRecordingCodesConfirmIdentityAndVersionsRemainDistinct() {
        val matcher = ArtistRecordingMatcher(listOf(ArtistRecording("one", "Song", 180000, setOf("USAAA0000001"))))
        val track = Track("tidal", "Song", "Same name", durationMs = 180000)
        assertEquals(ArtistMatch.Confirmed, matcher.match(track.copy(title = "Different release title", isrc = "us-aaa-00-00001")))
        assertEquals(ArtistMatch.Possible, matcher.match(track))
        assertEquals(ArtistMatch.Unverified, matcher.match(track.copy(title = "Song (Live)")))
        assertEquals(ArtistMatch.Unverified, matcher.match(track.copy(durationMs = 0)))
        assertEquals(ArtistMatch.Unverified, matcher.match(track.copy(durationMs = 240000)))
        assertEquals(ArtistMatch.Unverified, ArtistRecordingMatcher(emptyList()).match(track))
    }

    @Test fun titleDiscoveryFindsTracksOutsideArtistPageWithoutAcceptingOtherVersions() = runBlocking {
        val recording = ArtistRecording("mb", "Song", 180000, setOf("CODE"))
        val candidate = Track("outside", "Song", "MEEK", durationMs = 181000)
        val queries = mutableListOf<String>()
        val found = discoverArtistTracks(ArtistIdentity("artist", "Georgia Meek", aliases = listOf("MEEK")), listOf(recording), search = { query ->
            queries += query
            listOf(candidate, candidate.copy(id = "live", title = "Song (Live)"), candidate.copy(id = "wrong", durationMs = 240000))
        })
        assertEquals(listOf("Song", "Song Georgia Meek", "Song MEEK"), queries)
        assertEquals(listOf(candidate), found)
        assertEquals(ArtistMatch.Possible, ArtistRecordingMatcher(listOf(recording)).match(found.single()))
    }

    @Test fun selectionUsesOneReleasePerRecordingAndExcludesUnrelatedArtists() {
        val artist = ArtistIdentity("meek", "MEEK")
        val recordings = listOf(ArtistRecording("one", "Song", 180000, setOf("CODE")),
            ArtistRecording("two", "Other", 150000, emptySet()))
        val wrong = Track("wrong", "Other", "Someone else", durationMs = 150000)
        val possible = Track("possible", "Song", "Meek", durationMs = 180000)
        val confirmed = possible.copy(id = "confirmed", isrc = "CODE")
        val other = Track("other", "Other", "MEEK", durationMs = 151000)
        val result = ArtistRecordingMatcher(recordings, artist).select(listOf(wrong, possible, confirmed, other, other.copy(id = "reissue")))
        assertEquals(listOf(confirmed), result[ArtistMatch.Confirmed])
        assertEquals(listOf(other), result[ArtistMatch.Possible])
        assertNull(result[ArtistMatch.Unverified])
    }

    @Test fun recordingCodeSurvivesTidalParsingAndLocalPersistence() {
        val track = JsonCodec.track(JSONObject("""{"id":42,"title":"Song","isrc":"USAAA0000001"}"""))
        assertEquals("USAAA0000001", track.isrc)
        assertEquals(track, JsonCodec.decodeTrack(JsonCodec.encodeTrack(track)))
        assertEquals("", JsonCodec.decodeTrack(JSONObject("""{"id":"old","title":"Song","artist":"Artist"}""")).isrc)
        assertEquals("", JsonCodec.track(JSONObject("""{"id":1,"isrc":null}""")).isrc)
    }
}
