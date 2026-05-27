package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
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
    private var isCclexSelected = false
    private val selectedServices = mutableListOf("Require driver to show Face and Driver app Profile")
    private val CCLEX_PRICE = 68.0

    // Priority tier
    private var selectedTier = "regular"

    // Location
    private var pickupLat: Double? = null
    private var pickupLng: Double? = null
    private var dropoffLat: Double? = null
    private var dropoffLng: Double? = null

    // Multi-stop data
    data class StopData(var text: String = "", var lat: Double? = null, var lng: Double? = null)
    private val extraStops = mutableListOf<StopData>()
    private var currentStopIndex = -1   // -1 = not picking a stop; else = index in extraStops

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
            when {
                currentLocationType == "pickup" -> {
                    findViewById<TextView>(R.id.pickupLocationText).text = locationName
                    pickupLat = lat; pickupLng = lng
                }
                currentLocationType == "dropoff" -> {
                    findViewById<TextView>(R.id.dropoffLocationText).text = locationName
                    dropoffLat = lat; dropoffLng = lng
                }
                currentLocationType == "stop" && currentStopIndex >= 0 -> {
                    extraStops[currentStopIndex].text = locationName
                    extraStops[currentStopIndex].lat  = lat
                    extraStops[currentStopIndex].lng  = lng
                    refreshStopViews()
                }
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
            val stopsJson = org.json.JSONArray().apply {
                extraStops.forEach { s -> put(org.json.JSONObject().apply {
                    put("text", s.text)
                    put("lat",  s.lat ?: 0.0)
                    put("lng",  s.lng ?: 0.0)
                }) }
            }.toString()
            startActivity(Intent(this, OrderDetailsActivity::class.java).apply {
                putExtra("TOTAL_PRICE",  "₱%.2f".format(finalPrice))
                putExtra("VEHICLE_TYPE", selectedVehicleType)
                putExtra("ADD_SERVICES", selectedServices.toSet().joinToString(", "))
                putExtra("PICKUP",       findViewById<TextView>(R.id.pickupLocationText).text.toString())
                putExtra("DROPOFF",      findViewById<TextView>(R.id.dropoffLocationText).text.toString())
                putExtra("DISTANCE_KM",  distanceKm)
                putExtra("STOPS_JSON",   stopsJson)
                putExtra("PICKUP_LAT",   pickupLat ?: 0.0)
                putExtra("PICKUP_LNG",   pickupLng ?: 0.0)
                putExtra("DROPOFF_LAT",  dropoffLat ?: 0.0)
                putExtra("DROPOFF_LNG",  dropoffLng ?: 0.0)
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
                R.id.nav_wallet        -> startActivity(Intent(this, CustomerWalletActivity::class.java))
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

        // Saved address shortcut buttons
        findViewById<ImageView>(R.id.btnPickupSaved).setOnClickListener {
            currentLocationType = "pickup"
            showSavedAddressesSheet()
        }
        findViewById<ImageView>(R.id.btnDropoffSaved).setOnClickListener {
            currentLocationType = "dropoff"
            showSavedAddressesSheet()
        }

        // Add Stop button
        findViewById<LinearLayout>(R.id.btnAddStop).setOnClickListener {
            if (extraStops.size >= 18) {   // max 20 stops total (1 pickup + 18 extra + 1 dropoff)
                Toast.makeText(this, "Maximum 20 stops reached.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            extraStops.add(StopData("Choose Stop ${extraStops.size + 1}"))
            refreshStopViews()
            // Immediately launch picker for this new stop
            currentLocationType = "stop"
            currentStopIndex = extraStops.size - 1
            locationLauncher.launch(
                Intent(this, LocationSelectionActivity::class.java)
                    .putExtra("LOCATION_TYPE", "stop")
            )
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
        val svcCclex = expansion.findViewById<MaterialCardView>(R.id.svcCclex)

        styleService(svcQueue, isQueueSelected)
        styleService(svcFace,  isFaceProfileSelected)
        styleService(svcTherm, isThermalSelected)
        styleService(svcCclex, isCclexSelected)

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
        svcCclex.setOnClickListener {
            isCclexSelected = !isCclexSelected
            if (isCclexSelected) selectedServices.add("Pass Thrue CCLEX (+₱68)")
            else selectedServices.remove("Pass Thrue CCLEX (+₱68)")
            styleService(svcCclex, isCclexSelected)
            updateSheetPrices()
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

    private fun baseTotal() = basePrice + distanceKm * perKmPrice + serviceQueuePrice + (if (isCclexSelected) CCLEX_PRICE else 0.0)

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

    // ── Saved Addresses Sheet ──────────────────────────────────────────────────

    private fun showSavedAddressesSheet() {
        val acctId = SessionManager.getAcctId(this)
        val dialog = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.sheet_saved_addresses, null)
        dialog.setContentView(sheetView)

        val titleText  = sheetView.findViewById<TextView>(R.id.sheetTitle)
        val recycler   = sheetView.findViewById<RecyclerView>(R.id.sheetRecycler)
        val emptyText  = sheetView.findViewById<TextView>(R.id.sheetEmptyText)
        val loadingText = sheetView.findViewById<TextView>(R.id.sheetLoading)

        titleText.text = if (currentLocationType == "pickup") "Pick-up Location" else "Drop-off Location"

        recycler.layoutManager = LinearLayoutManager(this)

        ApiClient.getAddresses(acctId) { arr ->
            loadingText.visibility = View.GONE
            if (arr == null || arr.length() == 0) {
                emptyText.visibility = View.VISIBLE
                return@getAddresses
            }
            emptyText.visibility = View.GONE

            val list = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))

            class AddrVH(v: View) : RecyclerView.ViewHolder(v)

            recycler.adapter = object : RecyclerView.Adapter<AddrVH>() {

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddrVH =
                    AddrVH(LayoutInflater.from(parent.context).inflate(R.layout.item_saved_address_pick, parent, false))

                override fun getItemCount() = list.size

                override fun onBindViewHolder(holder: AddrVH, position: Int) {
                    val addr    = list[position]
                    val label   = addr.optString("Addr_Label", "Address")
                    val address = addr.optString("Addr_Address", "")
                    val type    = addr.optString("Addr_Type", "other")
                    val icon    = when (type) { "home" -> "🏠"; "work" -> "💼"; else -> "📍" }
                    holder.itemView.findViewById<TextView>(R.id.pickAddrIcon).text    = icon
                    holder.itemView.findViewById<TextView>(R.id.pickAddrLabel).text   = label
                    holder.itemView.findViewById<TextView>(R.id.pickAddrAddress).text = address

                    holder.itemView.setOnClickListener {
                        dialog.dismiss()
                        // Show loading toast then geocode
                        Toast.makeText(this@VehicleSelectionActivity, "Finding \"$label\"…", Toast.LENGTH_SHORT).show()
                        ApiClient.geocodeAddress(address) { lat, lng ->
                            if (lat != null && lng != null) {
                                val locType = currentLocationType
                                if (locType == "pickup") {
                                    findViewById<TextView>(R.id.pickupLocationText).text = "$label – $address"
                                    pickupLat = lat; pickupLng = lng
                                } else {
                                    findViewById<TextView>(R.id.dropoffLocationText).text = "$label – $address"
                                    dropoffLat = lat; dropoffLng = lng
                                }
                                calculateDistance()
                                onConditionsChanged()
                            } else {
                                // Could not geocode — still set the text but warn
                                val locType = currentLocationType
                                if (locType == "pickup") {
                                    findViewById<TextView>(R.id.pickupLocationText).text = address
                                } else {
                                    findViewById<TextView>(R.id.dropoffLocationText).text = address
                                }
                                Toast.makeText(this@VehicleSelectionActivity,
                                    "Address set. Please verify on map.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    // ── Multi-stop helpers ─────────────────────────────────────────────────────

    /** Rebuild the dynamic stop rows inside stopsContainer */
    private fun refreshStopViews() {
        val container = findViewById<LinearLayout>(R.id.stopsContainer)
        container.removeAllViews()
        extraStops.forEachIndexed { idx, stop ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_stop_row, container, false)
            row.findViewById<TextView>(R.id.stopText).text =
                if (stop.text.isNotEmpty() && !stop.text.startsWith("Choose")) stop.text
                else "Stop ${idx + 1} — Tap to choose"
            row.findViewById<TextView>(R.id.stopIndex).text = "${idx + 1}"
            row.setOnClickListener {
                currentLocationType = "stop"
                currentStopIndex = idx
                locationLauncher.launch(
                    Intent(this, LocationSelectionActivity::class.java)
                        .putExtra("LOCATION_TYPE", "stop")
                )
            }
            row.findViewById<ImageView>(R.id.btnRemoveStop).setOnClickListener {
                extraStops.removeAt(idx)
                refreshStopViews()
                calculateDistance()
                onConditionsChanged()
            }
            container.addView(row)
            // Add a divider
            val div = View(this)
            div.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1))
            div.setBackgroundColor(0xFFF0F0F0.toInt())
            container.addView(div)
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).let { it * it }
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun calculateDistance() {
        val pLat = pickupLat ?: return
        val pLng = pickupLng ?: return
        val dLat = dropoffLat ?: return
        val dLng = dropoffLng ?: return

        // Build ordered waypoints: pickup → stops → dropoff
        val waypoints = mutableListOf(Pair(pLat, pLng))
        extraStops.forEach { stop ->
            val sLat = stop.lat ?: return@forEach
            val sLng = stop.lng ?: return@forEach
            waypoints.add(Pair(sLat, sLng))
        }
        waypoints.add(Pair(dLat, dLng))

        // Sum distances between consecutive waypoints
        distanceKm = 0.0
        for (i in 0 until waypoints.size - 1) {
            val (lat1, lng1) = waypoints[i]
            val (lat2, lng2) = waypoints[i + 1]
            distanceKm += haversine(lat1, lng1, lat2, lng2)
        }
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
        extraStops.clear()
        currentStopIndex = -1
        findViewById<LinearLayout>(R.id.stopsContainer).removeAllViews()

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

    private fun getHiddenOrders(): Set<String> =
        getSharedPreferences("ll_hidden", android.content.Context.MODE_PRIVATE)
            .getStringSet("hidden_orders", emptySet()) ?: emptySet()

    private fun checkRecentOrder() {
        val userId = SessionManager.getUserId(this).takeIf { it.isNotEmpty() } ?: return
        val banner     = findViewById<MaterialCardView>(R.id.recentOrderBanner)
        val statusText = findViewById<TextView>(R.id.recentOrderStatus)
        val orderText  = findViewById<TextView>(R.id.recentOrderText)
        val activeStatuses = listOf("pending", "driver_assigned", "accepted", "driver_en_route", "picked_up", "delivered", "completed", "cancelled")
        val hidden = getHiddenOrders()

        fun showBanner(doc: com.google.firebase.firestore.DocumentSnapshot) {
            val status  = doc.getString("Book_Status") ?: "pending"
            val pickup  = doc.getString("Book_Pickuploc") ?: ""
            val dropoff = doc.getString("Book_Dropoffloc") ?: ""
            val fare    = (doc.get("Book_TotalFare") as? Number)?.toDouble() ?: 0.0
            val (label, color) = when (status) {
                "pending"                                         -> "PENDING"        to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                "driver_assigned", "accepted", "driver_en_route" -> "DRIVER ASSIGNED" to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                "picked_up"                                       -> "IN TRANSIT"     to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
                "cancelled"                                       -> "CANCELLED"      to ContextCompat.getColor(this, android.R.color.holo_red_dark)
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
                if (doc.id in hidden) return@firstOrNull false
                val s = doc.getString("Book_Status") ?: "pending"
                val rated = doc.get("Book_IsRated") as? Boolean ?: false
                !((s == "delivered" || s == "completed") && rated)
            }
            if (active != null) { showBanner(active); return true }
            return false
        }

        firestore.collection("booking").whereEqualTo("Book_CustID", userId).whereIn("Book_Status", activeStatuses).get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty || !processDocs(docs.documents)) banner.visibility = View.GONE
            }
    }
}
