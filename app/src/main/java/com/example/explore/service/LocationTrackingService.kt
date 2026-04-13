package com.example.explore.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.explore.ExploreApplication
import com.example.explore.ExplorationActivity
import com.example.explore.R
import com.example.explore.data.ExploredCell
import com.example.explore.data.ExploreRepository
import com.example.explore.data.GridUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private var locationManager: LocationManager? = null
    private var repository: ExploreRepository? = null
    private var areaId: Long = -1
    private var radiusMeters: Double = 5.0
    private var areaName: String = "Area"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }

        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        repository = ExploreRepository(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                areaId = intent.getLongExtra(EXTRA_AREA_ID, -1L)
                radiusMeters = intent.getDoubleExtra(EXTRA_RADIUS_METERS, 5.0)
                areaName = intent.getStringExtra(EXTRA_AREA_NAME) ?: "Area"

                if (areaId == -1L) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, createNotification())
                startLocationUpdates()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activityIntent = Intent(this, ExplorationActivity::class.java).apply {
            putExtra(ExplorationActivity.EXTRA_AREA_ID, areaId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this, 1, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ExploreApplication.CHANNEL_ID)
            .setContentTitle("Exploring $areaName...")
            .setContentText("Tracking your exploration progress")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(activityPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var started = false
            for (provider in providers) {
                if (locationManager?.isProviderEnabled(provider) == true) {
                    locationManager?.requestLocationUpdates(
                        provider, 3000L, 1f, locationListener
                    )
                    started = true
                    break
                }
            }
            if (!started) {
                stopSelf()
            }
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val broadcastIntent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LAT, location.latitude)
            putExtra(EXTRA_LNG, location.longitude)
            putExtra(EXTRA_AREA_ID, areaId)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        serviceScope.launch {
            val area = repository?.getAreaById(areaId) ?: return@launch
            val cells = GridUtils.cellsInRadius(area, location.latitude, location.longitude, radiusMeters)
            val exploredCells = cells.map { (row, col) ->
                ExploredCell(areaId = areaId, cellRow = row, cellCol = col)
            }
            if (exploredCells.isNotEmpty()) {
                repository?.insertCells(exploredCells)
            }
        }
    }

    private fun stopTracking() {
        locationManager?.removeUpdates(locationListener)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(locationListener)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.explore.ACTION_START"
        const val ACTION_STOP = "com.example.explore.ACTION_STOP"
        const val ACTION_LOCATION_UPDATE = "com.example.explore.ACTION_LOCATION_UPDATE"
        const val EXTRA_AREA_ID = "extra_area_id"
        const val EXTRA_RADIUS_METERS = "extra_radius_meters"
        const val EXTRA_AREA_NAME = "extra_area_name"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        private const val NOTIFICATION_ID = 1001
    }
}
