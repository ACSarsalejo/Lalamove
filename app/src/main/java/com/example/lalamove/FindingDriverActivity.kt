package com.example.lalamove

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.util.Locale

class FindingDriverActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var pickupPoint: GeoPoint? = null
    private var orderListener: ListenerRegistration? = null
    private var pulseAnimator: ValueAnimator? = null
    private var orderId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finding_driver)

        orderId = intent.getStringExtra("ORDER_ID") ?: ""
        val pickup = intent.getStringExtra("PICKUP") ?: "Pickup"
        val dropoff = intent.getStringExtra("DROPOFF") ?: "Dropoff"
        val totalFare = intent.getStringExtra("TOTAL_FARE") ?: "₱0.00"
        val vehicleType = intent.getStringExtra("VEHICLE_TYPE") ?: "motorcycle"
        val paymentMethod = intent.getStringExtra("PAYMENT_METHOD") ?: "Cash"
        val contactNumber = intent.getStringExtra("CONTACT_NUMBER") ?: ""
        val driverNotes = intent.getStringExtra("DRIVER_NOTES") ?: ""
        val itemDetails = intent.getStringExtra("ITEM_DETAILS") ?: ""
        val userName = intent.getStringExtra("USER_NAME") ?: "You"

        findViewById<TextView>(R.id.findPickupName).text = pickup
        findViewById<TextView>(R.id.findDropoffName).text = dropoff
        findViewById<TextView>(R.id.findOrderId).text = orderId
        findViewById<TextView>(R.id.findTotalFare).text = totalFare
        findViewById<TextView>(R.id.findContactNumber).text = contactNumber
        findViewById<TextView>(R.id.findPlacedBy).text = "$userName (You)"
        findViewById<TextView>(R.id.findPaymentBadge).text = "Pay $paymentMethod"
        findViewById<TextView>(R.id.findVehicleType).text = "Delivery • ${vehicleType.replaceFirstChar { it.uppercase() }}"

        if (itemDetails.isNotEmpty()) {
            findViewById<TextView>(R.id.findItemDetails).text = itemDetails
        }

        val notesContainer = findViewById<View>(R.id.findNotesContainer)
        if (driverNotes.isNotEmpty()) {
            notesContainer.visibility = View.VISIBLE
            findViewById<TextView>(R.id.findDriverNotes).text = driverNotes
        }

        // Fallback coordinates so map can initialise immediately
        pickupPoint = GeoPoint(10.3157, 123.8854)
        findViewById<TextView>(R.id.findPickupAddress).text = pickup
        findViewById<TextView>(R.id.findDropoffAddress).text = dropoff

        // Geocode on a background thread
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val pResults = geocoder.getFromLocationName(pickup, 1)
                val dResults = geocoder.getFromLocationName(dropoff, 1)
                runOnUiThread {
                    pResults?.firstOrNull()?.let { loc ->
                        pickupPoint = GeoPoint(loc.latitude, loc.longitude)
                        findViewById<TextView>(R.id.findPickupAddress).text = loc.getAddressLine(0) ?: pickup
                        setupMap()
                    }
                    dResults?.firstOrNull()?.let { loc ->
                        findViewById<TextView>(R.id.findDropoffAddress).text = loc.getAddressLine(0) ?: dropoff
                    }
                }
            } catch (_: Exception) {}
        }.start()

        mapView = findViewById(R.id.mapFindingDriver)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        MapUtils.stylizeMap(mapView)
        setupMap()

        findViewById<ImageView>(R.id.btnBackFinding).setOnClickListener { finish() }

        findViewById<ImageView>(R.id.btnCopyOrderId).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Order ID", orderId))
            Toast.makeText(this, "Order ID copied!", Toast.LENGTH_SHORT).show()
        }

        // Real-time listener for driver acceptance
        if (orderId.isNotEmpty()) {
            orderListener = FirebaseFirestore.getInstance().collection("booking").document(orderId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val status = snapshot.getString("Book_Status") ?: "pending"
                    val driverAssigned = status == "driver_assigned" || status == "accepted" ||
                        status == "driver_en_route" || status == "picked_up" ||
                        status == "delivered" || status == "completed"
                    if (driverAssigned) {
                        orderListener?.remove()
                        orderListener = null
                        val fareVal = (snapshot.get("Book_TotalFare") as? Number)?.toDouble() ?: 0.0
                        val mysqlBookId = snapshot.get("Book_ID")?.toString() ?: orderId
                        val nextIntent = Intent(this, DriverFoundActivity::class.java).apply {
                            putExtra("ORDER_ID", orderId)
                            putExtra("MYSQL_ORDER_ID", mysqlBookId)
                            putExtra("PICKUP", snapshot.getString("Book_Pickuploc") ?: pickup)
                            putExtra("DROPOFF", snapshot.getString("Book_Dropoffloc") ?: dropoff)
                            putExtra("FARE", "₱${String.format("%.2f", fareVal)}")
                            putExtra("VEHICLE", snapshot.getString("Book_VhclTypeID") ?: vehicleType)
                            putExtra("PAYMENT", snapshot.getString("Book_PaymentMethod") ?: paymentMethod)
                            putExtra("NOTES", snapshot.getString("Book_Notes") ?: "")
                            putExtra("STATUS", status)
                            putExtra("IS_RATED", snapshot.get("Book_IsRated") as? Boolean ?: false)
                            putExtra("DRIVER_ID", snapshot.get("Book_DrvrID")?.toString() ?: "")
                        }
                        startActivity(nextIntent)
                        finish()
                    }
                }
        }
    }

    private fun setupMap() {
        val center = pickupPoint ?: GeoPoint(10.3157, 123.8854)
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(center)

        val marker = Marker(mapView)
        marker.position = center
        marker.title = "Pickup"
        try {
            marker.icon = resources.getDrawable(R.drawable.ic_location, null)
        } catch (_: Exception) {}
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)

        // Add static ring overlay
        val ringOverlay = PulseOverlay(center, isStatic = true, staticRadius = 800f)
        mapView.overlays.add(ringOverlay)

        // Add animated pulse overlay
        startPulseAnimation(center)
        mapView.invalidate()
    }

    private fun startPulseAnimation(center: GeoPoint) {
        val pulseOverlay = PulseOverlay(center, isStatic = false)
        mapView.overlays.add(pulseOverlay)

        pulseAnimator = ValueAnimator.ofFloat(500f, 1500f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                pulseOverlay.radiusMeters = value
                val fraction = (value - 500f) / 1000f
                pulseOverlay.alpha = (100 - (fraction * 70).toInt())
                mapView.invalidate()
            }
            start()
        }
    }

    private inner class PulseOverlay(
        private val center: GeoPoint,
        private val isStatic: Boolean = false,
        private val staticRadius: Float = 0f
    ) : Overlay() {
        var radiusMeters: Float = 800f
        var alpha: Int = 80

        private val fillPaint = Paint().apply {
            color = Color.parseColor("#33FF6B00")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val strokePaint = Paint().apply {
            color = Color.parseColor("#55FF6B00")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val projection = mapView.projection
            val centerPixel = projection.toPixels(center, null)
            val radius = if (isStatic) staticRadius else radiusMeters
            val edgePoint = GeoPoint(center.latitude + radius / 111000.0, center.longitude)
            val edgePixel = projection.toPixels(edgePoint, null)
            val radiusPx = Math.abs(edgePixel.y - centerPixel.y).toFloat()
            if (isStatic) {
                fillPaint.alpha = 40
                strokePaint.alpha = 60
            } else {
                fillPaint.alpha = alpha
                strokePaint.alpha = alpha
            }
            canvas.drawCircle(centerPixel.x.toFloat(), centerPixel.y.toFloat(), radiusPx, fillPaint)
            canvas.drawCircle(centerPixel.x.toFloat(), centerPixel.y.toFloat(), radiusPx, strokePaint)
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
        orderListener?.remove()
        pulseAnimator?.cancel()
    }
}
