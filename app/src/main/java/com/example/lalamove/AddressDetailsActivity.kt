package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.net.HttpURLConnection
import java.net.URL

class AddressDetailsActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationNameView: TextView

    private var currentLat: Double = 10.3157
    private var currentLng: Double = 123.8854
    private var currentLocationName: String = ""
    private var currentLocationAddress: String = ""

    private val geoHandler = Handler(Looper.getMainLooper())
    private var geoRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_details)

        currentLat             = intent.getDoubleExtra("LOCATION_LAT", 10.3157)
        currentLng             = intent.getDoubleExtra("LOCATION_LNG", 123.8854)
        currentLocationName    = intent.getStringExtra("LOCATION_NAME") ?: ""
        currentLocationAddress = intent.getStringExtra("LOCATION_ADDRESS") ?: ""

        locationNameView = findViewById(R.id.addrLocationName)
        locationNameView.text = currentLocationName

        val back: ImageView = findViewById(R.id.addrBackButton)
        val edit: ImageView = findViewById(R.id.addrEditButton)
        back.setOnClickListener { finish() }
        edit.setOnClickListener { finish() }

        mapView = findViewById(R.id.addrMapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        MapUtils.stylizeMap(mapView)
        mapView.controller.setZoom(17.0)
        mapView.controller.setCenter(GeoPoint(currentLat, currentLng))

        // Reverse-geocode whenever the map stops moving
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                scheduleReverseGeocode()
                return false
            }
            override fun onZoom(event: ZoomEvent): Boolean {
                scheduleReverseGeocode()
                return false
            }
        })

        val inputMobile  = findViewById<EditText>(R.id.inputMobile)
        val inputContact = findViewById<EditText>(R.id.inputContactName)

        findViewById<MaterialButton>(R.id.btnConfirmAddress).setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("LOCATION_NAME",    currentLocationName)
                putExtra("LOCATION_ADDRESS", currentLocationAddress)
                putExtra("LOCATION_LAT",     currentLat)
                putExtra("LOCATION_LNG",     currentLng)
                putExtra("CONTACT_PHONE",    "+63${inputMobile.text?.toString()?.trim() ?: ""}")
                putExtra("CONTACT_NAME",     inputContact.text?.toString()?.trim() ?: "")
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun scheduleReverseGeocode() {
        geoRunnable?.let { geoHandler.removeCallbacks(it) }
        geoRunnable = Runnable {
            val center = mapView.mapCenter
            reverseGeocode(center.latitude, center.longitude)
        }
        geoHandler.postDelayed(geoRunnable!!, 700)
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        Thread {
            try {
                val urlStr = "https://nominatim.openstreetmap.org/reverse" +
                        "?lat=$lat&lon=$lng&format=json&zoom=16"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout    = 6000
                conn.setRequestProperty("User-Agent", packageName)
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(text)
                val displayName = json.optString("display_name", "")
                val parts   = displayName.split(",")
                val name    = parts.take(2).joinToString(",").trim()
                val address = parts.drop(2).take(3).joinToString(",").trim()

                runOnUiThread {
                    currentLat             = lat
                    currentLng             = lng
                    currentLocationName    = name.ifEmpty { displayName.take(60) }
                    currentLocationAddress = address
                    locationNameView.text  = currentLocationName
                }
            } catch (_: Exception) {}
        }.start()
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause()  {
        super.onPause()
        geoRunnable?.let { geoHandler.removeCallbacks(it) }
        mapView.onPause()
    }
}
