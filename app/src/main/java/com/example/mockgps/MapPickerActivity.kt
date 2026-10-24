package com.example.mockgps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MapPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_LAT = "extra_initial_lat"
        const val EXTRA_INITIAL_LON = "extra_initial_lon"
        const val EXTRA_INITIAL_ALT = "extra_initial_alt"

        const val RESULT_LAT = "result_lat"
        const val RESULT_LON = "result_lon"
        const val RESULT_ALT = "result_alt"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPickedCoords: TextView
    private lateinit var editPickedAlt: EditText
    private lateinit var editSearchPlace: EditText
    private lateinit var prefs: SharedPreferences

    private var initialLat: Double = 25.1972
    private var initialLon: Double = 55.2744
    private var initialAlt: Double = 15.0

    private var currentLat: Double = 25.1972
    private var currentLon: Double = 55.2744
    private var currentAlt: Double = 15.0
    private var isMapLoaded = false

    inner class MapBridge {
        @JavascriptInterface
        fun onCoordinatesChanged(lat: Double, lon: Double) {
            runOnUiThread {
                currentLat = lat
                currentLon = lon
                tvPickedCoords.text = "%.6f, %.6f".format(currentLat, currentLon)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_picker)

        prefs = getSharedPreferences("mocka_favorites", Context.MODE_PRIVATE)

        initialLat = intent.getDoubleExtra(EXTRA_INITIAL_LAT, 25.1972)
        initialLon = intent.getDoubleExtra(EXTRA_INITIAL_LON, 55.2744)
        initialAlt = intent.getDoubleExtra(EXTRA_INITIAL_ALT, 15.0)

        currentLat = initialLat
        currentLon = initialLon
        currentAlt = initialAlt

        initViews()
        setupWebView()
        setupListeners()
    }

    private fun initViews() {
        webView = findViewById(R.id.mapWebView)
        progressBar = findViewById(R.id.mapProgressBar)
        tvPickedCoords = findViewById(R.id.tvPickedCoords)
        editPickedAlt = findViewById(R.id.editPickedAlt)
        editSearchPlace = findViewById(R.id.editSearchPlace)

        tvPickedCoords.text = "%.6f, %.6f".format(currentLat, currentLon)
        editPickedAlt.setText(if (currentAlt % 1.0 == 0.0) currentAlt.toInt().toString() else "%.1f".format(currentAlt))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(MapBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = ProgressBar.GONE
                isMapLoaded = true
                // Center to requested coordinates
                webView.evaluateJavascript("setCoordinates($initialLat, $initialLon, 15);", null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = ProgressBar.VISIBLE
                } else {
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }

        progressBar.visibility = ProgressBar.VISIBLE
        webView.loadUrl("file:///android_asset/map_picker.html")
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.btnRecenter).setOnClickListener {
            if (isMapLoaded) {
                webView.evaluateJavascript("setCoordinates($initialLat, $initialLon, 15);", null)
                Toast.makeText(this, "Centered to original target", Toast.LENGTH_SHORT).show()
            }
        }

        // Altitude quick chips
        findViewById<Button>(R.id.chipAltGround).setOnClickListener { editPickedAlt.setText("0") }
        findViewById<Button>(R.id.chipAltLow).setOnClickListener { editPickedAlt.setText("50") }
        findViewById<Button>(R.id.chipAltHigh).setOnClickListener { editPickedAlt.setText("250") }

        // Search button & keyboard action
        findViewById<Button>(R.id.btnSearchPlace).setOnClickListener {
            performPlaceSearch()
        }
        editSearchPlace.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performPlaceSearch()
                true
            } else false
        }

        // Confirm & Use location
        findViewById<Button>(R.id.btnConfirmLocation).setOnClickListener {
            val altVal = editPickedAlt.text.toString().toDoubleOrNull() ?: currentAlt
            val resultIntent = Intent().apply {
                putExtra(RESULT_LAT, currentLat)
                putExtra(RESULT_LON, currentLon)
                putExtra(RESULT_ALT, altVal)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            Toast.makeText(this, "Location selected: %.4f, %.4f (Alt: %.0fm)".format(currentLat, currentLon, altVal), Toast.LENGTH_SHORT).show()
            finish()
        }

        // Save directly as Bookmark
        findViewById<Button>(R.id.btnSaveAsBookmarkFromMap).setOnClickListener {
            showSaveBookmarkDialog()
        }
    }

    private fun performPlaceSearch() {
        val query = editSearchPlace.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter a location or landmark to search", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        progressBar.visibility = ProgressBar.VISIBLE

        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val urlString = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=1"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mocka-Android-App/1.0")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        val first = array.getJSONObject(0)
                        val lat = first.getDouble("lat")
                        val lon = first.getDouble("lon")
                        val displayName = first.optString("display_name", query)

                        runOnUiThread {
                            progressBar.visibility = ProgressBar.GONE
                            currentLat = lat
                            currentLon = lon
                            webView.evaluateJavascript("setCoordinates($lat, $lon, 16);", null)
                            val shortName = displayName.split(",").take(2).joinToString(", ")
                            Toast.makeText(this, "Found: $shortName", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }
                }

                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "No match found for '$query'. Try city or country name.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Search failed: Check internet connection", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showSaveBookmarkDialog() {
        val altVal = editPickedAlt.text.toString().toDoubleOrNull() ?: currentAlt
        val input = EditText(this).apply {
            hint = "Bookmark Name (e.g. My Beach Spot)"
            setSingleLine()
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Save Bookmark with Altitude")
            .setMessage("Save position:\nLat: %.6f\nLon: %.6f\nAlt: %.1f meters".format(currentLat, currentLon, altVal))
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Map Pin ${System.currentTimeMillis() % 1000}" }
                val raw = prefs.getString("bookmarks", "") ?: ""
                val entry = "$name|$currentLat|$currentLon|$altVal"
                val updated = if (raw.isEmpty()) entry else "$raw;;$entry"
                prefs.edit().putString("bookmarks", updated).apply()
                Toast.makeText(this, "Saved '$name' (Alt: %.0fm) to favorites!".format(altVal), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
