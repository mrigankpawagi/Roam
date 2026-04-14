package mrigank.roam.service

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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import mrigank.roam.RoamApplication
import mrigank.roam.ExplorationActivity
import mrigank.roam.R
import mrigank.roam.data.ExploredCell
import mrigank.roam.data.ExploreRepository
import mrigank.roam.data.GridUtils
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
    private var lastUiAcceptedLocation: Location? = null
    private var lastExplorationAcceptedLocation: Location? = null
    private var lastSmoothedUiLocation: Location? = null
    private var latestGpsEventTimeMs: Long = Long.MIN_VALUE

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }

        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        @Suppress("MissingPermission")
        override fun onProviderEnabled(provider: String) {
            // Register for updates from a provider that became available after the service started.
            if (provider == LocationManager.GPS_PROVIDER || provider == LocationManager.NETWORK_PROVIDER) {
                try {
                    locationManager?.requestLocationUpdates(
                        provider, UPDATE_INTERVAL_MS, UPDATE_MIN_DISTANCE_METERS, locationListener
                    )
                } catch (e: SecurityException) {
                    // Permission revoked; nothing to do.
                }
            }
        }

        override fun onProviderDisabled(provider: String) {
            // Stop the service if no location provider is left.
            val anyEnabled =
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            if (!anyEnabled) {
                stopTracking()
            }
        }
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

                isRunning = true
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

        return NotificationCompat.Builder(this, RoamApplication.CHANNEL_ID)
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
                        provider, UPDATE_INTERVAL_MS, UPDATE_MIN_DISTANCE_METERS, locationListener
                    )
                    started = true
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
        if (!location.hasAccuracy() || location.accuracy < 0 || location.accuracy > MAX_ACCURACY_METERS) {
            return
        }

        if (location.provider == LocationManager.GPS_PROVIDER) {
            latestGpsEventTimeMs = maxOf(latestGpsEventTimeMs, locationEventTimeMs(location))
        }

        if (shouldAcceptLocation(
                location = location,
                lastAccepted = lastUiAcceptedLocation,
                maxAccuracyMeters = MAX_UI_ACCURACY_METERS,
                maxSpeedMetersPerSecond = MAX_UI_SPEED_MPS
            )
        ) {
            val smoothedLocation = smoothUiLocation(location)
            val broadcastIntent = Intent(ACTION_LOCATION_UPDATE).apply {
                putExtra(EXTRA_LAT, smoothedLocation.latitude)
                putExtra(EXTRA_LNG, smoothedLocation.longitude)
                putExtra(EXTRA_AREA_ID, areaId)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            lastUiAcceptedLocation = Location(location)
        }

        if (!shouldAcceptLocation(
                location = location,
                lastAccepted = lastExplorationAcceptedLocation,
                maxAccuracyMeters = MAX_EXPLORATION_ACCURACY_METERS,
                maxSpeedMetersPerSecond = MAX_EXPLORATION_SPEED_MPS
            )
        ) {
            return
        }
        lastExplorationAcceptedLocation = Location(location)

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

    private fun shouldAcceptLocation(
        location: Location,
        lastAccepted: Location?,
        maxAccuracyMeters: Float,
        maxSpeedMetersPerSecond: Float
    ): Boolean {
        if (!location.hasAccuracy() || location.accuracy < 0f || location.accuracy > maxAccuracyMeters) {
            return false
        }

        val eventTimeMs = locationEventTimeMs(location)

        if (location.provider == LocationManager.NETWORK_PROVIDER &&
            latestGpsEventTimeMs != Long.MIN_VALUE &&
            eventTimeMs + NETWORK_STALE_GRACE_MS < latestGpsEventTimeMs
        ) {
            return false
        }

        val previous = lastAccepted ?: return true
        val previousTimeMs = locationEventTimeMs(previous)
        val dtMs = eventTimeMs - previousTimeMs

        if (dtMs < -ALLOWED_TIME_BACKSTEP_MS) {
            return false
        }
        if (dtMs <= 0) {
            return location.accuracy < previous.accuracy
        }

        if (previous.provider == LocationManager.GPS_PROVIDER &&
            location.provider == LocationManager.NETWORK_PROVIDER &&
            dtMs < GPS_PREFERENCE_WINDOW_MS &&
            location.accuracy >= previous.accuracy * NETWORK_ACCURACY_IMPROVEMENT_FACTOR
        ) {
            return false
        }

        val maxAllowedDistance = maxSpeedMetersPerSecond * (dtMs / 1000f) + JUMP_DISTANCE_TOLERANCE_METERS
        if (previous.distanceTo(location) > maxAllowedDistance) {
            return false
        }

        return true
    }

    private fun smoothUiLocation(location: Location): Location {
        val previousSmoothed = lastSmoothedUiLocation
        if (previousSmoothed == null) {
            val first = Location(location)
            lastSmoothedUiLocation = first
            return first
        }

        val distanceMeters = previousSmoothed.distanceTo(location)
        val alpha = if (distanceMeters >= UI_SMOOTH_FAST_DISTANCE_METERS) UI_SMOOTH_FAST_ALPHA else UI_SMOOTH_ALPHA
        val smoothed = Location(location).apply {
            latitude = previousSmoothed.latitude + (location.latitude - previousSmoothed.latitude) * alpha
            longitude = previousSmoothed.longitude + (location.longitude - previousSmoothed.longitude) * alpha
        }
        lastSmoothedUiLocation = smoothed
        return smoothed
    }

    private fun locationEventTimeMs(location: Location): Long {
        return if (location.elapsedRealtimeNanos > 0L) {
            location.elapsedRealtimeNanos / 1_000_000L
        } else {
            val nowWallClockMs = System.currentTimeMillis()
            val ageMs = (nowWallClockMs - location.time).coerceAtLeast(0L)
            (SystemClock.elapsedRealtime() - ageMs).coerceAtLeast(0L)
        }
    }

    private fun stopTracking() {
        isRunning = false
        locationManager?.removeUpdates(locationListener)
        // Do NOT cancel serviceScope here — in-flight coroutines launched from
        // handleLocationUpdate just before removeUpdates() must be allowed to finish
        // their DB writes.  The scope is cancelled in onDestroy() once the service
        // is fully stopped.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        locationManager?.removeUpdates(locationListener)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "in.mrigank.roam.ACTION_START"
        const val ACTION_STOP = "in.mrigank.roam.ACTION_STOP"
        const val ACTION_LOCATION_UPDATE = "in.mrigank.roam.ACTION_LOCATION_UPDATE"
        const val EXTRA_AREA_ID = "extra_area_id"
        const val EXTRA_RADIUS_METERS = "extra_radius_meters"
        const val EXTRA_AREA_NAME = "extra_area_name"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_ACCURACY_METERS = 50f
        private const val MAX_UI_ACCURACY_METERS = 45f
        private const val MAX_EXPLORATION_ACCURACY_METERS = 30f
        private const val MAX_UI_SPEED_MPS = 25f
        private const val MAX_EXPLORATION_SPEED_MPS = 12f
        private const val UPDATE_INTERVAL_MS = 3000L
        private const val UPDATE_MIN_DISTANCE_METERS = 1f
        private const val ALLOWED_TIME_BACKSTEP_MS = 1000L
        private const val GPS_PREFERENCE_WINDOW_MS = 8000L
        private const val NETWORK_STALE_GRACE_MS = 1000L
        private const val NETWORK_ACCURACY_IMPROVEMENT_FACTOR = 0.75f
        private const val JUMP_DISTANCE_TOLERANCE_METERS = 5f
        private const val UI_SMOOTH_ALPHA = 0.35 // Move 35% of the delta toward each new fix.
        private const val UI_SMOOTH_FAST_ALPHA = 0.6 // Faster convergence for larger movement.
        private const val UI_SMOOTH_FAST_DISTANCE_METERS = 20f

        /** True while the service is actively tracking location. */
        @Volatile
        var isRunning = false
    }
}
