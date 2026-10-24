package com.example.mockgps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

class MockService : Service() {

    companion object {
        const val CHANNEL_ID = "mocka_telemetry"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.mockgps.ACTION_START"
        const val ACTION_STOP = "com.example.mockgps.ACTION_STOP"
        const val ACTION_UPDATE = "com.example.mockgps.ACTION_UPDATE"
        const val ACTION_NUDGE = "com.example.mockgps.ACTION_NUDGE"
        const val ACTION_TELEMETRY = "com.example.mockgps.ACTION_TELEMETRY"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_SPEED_MPS = "speed_mps"
        const val EXTRA_BEARING = "bearing"
        const val EXTRA_DRIFT = "drift"
        const val EXTRA_DISTANCE = "distance"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_STEP_METERS = "step_meters"
        const val EXTRA_NUDGE_HEADING = "nudge_heading"

        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var liveLat: Double = 25.2048
            private set
        @Volatile var liveLon: Double = 55.2708
            private set
        @Volatile var liveSpeedMps: Float = 0f
            private set
        @Volatile var liveBearing: Float = 0f
            private set
        @Volatile var liveDrift: Boolean = true
            private set
        @Volatile var liveDistanceMeters: Double = 0.0
            private set
    }

    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            advanceLocation(1.0) // 1 second interval
            pushTestLocation()
            sendTelemetryBroadcast()
            updateNotification()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopMocking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NUDGE -> {
                val stepMeters = intent?.getDoubleExtra(EXTRA_STEP_METERS, 50.0) ?: 50.0
                val nudgeHeading = intent?.getFloatExtra(EXTRA_NUDGE_HEADING, liveBearing) ?: liveBearing
                nudgeLocation(stepMeters, nudgeHeading)
                if (isRunning) {
                    pushTestLocation()
                    sendTelemetryBroadcast()
                    updateNotification()
                }
                return START_STICKY
            }
            ACTION_UPDATE -> {
                intent?.let {
                    if (it.hasExtra(EXTRA_LAT)) liveLat = it.getDoubleExtra(EXTRA_LAT, liveLat)
                    if (it.hasExtra(EXTRA_LON)) liveLon = it.getDoubleExtra(EXTRA_LON, liveLon)
                    if (it.hasExtra(EXTRA_SPEED_MPS)) liveSpeedMps = it.getFloatExtra(EXTRA_SPEED_MPS, liveSpeedMps)
                    if (it.hasExtra(EXTRA_BEARING)) liveBearing = it.getFloatExtra(EXTRA_BEARING, liveBearing)
                    if (it.hasExtra(EXTRA_DRIFT)) liveDrift = it.getBooleanExtra(EXTRA_DRIFT, liveDrift)
                }
                if (isRunning) {
                    pushTestLocation()
                    sendTelemetryBroadcast()
                    updateNotification()
                }
                return START_STICKY
            }
            ACTION_START -> {
                intent?.let {
                    liveLat = it.getDoubleExtra(EXTRA_LAT, liveLat)
                    liveLon = it.getDoubleExtra(EXTRA_LON, liveLon)
                    liveSpeedMps = it.getFloatExtra(EXTRA_SPEED_MPS, 0f)
                    liveBearing = it.getFloatExtra(EXTRA_BEARING, 0f)
                    liveDrift = it.getBooleanExtra(EXTRA_DRIFT, true)
                }

                startForegroundServiceNotification()

                val configured = setupTestProviders()
                if (!configured) {
                    stopMocking()
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (!isRunning) {
                    isRunning = true
                    liveDistanceMeters = 0.0
                    handler.post(tickRunnable)
                }

                return START_REDELIVER_INTENT
            }
        }

