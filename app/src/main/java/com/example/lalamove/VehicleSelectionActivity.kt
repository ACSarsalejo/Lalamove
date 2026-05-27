package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

class VehicleSelectionActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var priorityBehavior: BottomSheetBehavior<LinearLayout>

    private var selectedVehicleCard: MaterialCardView? = null
    private var selectedCheck: ImageView? = null
    private var selectedVehicleType = ""
    private var currentLocationType = "pickup"

    // The currently visible expansion section (one per vehicle card)
    private var currentExpansion: LinearLayout? = null

    // Pricing
    private var basePrice = 0.0
    private var perKmPrice = 0.0
    private var distanceKm = 0.0
    private var serviceQueuePrice = 0.0

    // Service toggle state (shared across all vehicle expansions)
    private var isQueueSelected = false
    private var isFaceProfileSelected = true  // default on, like the web
    private var isThermalSelected = false
    private val selectedServices = mutableListOf("Require driver to show Face and Driver app Profile")

    // Priority tier
    private var selectedTier = "regular"

    // Location
    private var pickupLat: Double? = null
    private var pickupLng: Double? = null
    private var dropoffLat: Double? = null
    private var dropoffLng: Double? = null

    private val vehicleIcons = mapOf(
        "motorcycle" to R.drawable.ic_vehicle_motorcycle,
        "sedan"      to R.drawable.ic_vehicle_sedan,
        "suv_small"  to R.drawable.ic_vehicle_suv_small,
        "suv_large"  to R.drawable.ic_vehicle_suv_large,
        "van"        to R.drawable.ic_vehicle_van,
        "truck"      to R.drawable.ic_vehicle_truck
    )

    private val vehicleViewIds = listOf(
        R.id.itemMotorcycle, R.id.itemSedan, R.id.itemSubcompact,
        R.id.itemSUV, R.id.itemPickup, R.id.itemCargoVan
    )

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val locationName = data?.getStringExtra("LOCATION_NAME") ?: ""
            val lat = data?.getDoubleExtra("LOCATION_LAT", 0.0) ?: 0.0
            val lng = data?.getDoubleExtra("LOCATION_LNG", 0.0) ?: 0.0
            if (currentLocationType == "pickup") {
                findViewById<TextView>(R.id.pickupLocationText).text = locationName
                pickupLat = lat; pickupLng = lng
            } else {
                findViewById<TextView>(R.id.dropoffLocationText).text = locationName
                dropoffLat = lat; dropoffLng = lng
            }
            calculateDistance()
            onConditionsChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_selection)

        firestore = FirebaseFirestore.getInstance()

        // Persistent bottom sheet setup (no dialog, no scrim)
        val sheetView = findViewById<LinearLayout>(R.id.prioritySheetView)
        priorityBehavior = BottomSheetBehavior.from(sheetView)
        priorityBehavior.isHideable = true
        priorityBehavior.peekHeight = 0
        priorityBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Tier card clicks
        findViewById<MaterialCardView>(R.id.cardPriority).setOnClickListener { applyTier("priority") }
        findViewById<MaterialCardView>(R.id.cardRegular).setOnClickListener  { applyTier("regular") }
        findViewById<MaterialCardView>(R.id.cardPooling).setOnClickListener  { applyTier("pooling") }
        applyTier("regular")  // default visual state

        // Next button
        findViewById<MaterialButton>(R.id.btnProceed).setOnClickListener {
            val finalPrice = priceForTier(selectedTier)
            startActivity(Intent(this, OrderDetailsActivity::class.java).apply {
                putExtra("TOTAL_PRICE",  "₱%.2f".format(finalPrice))
                putExtra("VEHICLE_TYPE", selectedVehicleType)
                putExtra("ADD_SERVICES", selectedServices.toSet().joinToString(", "))
                putExtra("PICKUP",       findViewById<TextView>(R.id.pickupLocationText).text.toString())
                putExtra("DROPOFF",      findViewById<TextView>(R.id.dropoffLocationText).text.toString())
                putExtra("DISTANCE_KM",  distanceKm)
            })
        }

        // Drawer
        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.drawerName).text = SessionManager.getName(this)
        headerView.findViewById<TextView>(R.id.drawerEmail).text = SessionManager.getEmail(this)
        val initials = SessionManager.getName(this).split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
        headerView.findViewById<TextView>(R.id.drawerInitials).text = initials.ifEmpty { "?" }
        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_orders        -> startActivity(Intent(this, CustomerOrdersActivity::class.java))
                R.id.nav_wallet        -> startActivity(Intent(this, WalletActivity::class.java))
                R.id.nav_my_drivers    -> startActivity(Intent(this, MyDriversActivity::class.java))
                R.id.nav_delivery_form -> startActivity(Intent(this, DeliveryFormActivity::class.java))
                R.id.nav_help          -> Toast.makeText(this, "Help Center coming soon!", Toast.LENGTH_SHORT).show()
                R.id.nav_settings      -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_sign_out      -> {
                    SessionManager.clear(this)
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
                }
            }
            true
        }

        // Check if this is a fresh start (coming from rating/completion)
        val hasPrefill = intent.getStringExtra("PREFILL_PICKUP")?.takeIf { it.isNotEmpty() } != null ||
                         intent.getStringExtra("PREFILL_DROPOFF")?.takeIf { it.isNotEmpty() } != null

        if (hasPrefill) {
            // Prefill from Reorder intent
            intent.getStringExtra("PREFILL_PICKUP")?.takeIf { it.isNotEmpty() }?.let {
                findViewById<TextView>(R.id.pickupLocationText).text = it
            }
            intent.getStringExtra("PREFILL_DROPOFF")?.takeIf { it.isNotEmpty() }?.let {
                findViewById<TextView>(R.id.dropoffLocationText).text = it
            }
        } else {
            // Fresh start: reset any previous selection
            resetSelectionState()
        }

        // Location pickers
        findViewById<LinearLayout>(R.id.pickupLocation).setOnClickListener {
            currentLocationType = "pickup"
            locationLauncher.launch(Intent(this, LocationSelectionActivity::class.java).putExtra("LOCATION_TYPE", "pickup"))
        }
        findViewById<LinearLayout>(R.id.dropoffLocation).setOnClickListener {
            currentLocationType = "dropoff"
            locationLauncher.launch(Intent(this, LocationSelectionActivity::class.java).putExtra("LOCATION_TYPE", "dropoff"))
        }

        findViewById<ImageView>(R.id.hamburgerIcon).setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        findViewById<ImageView>(R.id.bellIcon).setOnClickListener { startActivity(Intent(this, CustomerOrdersActivity::class.java)) }

        loadFaresFromFirestore()
        checkRecentOrder()
    }

    // ── Vehicle setup ──────────────────────────────────────────────────────────

    private fun loadFaresFromFirestore() {
        firestore.collection("fareconfig")
            .whereEqualTo("Fare_IsActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) { setupDefaultVehicles(); return@addOnSuccessListener }
                val fares = documents.sortedBy { it.getDouble("Fare_BaseFare") ?: 0.0 }
                fares.forEachIndexed { i, doc ->
                    if (i < vehicleViewIds.size) {
                        val typeId = doc.getString("Fare_VhclType") ?: ""
                        val label = doc.getString("Fare_Label") ?: ""
                        val base = doc.getDouble("Fare_BaseFare") ?: 0.0
                        val perKm = doc.getDouble("Fare_PerKm") ?: 0.0
                        val icon = vehicleIcons[typeId] ?: R.drawable.ic_sedan
                        setupVehicle(vehicleViewIds[i], label, "Base: ₱%.0f + ₱%.0f/km".format(base, perKm), base, perKm, icon, typeId)
                    }
                }
            }
            .addOnFailureListener {
                Log.w("VehicleSelection", "Firestore fareconfig unavailable, using defaults")
                setupDefaultVehicles()
            }
    }

    private fun setupDefaultVehicles() {
        setupVehicle(R.id.itemMotorcycle, "Motorcycle / Bike",     "Base: ₱40 + ₱15/km",  40.0,  15.0, R.drawable.ic_vehicle_motorcycle, "motorcycle")
        setupVehicle(R.id.itemSedan,      "200 kg Sedan",          "Base: ₱60 + ₱22/km",  60.0,  22.0, R.drawable.ic_vehicle_sedan,      "sedan")
        setupVehicle(R.id.itemSubcompact, "300 kg Subcompact SUV", "Base: ₱70 + ₱26/km",  70.0,  26.0, R.drawable.ic_vehicle_suv_small,  "suv_small")
        setupVehicle(R.id.itemSUV,        "600 kg 7-seater SUV",   "Base: ₱85 + ₱30/km",  85.0,  30.0, R.drawable.ic_vehicle_suv_large,  "suv_large")
        setupVehicle(R.id.itemPickup,     "1000 kg Closed Van",    "Base: ₱110 + ₱38/km", 110.0, 38.0, R.drawable.ic_vehicle_van,        "van")
        setupVehicle(R.id.itemCargoVan,   "2000 kg Large Truck",   "Base: ₱160 + ₱52/km", 160.0, 52.0, R.drawable.ic_vehicle_truck,      "truck")
    }

    private fun setupVehicle(id: Int, title: String, sub: String, price: Double, perKm: Double, iconRes: Int, typeId: String) {
        val root = findViewById<View>(id)
        root.findViewById<TextView>(R.id.vehicleTitle).text = title
        root.findViewById<TextView>(R.id.vehicleSub).text = sub
        root.findViewById<ImageView>(R.id.vehicleIcon).setImageResource(iconRes)
        val card = root.findViewById<MaterialCardView>(R.id.vehicleCard)
        val check = root.findViewById<ImageView>(R.id.vehicleCheck)
        val expansion = root.findViewById<LinearLayout>(R.id.vehicleExpansion)

        card.setOnClickListener {
            // Deselect previous card
            selectedVehicleCard?.let {
                it.strokeColor = ContextCompat.getColor(this, R.color.gray_light)
                it.strokeWidth = dpToPx(1)
                it.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
            }
            selectedCheck?.visibility = View.GONE

            // Hide previous expansion
            currentExpansion?.visibility = View.GONE

            // Select this card
            card.strokeColor = ContextCompat.getColor(this, R.color.orange_main)
            card.strokeWidth = dpToPx(2)
            check.visibility = View.VISIBLE
            selectedVehicleCard = card
            selectedCheck = check

            basePrice = price
            perKmPrice = perKm
            selectedVehicleType = typeId
            currentExpansion = expansion

            onConditionsChanged()
        }
    }

    // ── Condition check & expansion/sheet control ──────────────────────────────

    /** Called whenever vehicle selection or location changes. */
    private fun onConditionsChanged() {
        val ready = selectedVehicleType.isNotEmpty() && pickupLat != null && dropoffLat != null
        val expansion = currentExpansion

        if (ready && expansion != null) {
            syncExpansionState(expansion)
            if (expansion.visibility == View.GONE) {
                expansion.visibility = View.VISIBLE
                expansion.alpha = 0f
                expansion.animate().alpha(1f).setDuration(250).start()
            }
            updateSheetPrices()
            if (priorityBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                priorityBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        } else if (!ready) {
            expansion?.visibility = View.GONE
            priorityBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    /** Applies current toggle state to the service cards of the given expansion view. */
    private fun syncExpansionState(expansion: LinearLayout) {
        val svcQueue = expansion.findViewById<MaterialCardView>(R.id.svcQueue)
        val svcFace  = expansion.findViewById<MaterialCardView>(R.id.svcFaceProfile)
        val svcTherm = expansion.findViewById<MaterialCardView>(R.id.svcThermal)

        styleService(svcQueue, isQueueSelected)
        styleService(svcFace,  isFaceProfileSelected)
        styleService(svcTherm, isThermalSelected)

        svcQueue.setOnClickListener {
            isQueueSelected = !isQueueSelected
            serviceQueuePrice = if (isQueueSelected) 70.0 else 0.0
            if (isQueueSelected) selectedServices.add("Extra waiting time")
            else selectedServices.remove("Extra waiting time")
            styleService(svcQueue, isQueueSelected)
            updateSheetPrices()
        }
        svcFace.setOnClickListener {
            isFaceProfileSelected = !isFaceProfileSelected
            if (isFaceProfileSelected) selectedServices.add("Require driver to show Face and Driver app Profile")
            else selectedServices.remove("Require driver to show Face and Driver app Profile")
            styleService(svcFace, isFaceProfileSelected)
        }
        svcTherm.setOnClickListener {
            isThermalSelected = !isThermalSelected
            if (isThermalSelected) selectedServices.add("Thermal bag")
            else selectedServices.remove("Thermal bag")
            styleService(svcTherm, isThermalSelected)
        }
    }

    private fun styleService(card: MaterialCardView, selected: Boolean) {
        if (selected) {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.orange_light))
            card.strokeColor = ContextCompat.getColor(this, R.color.orange_main)
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
            card.strokeColor = ContextCompat.getColor(this, R.color.gray_light)
        }
    }

    // ── Priority sheet ─────────────────────────────────────────────────────────

    private fun baseTotal() = basePrice + distanceKm * perKmPrice + serviceQueuePrice

    private fun priceForTier(tier: String): Double {
        val base = baseTotal()
        return when (tier) {
            "priority" -> (base * 1.35).roundToInt().toDouble()
            "pooling"  -> (base * 0.87).roundToInt().toDouble()
            else       -> base.roundToInt().toDouble()
        }
    }

    private fun updateSheetPrices() {
        findViewById<TextView>(R.id.tvPriorityPrice).text = "₱%.0f".format(priceForTier("priority"))
        findViewById<TextView>(R.id.tvRegularPrice).text  = "₱%.0f".format(priceForTier("regular"))
        findViewById<TextView>(R.id.tvPoolingPrice).text  = "₱%.0f".format(priceForTier("pooling"))
        if (distanceKm > 0) {
            val tvDist = findViewById<TextView>(R.id.tvSheetDistance)
            tvDist.text = "%.1f km".format(distanceKm)
            tvDist.visibility = View.VISIBLE
        }
    }

    private fun applyTier(tier: String) {
        selectedTier = tier
        val orange      = ContextCompat.getColor(this, R.color.orange_main)
        val orangeLight = ContextCompat.getColor(this, R.color.orange_light)
        val gray        = ContextCompat.getColor(this, R.color.gray_light)
        val white       = ContextCompat.getColor(this, R.color.white)
        val black       = ContextCompat.getColor(this, android.R.color.black)

        fun style(cardId: Int, priceId: Int, active: Boolean) {
            val card = findViewById<MaterialCardView>(cardId)
            card.strokeColor = if (active) orange else gray
            card.strokeWidth = if (active) dpToPx(2) else dpToPx(1)
            card.setCardBackgroundColor(if (active) orangeLight else white)
            findViewById<TextView>(priceId).setTextColor(if (active) orange else black)
        }

        style(R.id.cardPriority, R.id.tvPriorityPrice, tier == "priority")
        style(R.id.cardRegular,  R.id.tvRegularPrice,  tier == "regular")
        style(R.id.cardPooling,  R.id.tvPoolingPrice,  tier == "pooling")
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun calculateDistance() {
        val pLat = pickupLat ?: return
        val pLng = pickupLng ?: return
        val dLat = dropoffLat ?: return
        val dLng = dropoffLng ?: return
        val R = 6371.0
        val dLatR = Math.toRadians(dLat - pLat)
        val dLngR = Math.toRadians(dLng - pLng)
        val a = Math.sin(dLatR / 2).let { it * it } +
                Math.cos(Math.toRadians(pLat)) * Math.cos(Math.toRadians(dLat)) *
                Math.sin(dLngR / 2).let { it * it }
        distanceKm = R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    private fun resetSelectionState() {
        selectedVehicleCard?.let {
            it.strokeColor = ContextCompat.getColor(this, R.color.gray_light)
            it.strokeWidth = dpToPx(1)
            it.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white))
        }
        selectedCheck?.visibility = View.GONE
        currentExpansion?.visibility = View.GONE

        selectedVehicleCard = null
        selectedCheck = null
        currentExpansion = null
        selectedVehicleType = ""
        pickupLat = null
        pickupLng = null
        dropoffLat = null
        dropoffLng = null

        findViewById<TextView>(R.id.pickupLocationText).text = "Choose Pickup Location"
        findViewById<TextView>(R.id.dropoffLocationText).text = "Choose Dropoff Location"

        priorityBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    override fun onResume() {
        super.onResume()
        checkRecentOrder()
        // Re-show sheet if conditions are still met (e.g. returning from OrderDetails)
        if (selectedVehicleType.isNotEmpty() && pickupLat != null && dropoffLat != null) {
            updateSheetPrices()
            if (priorityBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                priorityBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun checkRecentOrder() {
        val userIdLong = SessionManager.getUserId(this).takeIf { it != 0L } ?: return
        val userId = userIdLong.toString()
        val banner     = findViewById<MaterialCardView>(R.id.recentOrderBanner)
        val statusText = findViewById<TextView>(R.id.recentOrderStatus)
        val orderText  = findViewById<TextView>(R.id.recentOrderText)
        val activeStatuses = listOf("pending", "driver_assigned", "accepted", "driver_en_route", "picked_up", "delivered", "completed")

        fun showBanner(doc: com.google.firebase.firestore.DocumentSnapshot) {
            val status  = doc.getString("Book_Status") ?: "pending"
            val pickup  = doc.getString("Book_Pickuploc") ?: ""
            val dropoff = doc.getString("Book_Dropoffloc") ?: ""
            val fare    = (doc.get("Book_TotalFare") as? Number)?.toDouble() ?: 0.0
            val (label, color) = when (status) {
                "pending"                                         -> "PENDING"        to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                "driver_assigned", "accepted", "driver_en_route" -> "DRIVER ASSIGNED" to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                "picked_up"                                       -> "IN TRANSIT"     to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                else                                              -> "DELIVERED"      to ContextCompat.getColor(this, android.R.color.holo_green_dark)
            }
            statusText.text = label; statusText.setTextColor(color)
            orderText.text = "$pickup → $dropoff"
            banner.visibility = View.VISIBLE
            banner.setOnClickListener {
                startActivity(Intent(this, CustomerOrderDetailActivity::class.java).apply {
                    putExtra("ORDER_ID",  doc.id)
                    putExtra("PICKUP",    pickup)
                    putExtra("DROPOFF",   dropoff)
                    putExtra("FARE",      "₱${String.format("%.2f", fare)}")
                    putExtra("VEHICLE",   doc.getString("Book_VhclTypeID") ?: "motorcycle")
                    putExtra("PAYMENT",   doc.getString("Book_PaymentMethod") ?: "Cash")
                    putExtra("NOTES",     doc.getString("Book_Notes") ?: "")
                    putExtra("STATUS",    status)
                    putExtra("IS_RATED",  doc.get("Book_IsRated") as? Boolean ?: false)
                    putExtra("DRIVER_ID", doc.get("Book_DrvrID")?.toString() ?: "")
                })
            }
        }

        fun processDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>): Boolean {
            val active = docs.firstOrNull { doc ->
                val s = doc.getString("Book_Status") ?: "pending"
                val rated = doc.get("Book_IsRated") as? Boolean ?: false
                !((s == "delivered" || s == "completed") && rated)
            }
            if (active != null) { showBanner(active); return true }
            return false
        }

        firestore.collection("booking").whereEqualTo("Book_CustID", userId).whereIn("Book_Status", activeStatuses).get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty && processDocs(docs.documents)) return@addOnSuccessListener
                firestore.collection("booking").whereEqualTo("Book_CustID", userIdLong).whereIn("Book_Status", activeStatuses).get()
                    .addOnSuccessListener { docs2 ->
                        if (docs2.isEmpty || !processDocs(docs2.documents)) banner.visibility = View.GONE
                    }
            }
    }
}
