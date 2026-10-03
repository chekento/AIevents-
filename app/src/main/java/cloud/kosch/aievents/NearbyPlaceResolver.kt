package cloud.kosch.aievents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class NearbyPlace(
    val name: String,
    val point: GeoPoint,
    val distanceKm: Double
)

object NearbyPlaceResolver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(center: GeoPoint, radiusKm: Int, limit: Int = 8): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            val radiusMeters = (radiusKm.coerceIn(10, 120) * 1000)
            val q = """
                [out:json][timeout:12];
                (
                  node["place"~"city|town"](around:$radiusMeters,${center.lat},${center.lon});
                  way["place"~"city|town"](around:$radiusMeters,${center.lat},${center.lon});
                  relation["place"~"city|town"](around:$radiusMeters,${center.lat},${center.lon});
                );
                out center tags;
            """.trimIndent()

            val req = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", "AIevents/0.5 github.com/chekento/AIevents-")
                .post(q.toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()

            val body = http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string() ?: return@withContext emptyList()
            }
            val root = JSONObject(body)
            val elements = root.optJSONArray("elements") ?: return@withContext emptyList()

            buildList {
                for (i in 0 until elements.length()) {
                    val e = elements.optJSONObject(i) ?: continue
                    val tags = e.optJSONObject("tags") ?: continue
                    val name = tags.optString("name").trim()
                    if (name.isBlank()) continue

                    val lat: Double
                    val lon: Double
                    if (e.has("lat") && e.has("lon")) {
                        lat = e.optDouble("lat", Double.NaN)
                        lon = e.optDouble("lon", Double.NaN)
                    } else {
                        val c = e.optJSONObject("center") ?: continue
                        lat = c.optDouble("lat", Double.NaN)
                        lon = c.optDouble("lon", Double.NaN)
                    }
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val point = GeoPoint(lat, lon)
                    val distance = distanceKm(center, point)
                    if (distance <= radiusKm + 1.0) {
                        add(NearbyPlace(name, point, distance))
                    }
                }
            }
                .groupBy { it.name.lowercase() }
                .mapNotNull { (_, values) -> values.minByOrNull { it.distanceKm } }
                .sortedBy { it.distanceKm }
                .take(limit)
        }

    private fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(la1) * cos(la2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}
