package io.github.yyan115.opentune

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yyan115.opentune.core.JsonCodec
import io.github.yyan115.opentune.core.TidalClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertEquals

/** Explicitly opted-in, read-only audit. Never logs credentials or response bodies. */
@UnstableApi
class FavoritesLiveTest {
    @Test fun auditFavoritePageCounts() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveFavoriteAudit") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as OpenTuneApp
        assumeTrue(app.tidal.signedIn && !app.settings.state.value.offline)
        var firstRequest: okhttp3.Request? = null
        val seen = mutableSetOf<String>()
        val report = StringBuilder()
        val http = app.http.newBuilder().addInterceptor { chain ->
            if (firstRequest == null) firstRequest = chain.request()
            val response = chain.proceed(chain.request())
            if (response.isSuccessful && chain.request().url.encodedPath.endsWith("/favorites/tracks")) {
                val body = JSONObject(response.peekBody(4L * 1024 * 1024).string())
                val ids = JsonCodec.items(body).map { it.optString("id") }
                val duplicates = ids.count { !seen.add(it) }
                report.append("offset=${chain.request().url.queryParameter("offset")} returnedOffset=${body.optInt("offset", -1)} limit=${body.optInt("limit", -1)} total=${body.optInt("totalNumberOfItems", -1)} raw=${body.optJSONArray("items")?.length()} decoded=${ids.size} duplicates=$duplicates\n")
            }
            response
        }.build()
        val client = TidalClient(app.sessions::identity, app.sessions, http)
        val result = client.allFavorites()
        report.append("unique=${result.size}\n")
        val original = firstRequest!!
        val largeRequest = original.newBuilder().url(original.url.newBuilder()
            .setQueryParameter("offset", "0").setQueryParameter("limit", "10000").build()).build()
        app.http.newCall(largeRequest).execute().use { response ->
            val body = JSONObject(response.body!!.string())
            val tracks = JsonCodec.items(body)
            val ids = tracks.map { it.optString("id") }
            report.append("streamable=${tracks.count { it.optBoolean("allowStreaming") }} ready=${tracks.count { it.optBoolean("streamReady") }} both=${tracks.count { it.optBoolean("allowStreaming") && it.optBoolean("streamReady") }}\n")
            if (response.isSuccessful && body.optInt("totalNumberOfItems", Int.MAX_VALUE) <= 10_000)
                assertEquals("Paged collection must match a single complete response", ids.toSet(), result.map { it.id }.toSet())
            report.append("largePage status=${response.code} total=${body.optInt("totalNumberOfItems", -1)} limit=${body.optInt("limit", -1)} raw=${ids.size} unique=${ids.distinct().size} missingFromPaged=${ids.toSet().minus(seen).size}\n")
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", report.toString()) })
    }
}
