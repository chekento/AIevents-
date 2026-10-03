package cloud.kosch.aievents

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationUtils {
    fun bestLocation(context: Context): android.location.Location? {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse && !fine) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    fun describe(context: Context, location: android.location.Location): String {
        return runCatching {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            listOfNotNull(
                address?.locality,
                address?.adminArea,
                address?.countryName
            ).distinct().joinToString(", ").ifBlank {
                location.latitude.toString() + "," + location.longitude.toString()
            }
        }.getOrDefault(location.latitude.toString() + "," + location.longitude.toString())
    }
}
