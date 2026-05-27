package com.example.lalamove

import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class DriverActiveDeliveryActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var pickupPoint: GeoPoint? = null
    private var dropoffPoint: GeoPoint? = null
    private var pickup = ""
    private var dropoff = ""
    private var orderId = ""
    private var isProofCaptured = false

    private var driverMarker: Marker? = null
    private var routePolyline: Polyline? = null
    private val simPoints = ArrayList<GeoPoint>()
    private var simIndex = 0
    private var isPickedUp = false
    private var vehicleType = "motorcycle"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var markerAnimator: ValueAnimator? = null

    private val trackingRunnable = object : Runnable {
        override fun run() {
            if (simIndex < simPoints.size) {
                val nextPt = simPoints[simIndex]
                val startPt = driverMarker?.position ?: nextPt

                // Calculate bearing and rotate marker
                val bearing = getBearing(startPt, nextPt)
                val offset = when (vehicleType.lowercase()) {
                    "motorcycle" -> -90f
                    "suv" -> -90f
                    "van" -> -90f
                    else -> 0f
                }
                driverMarker?.rotation = bearing + offset

                // Animate position transition smoothly
                markerAnimator?.cancel()
                markerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 2400
                    addUpdateListener { animation ->
                        val fraction = animation.animatedValue as Float
                        val lat = startPt.latitude + (nextPt.latitude - startPt.latitude) * fraction
                        val lng = startPt.longitude + (nextPt.longitude - startPt.longitude) * fraction
                        driverMarker?.position = GeoPoint(lat, lng)
                        mapView.invalidate()
                    }
                    start()
                }

                // Push simulated location to Firestore
                FirebaseFirestore.getInstance().collection("booking").document(orderId)
                    .update("driver_lat", nextPt.latitude, "driver_lng", nextPt.longitude)

                simIndex++
            }
            mainHandler.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_active_delivery)

        orderId = intent.getStringExtra("ORDER_ID") ?: ""
        pickup = intent.getStringExtra("PICKUP") ?: ""
        dropoff = intent.getStringExtra("DROPOFF") ?: ""
        val fare = intent.getStringExtra("FARE") ?: "₱0.00"
        vehicleType = intent.getStringExtra("VEHICLE") ?: "motorcycle"

        findViewById<TextView>(R.id.deliveryPickup).text = pickup
        findViewById<TextView>(R.id.deliveryDropoff).text = dropoff
        findViewById<TextView>(R.id.deliveryFare).text = fare

        // Geocode locations (synchronous)
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val pResults = geocoder.getFromLocationName(pickup, 1)
            if (!pResults.isNullOrEmpty()) pickupPoint = GeoPoint(pResults[0].latitude, pResults[0].longitude)
            val dResults = geocoder.getFromLocationName(dropoff, 1)
            if (!dResults.isNullOrEmpty()) dropoffPoint = GeoPoint(dResults[0].latitude, dResults[0].longitude)
        } catch (e: Exception) {}

        if (pickupPoint == null) pickupPoint = GeoPoint(10.3157, 123.8854)
        if (dropoffPoint == null) dropoffPoint = GeoPoint(10.3257, 123.8954)

        mapView = findViewById(R.id.mapActiveDelivery)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        MapUtils.stylizeMap(mapView)

        findViewById<ImageView>(R.id.btnBackDelivery).setOnClickListener { finish() }

        val btnAddProof = findViewById<MaterialButton>(R.id.btnAddProof)
        val btnMainAction = findViewById<MaterialButton>(R.id.btnMainAction)

        // Fetch current status from Firestore and setup map
        FirebaseFirestore.getInstance().collection("booking").document(orderId).get()
            .addOnSuccessListener { doc ->
                val status = doc.getString("Book_Status") ?: "accepted"
                isPickedUp = (status == "picked_up")
                if (isPickedUp) {
                    btnMainAction.text = "Confirm Delivery"
                    btnAddProof.visibility = View.VISIBLE
                } else {
                    btnMainAction.text = "Confirm Pick Up"
                    btnAddProof.visibility = View.GONE
                }
                setupMap()
            }
            .addOnFailureListener {
                setupMap()
            }

        btnMainAction.setOnClickListener {
            if (!isPickedUp) {
                btnMainAction.isEnabled = false
                btnMainAction.text = "Updating..."
                FirebaseFirestore.getInstance().collection("booking").document(orderId)
                    .update("Book_Status", "picked_up")
                    .addOnSuccessListener {
                        ApiClient.triggerSync()
                        isPickedUp = true
                        btnMainAction.isEnabled = true
                        btnMainAction.text = "Confirm Delivery"
                        btnAddProof.visibility = View.VISIBLE
                        Toast.makeText(this, "Pick Up Confirmed", Toast.LENGTH_SHORT).show()

                        // Start Leg 2: pickup to dropoff
                        val pLoc = pickupPoint
                        val dLoc = dropoffPoint
                        if (pLoc != null && dLoc != null) {
                            startSimulationLeg(pLoc, dLoc)
                        }
                    }
                    .addOnFailureListener { e ->
                        btnMainAction.isEnabled = true
                        btnMainAction.text = "Confirm Pick Up"
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Confirm Delivery")
                    .setMessage("Are you sure you have completed this delivery?")
                    .setPositiveButton("Yes, Confirm") { _, _ -> completeOrder() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        btnAddProof.setOnClickListener {
            Toast.makeText(this, "📷 Capturing Proof of Delivery...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                btnAddProof.text = "Proof Added ✅"
                btnAddProof.isEnabled = false
                Toast.makeText(this, "Proof uploaded successfully", Toast.LENGTH_SHORT).show()
            }, 1500)
        }
    }

    private fun setupMap() {
        val pLoc = pickupPoint ?: return
        val dLoc = dropoffPoint ?: return

        mapView.overlays.clear()

        val pickupMarker = Marker(mapView)
        pickupMarker.position = pLoc
        pickupMarker.title = "Pickup"
        try {
            pickupMarker.icon = resources.getDrawable(R.drawable.ic_location, null)
        } catch (_: Exception) {}
        pickupMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(pickupMarker)

        val dropoffMarker = Marker(mapView)
        dropoffMarker.position = dLoc
        dropoffMarker.title = "Dropoff"
        try {
            dropoffMarker.icon = resources.getDrawable(R.drawable.ic_location_orange, null)
        } catch (_: Exception) {}
        dropoffMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(dropoffMarker)

        routePolyline = Polyline()
        routePolyline?.outlinePaint?.color = Color.parseColor("#FF6B00")
        routePolyline?.outlinePaint?.strokeWidth = 8f
        mapView.overlays.add(routePolyline)

        val dMarker = Marker(mapView)
        val iconRes = when (vehicleType.lowercase()) {
            "motorcycle" -> R.drawable.ic_motorcycle
            "sedan" -> R.drawable.ic_sedan
            "suv" -> R.drawable.ic_suv
            "van" -> R.drawable.ic_van
            else -> R.drawable.ic_motorcycle
        }
        try {
            dMarker.icon = resources.getDrawable(iconRes, null)
        } catch (_: Exception) {}
        dMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        dMarker.title = "You (Driver)"
        mapView.overlays.add(dMarker)
        driverMarker = dMarker

        val boundingBox = BoundingBox.fromGeoPoints(listOf(pLoc, dLoc))
        mapView.post {
            mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.6f), false)
        }
        mapView.invalidate()

        if (!isPickedUp) {
            // Leg 1: Driver heading to pickup (simulated starting from a 0.008 offset)
            val offsetLat = pLoc.latitude - 0.008
            val offsetLng = pLoc.longitude - 0.008
            startSimulationLeg(GeoPoint(offsetLat, offsetLng), pLoc)
        } else {
            // Leg 2: Driver heading to dropoff
            startSimulationLeg(pLoc, dLoc)
        }
    }

    private fun startSimulationLeg(start: GeoPoint, end: GeoPoint) {
        mainHandler.removeCallbacks(trackingRunnable)
        markerAnimator?.cancel()
        simPoints.clear()
        simIndex = 0

        ApiClient.fetchOSRMRoute(start.latitude, start.longitude, end.latitude, end.longitude) { points ->
            if (!points.isNullOrEmpty()) {
                simPoints.addAll(points)
            } else {
                // Fallback: interpolate 30 steps
                val steps = 30
                for (i in 0..steps) {
                    val f = i.toDouble() / steps
                    val lat = start.latitude + (end.latitude - start.latitude) * f
                    val lng = start.longitude + (end.longitude - start.longitude) * f
                    simPoints.add(GeoPoint(lat, lng))
                }
            }

            routePolyline?.setPoints(simPoints)
            mapView.invalidate()

            mainHandler.post(trackingRunnable)
        }
    }

    private fun getBearing(start: GeoPoint, end: GeoPoint): Float {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = Math.toRadians(end.latitude)
        val lon2 = Math.toRadians(end.longitude)

        val dLon = lon2 - lon1
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        var brng = Math.atan2(y, x)
        brng = Math.toDegrees(brng)
        return ((brng + 360) % 360).toFloat()
    }

    private fun completeOrder() {
        val btnMainAction = findViewById<MaterialButton>(R.id.btnMainAction)
        btnMainAction.isEnabled = false
        btnMainAction.text = "Completing..."

        FirebaseFirestore.getInstance().collection("booking").document(orderId)
            .update("Book_Status", "delivered")
            .addOnSuccessListener {
                ApiClient.triggerSync()
                Toast.makeText(this, "🎉 Delivery completed!", Toast.LENGTH_LONG).show()
                val intent = Intent(this, DriverDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                btnMainAction.isEnabled = true
                btnMainAction.text = "Confirm Delivery"
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(trackingRunnable)
        markerAnimator?.cancel()
    }
}
