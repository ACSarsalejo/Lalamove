package com.example.lalamove

import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

class CustomerOrderDetailActivity : AppCompatActivity() {

    private var pickupPoint: GeoPoint? = null
    private var dropoffPoint: GeoPoint? = null
    private var pickup = ""
    private var dropoff = ""
    private var orderId = ""
    private lateinit var mapView: MapView
    private lateinit var firestore: FirebaseFirestore
    private var orderListener: ListenerRegistration? = null
    private var vehicle = ""
    private var driverMarker: Marker? = null
    private var driverAnimator: ValueAnimator? = null
    private var currentStatus = ""
    private var shouldZoomAndCenter = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = packageName
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_order_detail)

        firestore = FirebaseFirestore.getInstance()

        orderId = intent.getStringExtra("ORDER_ID") ?: ""
        val mysqlOrderId = intent.getStringExtra("MYSQL_ORDER_ID") ?: orderId
        pickup = intent.getStringExtra("PICKUP") ?: ""
        dropoff = intent.getStringExtra("DROPOFF") ?: ""
        val fare = intent.getStringExtra("FARE") ?: "₱0.00"
        vehicle = intent.getStringExtra("VEHICLE") ?: ""
        val payment = intent.getStringExtra("PAYMENT") ?: "Cash"
        val notes = intent.getStringExtra("NOTES") ?: ""
        val status = intent.getStringExtra("STATUS") ?: "pending"
        currentStatus = status
        val isRated = intent.getBooleanExtra("IS_RATED", false)

        findViewById<TextView>(R.id.custDetailPickup).text = pickup
        findViewById<TextView>(R.id.custDetailDropoff).text = dropoff
        findViewById<TextView>(R.id.custDetailTotalFare).text = fare
        // Show MySQL order ID when available, fall back to Firestore doc ID
        val displayOrderId = if (mysqlOrderId != orderId) mysqlOrderId else orderId
        findViewById<TextView>(R.id.custDetailOrderId).text = displayOrderId
        findViewById<TextView>(R.id.custDetailVehicle).text = vehicle.replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.custDetailPayment).text = payment

        if (notes.isNotEmpty()) {
            findViewById<View>(R.id.custDetailNotesSection).visibility = View.VISIBLE
            findViewById<TextView>(R.id.custDetailNotes).text = notes
        }

        findViewById<ImageView>(R.id.btnBackOrderDetail).setOnClickListener { finish() }

        // Setup map immediately with fallback coordinates
        pickupPoint  = GeoPoint(10.3157, 123.8854)
        dropoffPoint = GeoPoint(10.3257, 123.8954)

        mapView = findViewById(R.id.mapCustomerOrder)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        MapUtils.stylizeMap(mapView)
        setupMap()

        // Geocode on background thread and update map when done
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val pResults = if (pickup.isNotEmpty()) geocoder.getFromLocationName(pickup, 1) else null
                val dResults = if (dropoff.isNotEmpty()) geocoder.getFromLocationName(dropoff, 1) else null
                val pPoint = pResults?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                val dPoint = dResults?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                if (pPoint != null || dPoint != null) {
                    runOnUiThread {
                        if (pPoint != null) pickupPoint = pPoint
                        if (dPoint != null) dropoffPoint = dPoint
                        setupMap()
                    }
                }
            } catch (_: Exception) {}
        }.start()

        updateStatusUI(status, isRated, intent.getStringExtra("DRIVER_ID"))

        orderListener = firestore.collection("booking").document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                try {
                    val liveStatus = snapshot.getString("Book_Status") ?: "pending"
                    val statusChanged = liveStatus != currentStatus
                    if (statusChanged) {
                        currentStatus = liveStatus
                        shouldZoomAndCenter = true
                        setupMap()
                    }

                    val liveRated  = snapshot.get("Book_IsRated") as? Boolean ?: false
                    // Web stores Book_DrvrID as integer; use get() to handle both types
                    val liveDrvrId = snapshot.get("Book_DrvrID")?.toString()?.takeIf { it != "null" }
                    // Update display ID once MySQL assigns it
                    val liveMysqlId = snapshot.get("Book_ID")?.toString()
                    if (!liveMysqlId.isNullOrEmpty() && liveMysqlId != "null") {
                        findViewById<TextView>(R.id.custDetailOrderId).text = liveMysqlId
                    }
                    updateStatusUI(liveStatus, liveRated, liveDrvrId)

                    // Read live coordinates
                    val driverLat = snapshot.getDouble("driver_lat")
                    val driverLng = snapshot.getDouble("driver_lng")
                    if (driverLat != null && driverLng != null) {
                        updateDriverLocationOnMap(GeoPoint(driverLat, driverLng))
                    }
                } catch (_: Exception) {}
            }

        findViewById<MaterialButton>(R.id.btnCancelOrder).setOnClickListener {
            showCancelConfirmation()
        }
    }

    private fun setupMap() {
        val pLoc = pickupPoint ?: return
        val dLoc = dropoffPoint ?: return

        mapView.overlays.clear()

        val pickupMarker = Marker(mapView)
        pickupMarker.position = pLoc
        pickupMarker.title = "Pickup: $pickup"
        try {
            pickupMarker.icon = resources.getDrawable(R.drawable.ic_location, null)
        } catch (_: Exception) {}
        pickupMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(pickupMarker)

        val dropoffMarker = Marker(mapView)
        dropoffMarker.position = dLoc
        dropoffMarker.title = "Dropoff: $dropoff"
        try {
            dropoffMarker.icon = resources.getDrawable(R.drawable.ic_location_orange, null)
        } catch (_: Exception) {}
        dropoffMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(dropoffMarker)

        driverMarker?.let {
            mapView.overlays.add(it)
        }

        val polyline = Polyline()
        polyline.outlinePaint.color = Color.parseColor("#FF6B00")
        polyline.outlinePaint.strokeWidth = 8f
        mapView.overlays.add(polyline)

        val startPt: GeoPoint
        val endPt: GeoPoint
        if (currentStatus == "picked_up") {
            startPt = pLoc
            endPt = dLoc
        } else {
            // Driver is heading to pickup. Start from driver's current position if known, otherwise offset.
            startPt = driverMarker?.position ?: GeoPoint(pLoc.latitude - 0.008, pLoc.longitude - 0.008)
            endPt = pLoc
        }

        ApiClient.fetchOSRMRoute(startPt.latitude, startPt.longitude, endPt.latitude, endPt.longitude) { points ->
            if (!points.isNullOrEmpty()) {
                polyline.setPoints(points)
            } else {
                polyline.setPoints(listOf(startPt, endPt))
            }
            mapView.invalidate()
        }

        val boundingBox = BoundingBox.fromGeoPoints(listOf(pLoc, dLoc))
        mapView.post {
            mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.6f), false)
        }
        mapView.invalidate()
    }

    private fun updateDriverLocationOnMap(driverPt: GeoPoint) {
        if (driverMarker == null) {
            val dMarker = Marker(mapView)
            val iconRes = when (vehicle.lowercase()) {
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
            dMarker.title = "Driver Partner"
            mapView.overlays.add(dMarker)
            driverMarker = dMarker

            driverMarker?.position = driverPt
            mapView.controller.setZoom(16.5)
            mapView.controller.setCenter(driverPt)
            mapView.invalidate()
            return
        }

        if (shouldZoomAndCenter) {
            shouldZoomAndCenter = false
            mapView.controller.setZoom(16.5)
            mapView.controller.animateTo(driverPt)
        }

        val startPt = driverMarker?.position ?: driverPt
        val bearing = getBearing(startPt, driverPt)
        val offset = when (vehicle.lowercase()) {
            "motorcycle" -> -90f
            "suv" -> -90f
            "van" -> -90f
            else -> 0f
        }
        driverMarker?.rotation = bearing + offset

        driverAnimator?.cancel()
        driverAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val lat = startPt.latitude + (driverPt.latitude - startPt.latitude) * fraction
                val lng = startPt.longitude + (driverPt.longitude - startPt.longitude) * fraction
                val currentAnimatedPt = GeoPoint(lat, lng)
                driverMarker?.position = currentAnimatedPt
                mapView.controller.setCenter(currentAnimatedPt)
                mapView.invalidate()
            }
            start()
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

    private fun updateStatusUI(status: String, isRated: Boolean, driverId: String?) {
        val statusBanner = findViewById<LinearLayout>(R.id.statusBanner)
        val textStatus = findViewById<TextView>(R.id.textOrderStatus)
        val statusDot = findViewById<View>(R.id.statusIndicator)
        val bottomBar = findViewById<LinearLayout>(R.id.bottomActionBar)
        val btnAction = findViewById<MaterialButton>(R.id.btnOrderAction)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancelOrder)
        val profileSection = findViewById<LinearLayout>(R.id.driverProfileSection)
        val textHeader = findViewById<TextView>(R.id.textHeaderTitle)
        val statusIcon = findViewById<ImageView>(R.id.statusIcon)

        btnCancel.visibility = if (status == "pending") View.VISIBLE else View.GONE

        // Update header title based on status
        textHeader.text = when (status) {
            "pending" -> "Finding Driver"
            "driver_assigned", "accepted", "driver_en_route", "picked_up" -> "In Transit"
            "delivered", "completed" -> "Delivered"
            "cancelled" -> "Cancelled"
            else -> "Order Details"
        }

        val hasDriver = !driverId.isNullOrEmpty() && status != "pending" && status != "cancelled"
        if (hasDriver) {
            firestore.collection("driver").document(driverId!!).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val firstName = doc.getString("Drvr_FirstName") ?: "Driver"
                    val lastName = doc.getString("Drvr_LastName") ?: ""
                    val vehicleId = doc.get("Drvr_VehicleID")?.toString()
                    val vehicleType = doc.getString("Drvr_VhclTypeID")?.replaceFirstChar { it.uppercase() } ?: "Vehicle"
                    findViewById<TextView>(R.id.driverName).text = "$firstName $lastName"

                    // Setup Chat & Call Buttons
                    val phone = doc.getString("Drvr_PhoneNum") ?: ""
                    findViewById<MaterialButton>(R.id.btnDriverCall).setOnClickListener {
                        if (phone.isNotEmpty()) {
                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            startActivity(callIntent)
                        } else {
                            Toast.makeText(this, "Driver phone number not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                    findViewById<MaterialButton>(R.id.btnDriverChat).setOnClickListener {
                        Toast.makeText(this, "Opening chat with $firstName...", Toast.LENGTH_SHORT).show()
                    }

                    // Format and display rating
                    val ratingVal = doc.getDouble("Drvr_Rating") ?: 5.0
                    val ratingFormatted = String.format(Locale.US, "⭐ %.2f", ratingVal)
                    findViewById<TextView>(R.id.driverRatingText).text = ratingFormatted

                    if (!vehicleId.isNullOrEmpty()) {
                        firestore.collection("vehicle").document(vehicleId).get().addOnSuccessListener { vDoc ->
                            val plateNumber = vDoc.getString("Vhcl_PlateNumber") ?: "—"
                            val color = vDoc.getString("Vhcl_Color") ?: ""
                            findViewById<TextView>(R.id.driverPlateAndVehicle).text = "$plateNumber • $vehicleType"

                            val message = when (status) {
                                "delivered", "completed" ->
                                    "Delivered by $firstName $lastName. ($color $vehicleType, $plateNumber)"
                                else ->
                                    "$firstName $lastName is on the way. Look for a $color $vehicleType with plate $plateNumber."
                            }
                            findViewById<TextView>(R.id.driverMessage).text = message
                        }
                    } else {
                        val plateNumber = doc.getString("Drvr_LicenseNum") ?: "—"
                        findViewById<TextView>(R.id.driverPlateAndVehicle).text = "$plateNumber • $vehicleType"

                        val message = when (status) {
                            "delivered", "completed" -> "Delivered by $firstName $lastName."
                            else -> "$firstName $lastName has accepted your booking and is on the way."
                        }
                        findViewById<TextView>(R.id.driverMessage).text = message
                    }
                    profileSection.visibility = View.VISIBLE
                }
            }
        } else {
            profileSection.visibility = View.GONE
        }

        when (status) {
            "pending" -> {
                statusBanner.setBackgroundColor(Color.parseColor("#FFF3E0"))
                textStatus.text = "Waiting for a driver..."
                textStatus.setTextColor(Color.parseColor("#FF6B00"))
                statusIcon.setColorFilter(Color.parseColor("#FF6B00"))
                (statusDot?.background as? GradientDrawable)?.setColor(Color.parseColor("#FF6B00"))
                bottomBar.visibility = View.GONE
            }
            "driver_assigned", "accepted" -> {
                statusBanner.setBackgroundColor(Color.parseColor("#E3F2FD"))
                textStatus.text = "Driver assigned • Heading to pickup"
                textStatus.setTextColor(Color.parseColor("#1976D2"))
                statusIcon.setColorFilter(Color.parseColor("#1976D2"))
                (statusDot?.background as? GradientDrawable)?.setColor(Color.parseColor("#1976D2"))
                bottomBar.visibility = View.GONE
            }
            "driver_en_route" -> {
                statusBanner.setBackgroundColor(Color.parseColor("#E3F2FD"))
                textStatus.text = "Driver is heading to the pick-up point."
                textStatus.setTextColor(Color.parseColor("#1565C0"))
                statusIcon.setColorFilter(Color.parseColor("#1565C0"))
                (statusDot?.background as? GradientDrawable)?.setColor(Color.parseColor("#1565C0"))
                bottomBar.visibility = View.GONE
            }
            "picked_up" -> {
                statusBanner.setBackgroundColor(Color.parseColor("#EDE7F6"))
                textStatus.text = "Package picked up • In transit"
                textStatus.setTextColor(Color.parseColor("#6A1B9A"))
                statusIcon.setColorFilter(Color.parseColor("#6A1B9A"))
                (statusDot?.background as? GradientDrawable)?.setColor(Color.parseColor("#6A1B9A"))
                bottomBar.visibility = View.GONE
            }
            "delivered", "completed" -> {
                statusBanner.setBackgroundColor(Color.parseColor("#E8F5E9"))
                textStatus.text = if (isRated) "Delivered • Rated ⭐" else "Delivered • Rate your driver"
                textStatus.setTextColor(Color.parseColor("#4CAF50"))
                statusIcon.setColorFilter(Color.parseColor("#4CAF50"))
                (statusDot?.background as? GradientDrawable)?.setColor(Color.parseColor("#4CAF50"))
                if (!isRated) {
                    bottomBar.visibility = View.VISIBLE
                    btnAction.text = "Rate Driver ⭐"
                    btnAction.setBackgroundColor(Color.parseColor("#FF6B00"))
                    btnAction.setOnClickListener { showRateDialog() }
                } else {
                    bottomBar.visibility = View.GONE
                }
            }
            "cancelled" -> {
                statusBanner.setBackgroundColor(Color.parseColor("#FFEBEE"))
                textStatus.text = "Order Cancelled"
                textStatus.setTextColor(Color.parseColor("#E53935"))
                statusIcon.setColorFilter(Color.parseColor("#E53935"))
                (statusDot?.background as? GradientDrawable)?.setColor(Color.parseColor("#E53935"))
                bottomBar.visibility = View.GONE
                btnCancel.visibility = View.GONE
            }
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Order?")
            .setMessage("Are you sure you want to cancel this order?")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Final Confirmation")
                    .setMessage("This action cannot be undone. Cancel order permanently?")
                    .setPositiveButton("Confirm Cancellation") { _, _ ->
                        firestore.collection("booking").document(orderId)
                            .update("Book_Status", "cancelled")
                            .addOnSuccessListener {
                                ApiClient.triggerSync()
                                Toast.makeText(this, "Order Cancelled", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Delivery")
            .setMessage("Has your delivery been received successfully?")
            .setPositiveButton("Yes, Received") { _, _ ->
                firestore.collection("booking").document(orderId)
                    .update("Book_Status", "completed")
                    .addOnSuccessListener {
                        ApiClient.triggerSync()
                        Toast.makeText(this, "Delivery confirmed!", Toast.LENGTH_SHORT).show()
                        showRateDialog()
                    }
            }
            .setNegativeButton("Not Yet", null)
            .show()
    }

    private fun showRateDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rate_driver, null)
        val stars = listOf(
            dialogView.findViewById<ImageView>(R.id.star1),
            dialogView.findViewById<ImageView>(R.id.star2),
            dialogView.findViewById<ImageView>(R.id.star3),
            dialogView.findViewById<ImageView>(R.id.star4),
            dialogView.findViewById<ImageView>(R.id.star5)
        )
        val inputFeedback = dialogView.findViewById<EditText>(R.id.inputFeedback)
        var selectedRating = 0

        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                selectedRating = index + 1
                stars.forEachIndexed { i, s ->
                    s.setImageResource(
                        if (i < selectedRating) android.R.drawable.btn_star_big_on
                        else android.R.drawable.btn_star_big_off
                    )
                }
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Submit Rating") { _, _ ->
                if (selectedRating == 0) {
                    Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val feedback = inputFeedback.text.toString().trim()
                firestore.collection("booking").document(orderId)
                    .update(mapOf(
                        "Book_Status"      to "completed",
                        "Book_IsRated"     to true,
                        "Book_RatingGiven" to selectedRating,
                        "Book_Feedback"    to feedback
                    ))
                    .addOnSuccessListener {
                        ApiClient.triggerSync()
                        Toast.makeText(this, "Thank you for your feedback! ⭐", Toast.LENGTH_LONG).show()
                        val intent = Intent(this, VehicleSelectionActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
            }
            .setNegativeButton("Skip", null)
            .setCancelable(false)
            .show()
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
        driverAnimator?.cancel()
    }
}
