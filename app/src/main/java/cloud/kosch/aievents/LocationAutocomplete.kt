package cloud.kosch.aievents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class PlaceSuggestion(
    val id: String,
    val displayName: String,
    val city: String,
    val region: String,
    val country: String,
    val countryCode: String,
    val point: GeoPoint,
    val type: String
)

object LocationAutocomplete {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, language: String, limit: Int = 8): List<PlaceSuggestion> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            val lang = language.takeIf { it.matches(Regex("[a-z]{2}")) } ?: "en"
            val url = "https://photon.komoot.io/api/?q=" +
                URLEncoder.encode(q, StandardCharsets.UTF_8.toString()) +
                "&limit=" + limit.coerceIn(3, 12) + "&lang=" + lang

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AIevents/0.4 github.com/chekento/AIevents-")
                .get()
                .build()

            val text = http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("Place search HTTP " + response.code)
                response.body?.string() ?: "{}"
            }
            val root = JSONObject(text)
            val features = root.optJSONArray("features") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until features.length()) {
                    val feature = features.optJSONObject(i) ?: continue
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val coords = geometry.optJSONArray("coordinates") ?: continue
                    if (coords.length() < 2) continue
                    val lon = coords.optDouble(0, Double.NaN)
                    val lat = coords.optDouble(1, Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue

                    val p = feature.optJSONObject("properties") ?: JSONObject()
                    val name = p.optString("name")
                    val city = p.optString("city").ifBlank {
                        p.optString("district").ifBlank {
                            p.optString("county")
                        }
                    }
                    val state = p.optString("state")
                    val country = p.optString("country")
                    val countryCode = p.optString("countrycode").uppercase()
                    val type = p.optString("type")
                    val osmType = p.optString("osm_type")
                    val osmId = p.optString("osm_id")
                    val id = listOf(osmType, osmId, lat.toString(), lon.toString())
                        .filter { it.isNotBlank() }.joinToString(":")

                    val pieces = listOf(name, city, state, country)
                        .filter { it.isNotBlank() }
                        .distinct()
                    val display = pieces.joinToString(", ").ifBlank { q }

                    add(
                        PlaceSuggestion(
                            id = id,
                            displayName = display,
                            city = city.ifBlank { name },
                            region = state,
                            country = country,
                            countryCode = countryCode,
                            point = GeoPoint(lat, lon),
                            type = type
                        )
                    )
                }
            }.distinctBy { it.id }.take(limit)
        }
}
