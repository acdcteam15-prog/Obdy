package com.example.obdy

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/** Records the GPS track of a trip so it can be saved as a GPX file. Only runs while a trip runs. */
class RouteRecorder(private val ctx: Context) {
    val points = ArrayList<RoutePoint>()
    private var lm: LocationManager? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) { points.add(RoutePoint(l.latitude, l.longitude, l.time, if (l.hasSpeed()) l.speed.toDouble() else 0.0)) }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!hasPermission()) return false
        return try {
            val m = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
            lm = m
            true
        } catch (e: Exception) { false }
    }

    fun stop() { try { lm?.removeUpdates(listener) } catch (_: Exception) {}; lm = null }
}

/** The last place the phone knew about (from GPS or the network). Used to remember where the car was parked. */
object Where {
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val m = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { try { m.getLastKnownLocation(it) } catch (e: Exception) { null } }.maxByOrNull { it.time }
        } catch (e: Exception) { null }
    }
}