        return START_STICKY
    }

    private fun setupTestProviders(): Boolean {
        return try {
            providers.forEach { provider ->
                try { lm.removeTestProvider(provider) } catch (_: Exception) {}
                lm.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    1,     // powerRequirement: low (1)
                    1      // accuracy: fine (1)
                )
                lm.setTestProviderEnabled(provider, true)
            }
            true
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Permission denied: Please select 'Mocka' in Developer Options -> Select mock location app",
                Toast.LENGTH_LONG
            ).show()
            false
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize test providers: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun advanceLocation(seconds: Double) {
        if (liveSpeedMps <= 0.001f) return

        val distance = liveSpeedMps * seconds
        liveDistanceMeters += distance

        val radHeading = Math.toRadians(liveBearing.toDouble())
        val deltaLat = (distance * cos(radHeading)) / 111139.0
        val cosLat = max(0.0001, cos(Math.toRadians(liveLat)))
        val deltaLon = (distance * sin(radHeading)) / (111139.0 * cosLat)

        liveLat = (liveLat + deltaLat).coerceIn(-90.0, 90.0)
        var newLon = liveLon + deltaLon
        if (newLon > 180.0) newLon -= 360.0
        if (newLon < -180.0) newLon += 360.0
        liveLon = newLon
    }

    private fun nudgeLocation(distanceMeters: Double, headingDegrees: Float) {
        val rad = Math.toRadians(headingDegrees.toDouble())
        val deltaLat = (distanceMeters * cos(rad)) / 111139.0
        val cosLat = max(0.0001, cos(Math.toRadians(liveLat)))
        val deltaLon = (distanceMeters * sin(rad)) / (111139.0 * cosLat)

        liveLat = (liveLat + deltaLat).coerceIn(-90.0, 90.0)
        var newLon = liveLon + deltaLon
        if (newLon > 180.0) newLon -= 360.0
        if (newLon < -180.0) newLon += 360.0
        liveLon = newLon
        liveBearing = headingDegrees
        liveDistanceMeters += distanceMeters
    }

    private fun pushTestLocation() {
        var pushLat = liveLat
        var pushLon = liveLon
        var pushAccuracy = 1.8f
        var pushAltitude = 14.5

        if (liveDrift) {
            val driftLatMeters = (Math.random() - 0.5) * 2.2
            val driftLonMeters = (Math.random() - 0.5) * 2.2
            val cosLat = max(0.0001, cos(Math.toRadians(liveLat)))
            pushLat += driftLatMeters / 111139.0
            pushLon += driftLonMeters / (111139.0 * cosLat)
            pushAccuracy = 1.5f + (Math.random().toFloat() * 1.5f)
            pushAltitude += (Math.random() - 0.5) * 1.0
        }

        val nowTime = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtimeNanos()

        providers.forEach { provider ->
            val location = Location(provider).apply {
                latitude = pushLat
                longitude = pushLon
                altitude = pushAltitude
                accuracy = pushAccuracy
                speed = liveSpeedMps
                bearing = liveBearing
                time = nowTime
                elapsedRealtimeNanos = nowElapsed

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 1.0f
                    speedAccuracyMetersPerSecond = 0.25f
                    verticalAccuracyMeters = 0.8f
                }
            }

            try {
                lm.setTestProviderLocation(provider, location)
            } catch (_: Exception) {}
        }
    }

    private fun sendTelemetryBroadcast() {
        val intent = Intent(ACTION_TELEMETRY).apply {
            setPackage(packageName)
            putExtra(EXTRA_LAT, liveLat)
            putExtra(EXTRA_LON, liveLon)
            putExtra(EXTRA_SPEED_MPS, liveSpeedMps)
            putExtra(EXTRA_BEARING, liveBearing)
            putExtra(EXTRA_DRIFT, liveDrift)
            putExtra(EXTRA_DISTANCE, liveDistanceMeters)
            putExtra(EXTRA_IS_RUNNING, isRunning)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, MockService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val speedKmH = (liveSpeedMps * 3.6f)
        val title = "Mocka Active: %.4f, %.4f".format(liveLat, liveLon)
        val text = "Speed: %.1f km/h • Heading: %.0f° • Distance: %.0fm".format(speedKmH, liveBearing, liveDistanceMeters)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            val stopAction = Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Stop Spoofing",
                stopPendingIntent
            ).build()
            builder.addAction(stopAction)
        }

        return builder.build()
    }

    private fun startForegroundServiceNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mock Location Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live broadcasted GPS coordinates and status"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun stopMocking() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        if (::lm.isInitialized) {
            providers.forEach { provider ->
                try {
                    lm.setTestProviderEnabled(provider, false)
                    lm.removeTestProvider(provider)
                } catch (_: Exception) {}
            }
        }
        sendTelemetryBroadcast()
    }

    override fun onDestroy() {
        stopMocking()
        super.onDestroy()
    }
}
