package com.jarvis.assistant.device

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.Locale

class LocationController(private val context: Context? = null) {

    /**
     * Returns a coarse location description using the last-known GPS/Network location
     * and reverse geocoding. Requires ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION.
     */
    @SuppressLint("MissingPermission")
    fun getCoarseLocationDescription(): String {
        val ctx = context ?: return "Location unavailable"
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return "Location service unavailable"
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
                return "Location services are turned off. Opening location settings, Sir."
            }
            val loc = lm.getLastKnownLocation(provider) ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (loc == null) return "Locating… last known GPS position not available yet, Sir."

            val geocoder = Geocoder(ctx, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            }
            if (!addresses.isNullOrEmpty()) {
                val a = addresses[0]
                val parts = listOfNotNull(a.subLocality, a.locality, a.adminArea, a.countryName).filter { it.isNotBlank() }.distinct()
                "You are currently near ${parts.joinToString(", ")} (Coordinates: ${loc.latitude.format(2)}° N, ${loc.longitude.format(2)}° E), Sir."
            } else {
                "Your current coordinates are ${loc.latitude.format(4)}, ${loc.longitude.format(4)}, Sir."
            }
        } catch (e: Exception) {
            Log.e("LocationController", "Location failed", e)
            "Location permission is required to pinpoint your position, Sir."
        }
    }

    fun openNavigation(place: String): Boolean {
        Log.i("LocationController", "Navigating to: $place")
        return try {
            val ctx = context ?: return false
            val uri = Uri.parse("google.navigation:q=${Uri.encode(place)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage("com.google.android.apps.maps")
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
            } else {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(place)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(web)
            }
            true
        } catch (e: Exception) {
            Log.e("LocationController", "Navigation failed", e)
            false
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
