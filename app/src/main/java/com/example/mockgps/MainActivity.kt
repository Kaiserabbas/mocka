package com.example.mockgps

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvCurrentCoords: TextView
    private lateinit var tvLiveSpeed: TextView
    private lateinit var tvLiveHeading: TextView
    private lateinit var tvLiveDistance: TextView
    private lateinit var tvLiveDrift: TextView
    private lateinit var btnCopyCoords: TextView
    private lateinit var tvStepSize: TextView

    private lateinit var editLat: EditText
    private lateinit var editLon: EditText
    private lateinit var editSpeed: EditText
    private lateinit var editBearing: EditText
    private lateinit var cbDrift: CheckBox

    private lateinit var prefs: SharedPreferences
    private var selectedStepMeters: Double = 50.0

    private val telemetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val isRunning = intent.getBooleanExtra(MockService.EXTRA_IS_RUNNING, false)
            val lat = intent.getDoubleExtra(MockService.EXTRA_LAT, 0.0)
            val lon = intent.getDoubleExtra(MockService.EXTRA_LON, 0.0)
            val speedMps = intent.getFloatExtra(MockService.EXTRA_SPEED_MPS, 0f)
            val bearing = intent.getFloatExtra(MockService.EXTRA_BEARING, 0f)
            val drift = intent.getBooleanExtra(MockService.EXTRA_DRIFT, false)
            val distance = intent.getDoubleExtra(MockService.EXTRA_DISTANCE, 0.0)

            updateTelemetryUI(isRunning, lat, lon, speedMps, bearing, drift, distance)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("mocka_favorites", Context.MODE_PRIVATE)

        initViews()
        requestAppPermissions()
        setupListeners()
        syncWithServiceState()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MockService.ACTION_TELEMETRY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(telemetryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(telemetryReceiver, filter)
        }
        syncWithServiceState()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(telemetryReceiver)
        } catch (_: Exception) {}
    }

    private fun initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvCurrentCoords = findViewById(R.id.tvCurrentCoords)
        tvLiveSpeed = findViewById(R.id.tvLiveSpeed)
        tvLiveHeading = findViewById(R.id.tvLiveHeading)
        tvLiveDistance = findViewById(R.id.tvLiveDistance)
        tvLiveDrift = findViewById(R.id.tvLiveDrift)
        btnCopyCoords = findViewById(R.id.btnCopyCoords)
        tvStepSize = findViewById(R.id.tvStepSize)

        editLat = findViewById(R.id.lat)
        editLon = findViewById(R.id.lon)
        editSpeed = findViewById(R.id.speed)
        editBearing = findViewById(R.id.bearing)
        cbDrift = findViewById(R.id.cbDrift)

        // Default to Dubai Burj Khalifa if empty
        if (editLat.text.isEmpty()) editLat.setText("25.1972")
        if (editLon.text.isEmpty()) editLon.setText("55.2744")
        if (editSpeed.text.isEmpty()) editSpeed.setText("0")
        if (editBearing.text.isEmpty()) editBearing.setText("0")
    }

    private fun setupListeners() {
        // Copy coordinates
        btnCopyCoords.setOnClickListener {
            val coords = tvCurrentCoords.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Coordinates", coords))
            Toast.makeText(this, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Paste coordinates
        findViewById<Button>(R.id.btnPaste).setOnClickListener {
            pasteCoordinatesFromClipboard()
        }

        // Landmark Presets
        findViewById<Button>(R.id.btnPresets).setOnClickListener {
            showPresetsDialog()
        }

        // Saved Favorites
        findViewById<Button>(R.id.btnFavorites).setOnClickListener {
            showFavoritesDialog()
        }

        // Save Current Bookmark
        findViewById<Button>(R.id.btnSaveBookmark).setOnClickListener {
            showSaveBookmarkDialog()
        }

        // Speed Preset Chips
        findViewById<Button>(R.id.chipSpeedStationary).setOnClickListener { editSpeed.setText("0") }
        findViewById<Button>(R.id.chipSpeedWalk).setOnClickListener { editSpeed.setText("5") }
        findViewById<Button>(R.id.chipSpeedCycle).setOnClickListener { editSpeed.setText("18") }
        findViewById<Button>(R.id.chipSpeedDrive).setOnClickListener { editSpeed.setText("60") }
        findViewById<Button>(R.id.chipSpeedHighway).setOnClickListener { editSpeed.setText("110") }

        // Compass Heading Buttons
        findViewById<Button>(R.id.btnHeadingNorth).setOnClickListener { editBearing.setText("0") }
        findViewById<Button>(R.id.btnHeadingEast).setOnClickListener { editBearing.setText("90") }
        findViewById<Button>(R.id.btnHeadingSouth).setOnClickListener { editBearing.setText("180") }
        findViewById<Button>(R.id.btnHeadingWest).setOnClickListener { editBearing.setText("270") }
        findViewById<Button>(R.id.btnTurnMinus).setOnClickListener { adjustBearing(-15f) }
        findViewById<Button>(R.id.btnTurnPlus).setOnClickListener { adjustBearing(15f) }

        // Step Size Chips
        findViewById<Button>(R.id.chipStep10).setOnClickListener { setStepSize(10.0, "10m") }
        findViewById<Button>(R.id.chipStep50).setOnClickListener { setStepSize(50.0, "50m") }
        findViewById<Button>(R.id.chipStep100).setOnClickListener { setStepSize(100.0, "100m") }
        findViewById<Button>(R.id.chipStep500).setOnClickListener { setStepSize(500.0, "500m") }

        // D-Pad Steer Buttons
        findViewById<Button>(R.id.dpadNorth).setOnClickListener { nudgePosition(0f) }
        findViewById<Button>(R.id.dpadEast).setOnClickListener { nudgePosition(90f) }
        findViewById<Button>(R.id.dpadSouth).setOnClickListener { nudgePosition(180f) }
        findViewById<Button>(R.id.dpadWest).setOnClickListener { nudgePosition(270f) }
        findViewById<Button>(R.id.dpadCenter).setOnClickListener {
            editSpeed.setText("0")
            if (MockService.isRunning) {
                updateRunningService()
            }
            Toast.makeText(this, "Movement halted (Stationary)", Toast.LENGTH_SHORT).show()
        }

        // Main Action: Start Spoofing
        findViewById<Button>(R.id.start).setOnClickListener {
            startMocking()
        }

        // Main Action: Update Live
        findViewById<Button>(R.id.btnUpdateLive).setOnClickListener {
            if (!MockService.isRunning) {
                Toast.makeText(this, "Service is not running. Starting...", Toast.LENGTH_SHORT).show()
                startMocking()
            } else {
                updateRunningService()
                Toast.makeText(this, "Live telemetry updated", Toast.LENGTH_SHORT).show()
            }
        }

        // Main Action: Stop Spoofing
        findViewById<Button>(R.id.stop).setOnClickListener {
            stopMocking()
        }

        // Developer Options Setup Helper
        findViewById<Button>(R.id.dev).setOnClickListener {
            showDeveloperOptionsGuide()
        }
    }

    private fun setStepSize(meters: Double, label: String) {
        selectedStepMeters = meters
        tvStepSize.text = "Step: $label"
        Toast.makeText(this, "Step set to $label", Toast.LENGTH_SHORT).show()
    }

    private fun adjustBearing(delta: Float) {
        val current = editBearing.text.toString().toFloatOrNull() ?: 0f
        var updated = (current + delta) % 360f
        if (updated < 0) updated += 360f
        editBearing.setText(updated.toInt().toString())
        if (MockService.isRunning) {
            updateRunningService()
        }
    }

    private fun nudgePosition(headingDegrees: Float) {
        if (!MockService.isRunning) {
            Toast.makeText(this, "Start spoofing first to use D-Pad steer", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, MockService::class.java).apply {
            action = MockService.ACTION_NUDGE
            putExtra(MockService.EXTRA_STEP_METERS, selectedStepMeters)
            putExtra(MockService.EXTRA_NUDGE_HEADING, headingDegrees)
        }
        startService(intent)
    }

    private fun startMocking() {
        val la = editLat.text.toString().toDoubleOrNull()
        val lo = editLon.text.toString().toDoubleOrNull()
        if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
            Toast.makeText(this, "Please enter valid coordinates (Lat -90..90, Lon -180..180)", Toast.LENGTH_SHORT).show()
            return
        }

        val speedKmH = editSpeed.text.toString().toFloatOrNull() ?: 0f
        val speedMps = speedKmH / 3.6f
        val bearing = editBearing.text.toString().toFloatOrNull() ?: 0f
        val drift = cbDrift.isChecked

        val intent = Intent(this, MockService::class.java).apply {
            action = MockService.ACTION_START
            putExtra(MockService.EXTRA_LAT, la)
            putExtra(MockService.EXTRA_LON, lo)
            putExtra(MockService.EXTRA_SPEED_MPS, speedMps)
            putExtra(MockService.EXTRA_BEARING, bearing)
            putExtra(MockService.EXTRA_DRIFT, drift)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateTelemetryUI(true, la, lo, speedMps, bearing, drift, 0.0)
    }

    private fun updateRunningService() {
        val la = editLat.text.toString().toDoubleOrNull()
        val lo = editLon.text.toString().toDoubleOrNull()
        val speedKmH = editSpeed.text.toString().toFloatOrNull() ?: 0f
        val speedMps = speedKmH / 3.6f
        val bearing = editBearing.text.toString().toFloatOrNull() ?: 0f
        val drift = cbDrift.isChecked

        val intent = Intent(this, MockService::class.java).apply {
            action = MockService.ACTION_UPDATE
            if (la != null && la in -90.0..90.0) putExtra(MockService.EXTRA_LAT, la)
            if (lo != null && lo in -180.0..180.0) putExtra(MockService.EXTRA_LON, lo)
            putExtra(MockService.EXTRA_SPEED_MPS, speedMps)
            putExtra(MockService.EXTRA_BEARING, bearing)
            putExtra(MockService.EXTRA_DRIFT, drift)
        }
        startService(intent)
    }

    private fun stopMocking() {
        val intent = Intent(this, MockService::class.java).apply {
            action = MockService.ACTION_STOP
        }
        startService(intent)
        updateTelemetryUI(false, MockService.liveLat, MockService.liveLon, 0f, 0f, false, 0.0)
        Toast.makeText(this, "Spoofing stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateTelemetryUI(
        isRunning: Boolean,
        lat: Double,
        lon: Double,
        speedMps: Float,
        bearing: Float,
        drift: Boolean,
        distance: Double
    ) {
        if (isRunning) {
            tvStatusBadge.text = "● ACTIVE"
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            tvStatusBadge.setBackgroundResource(R.drawable.badge_active)
        } else {
            tvStatusBadge.text = "● IDLE"
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            tvStatusBadge.setBackgroundResource(R.drawable.badge_inactive)
        }

        tvCurrentCoords.text = "%.6f, %.6f".format(lat, lon)
        tvLiveSpeed.text = "%.1f km/h".format(speedMps * 3.6f)

        val cardinal = when ((((bearing + 22.5f) % 360f) / 45f).toInt()) {
            0 -> "N"
            1 -> "NE"
            2 -> "E"
            3 -> "SE"
            4 -> "S"
            5 -> "SW"
            6 -> "W"
            else -> "NW"
        }
        tvLiveHeading.text = "%.0f° (%s)".format(bearing, cardinal)
        tvLiveDistance.text = if (distance >= 1000) "%.2f km".format(distance / 1000.0) else "%.0f m".format(distance)
        tvLiveDrift.text = if (drift) "ON" else "OFF"
    }

    private fun syncWithServiceState() {
        updateTelemetryUI(
            MockService.isRunning,
            MockService.liveLat,
            MockService.liveLon,
            MockService.liveSpeedMps,
            MockService.liveBearing,
            MockService.liveDrift,
            MockService.liveDistanceMeters
        )
    }

    private fun pasteCoordinatesFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (clip.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val parts = clip.split(",", " ", "\t").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val la = parts[0].toDoubleOrNull()
            val lo = parts[1].toDoubleOrNull()
            if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                editLat.setText(la.toString())
                editLon.setText(lo.toString())
                Toast.makeText(this, "Loaded coordinates: $la, $lo", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "Could not parse coordinates from clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showPresetsDialog() {
        val presets = listOf(
            Triple("🇦🇪 Dubai: Burj Khalifa", 25.1972, 55.2744),
            Triple("🇺🇸 New York: Times Square", 40.7580, -73.9855),
            Triple("🇬🇧 London: Big Ben", 51.5007, -0.1246),
            Triple("🇯🇵 Tokyo: Shibuya Crossing", 35.6595, 139.7005),
            Triple("🇫🇷 Paris: Eiffel Tower", 48.8584, 2.2945),
            Triple("🇦🇺 Sydney: Opera House", -33.8568, 151.2153),
            Triple("🇺🇸 San Francisco: Golden Gate", 37.8199, -122.4783),
            Triple("🇸🇬 Singapore: Marina Bay Sands", 1.2847, 103.8610),
            Triple("🇮🇹 Rome: Colosseum", 41.8902, 12.4922),
            Triple("🇪🇬 Cairo: Pyramids of Giza", 29.9792, 31.1342)
        )

        val names = presets.map { "${it.first}\n(${it.second}, ${it.third})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Landmark Preset")
            .setItems(names) { _, which ->
                val selected = presets[which]
                editLat.setText(selected.second.toString())
                editLon.setText(selected.third.toString())
                Toast.makeText(this, "Selected ${selected.first}", Toast.LENGTH_SHORT).show()
                if (MockService.isRunning) {
                    updateRunningService()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFavoritesDialog() {
        val raw = prefs.getString("bookmarks", "") ?: ""
        val bookmarks = raw.split(";;").filter { it.isNotEmpty() }.mapNotNull { entry ->
            val p = entry.split("|")
            if (p.size == 3) Triple(p[0], p[1].toDoubleOrNull(), p[2].toDoubleOrNull()) else null
        }.filter { it.second != null && it.third != null }

        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No saved favorites yet. Click '+ Bookmark' to save one!", Toast.LENGTH_SHORT).show()
            return
        }

        val names = bookmarks.map { "⭐ ${it.first}\n(${it.second}, ${it.third})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved Favorites")
            .setItems(names) { _, which ->
                val chosen = bookmarks[which]
                editLat.setText(chosen.second.toString())
                editLon.setText(chosen.third.toString())
                Toast.makeText(this, "Loaded ${chosen.first}", Toast.LENGTH_SHORT).show()
                if (MockService.isRunning) {
                    updateRunningService()
                }
            }
            .setNeutralButton("Clear All") { _, _ ->
                prefs.edit().remove("bookmarks").apply()
                Toast.makeText(this, "Favorites cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSaveBookmarkDialog() {
        val la = editLat.text.toString().toDoubleOrNull()
        val lo = editLon.text.toString().toDoubleOrNull()
        if (la == null || lo == null) {
            Toast.makeText(this, "Enter valid coordinates first", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Bookmark Name (e.g. Test Site 1)"
            setSingleLine()
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Save Bookmark")
            .setMessage("Save ($la, $lo) as a quick favorite:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Location ${System.currentTimeMillis() % 1000}" }
                val raw = prefs.getString("bookmarks", "") ?: ""
                val entry = "$name|$la|$lo"
                val updated = if (raw.isEmpty()) entry else "$raw;;$entry"
                prefs.edit().putString("bookmarks", updated).apply()
                Toast.makeText(this, "Saved '$name' to favorites", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeveloperOptionsGuide() {
        AlertDialog.Builder(this)
            .setTitle("Setup Mock Location")
            .setMessage("To allow Mocka to provide GPS coordinates to other apps on your device:\n\n" +
                    "1. Tap 'Open Settings' below.\n" +
                    "2. Scroll down to the 'Debugging' section.\n" +
                    "3. Find 'Select mock location app'.\n" +
                    "4. Choose 'Mocka'.\n\n" +
                    "Once selected, Mocka will broadcast seamless mock coordinates system-wide.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open Developer Options. Enable it in About Phone > Build number.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }
}
