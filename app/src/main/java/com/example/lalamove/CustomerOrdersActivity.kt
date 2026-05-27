package com.example.lalamove

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CustomerOrdersActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var ordersListener: ListenerRegistration? = null
    private val ordersList      = mutableListOf<Map<String, Any?>>()
    private val orderIds        = mutableListOf<String>()
    private val driversMap      = mutableMapOf<String, Map<String, Any?>>()
    private val favouriteDriverIds = mutableSetOf<String>()
    private lateinit var adapter: MyOrdersAdapter

    private val acctId get() = SessionManager.getAcctId(this)

    private fun getHiddenOrders(): Set<String> =
        getSharedPreferences("ll_hidden", Context.MODE_PRIVATE)
            .getStringSet("hidden_orders", emptySet()) ?: emptySet()

    private fun hideOrder(id: String) {
        val prefs = getSharedPreferences("ll_hidden", Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet("hidden_orders", emptySet()) ?: emptySet()).toMutableSet()
        updated.add(id)
        prefs.edit().putStringSet("hidden_orders", updated).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_orders)

        firestore = FirebaseFirestore.getInstance()

        val recycler     = findViewById<RecyclerView>(R.id.recyclerMyOrders)
        val textNoOrders = findViewById<TextView>(R.id.textNoMyOrders)

        findViewById<ImageView>(R.id.btnBackOrders).setOnClickListener { finish() }

        adapter = MyOrdersAdapter(
            orders             = ordersList,
            drivers            = driversMap,
            orderIds           = orderIds,
            favouriteDriverIds = favouriteDriverIds,
            onItemClick        = { pos -> navigateToDetail(pos) },
            onConfirmDelivery  = { pos -> showConfirmDeliveryDialog(pos) },
            onRateDriver       = { pos -> showRateDriverDialog(pos) },
            onReorder          = { pos -> reorder(pos) },
            onReportDriver     = { pos -> showReportDialog(pos) },
            onFavDriver        = { pos -> toggleFavDriver(pos) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val swipePaint = Paint().apply { color = Color.parseColor("#E53935") }
        val trashIcon  = ContextCompat.getDrawable(this, R.drawable.ic_remove)!!
        val iconSize   = (24 * resources.displayMetrics.density).toInt()
        val iconMargin = (24 * resources.displayMetrics.density).toInt()

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos == RecyclerView.NO_ID.toInt() || pos >= orderIds.size) return
                val id = orderIds[pos]
                // Snap the card back immediately; only remove if the user confirms
                adapter.notifyItemChanged(pos)
                MaterialAlertDialogBuilder(this@CustomerOrdersActivity)
                    .setTitle("Hide Order")
                    .setMessage("Remove this order from your history? It stays saved and won't be permanently deleted.")
                    .setPositiveButton("Remove") { _, _ ->
                        val orderIndex = orderIds.indexOf(id)
                        val currentStatus = if (orderIndex >= 0) {
                            ordersList[orderIndex]["Book_Status"]?.toString() ?: "pending"
                        } else {
                            "pending"
                        }
                        if (currentStatus in listOf("pending", "driver_assigned", "accepted", "driver_en_route", "picked_up")) {
                            val custId = SessionManager.getUserId(this@CustomerOrdersActivity)
                            ApiClient.cancelOrder(id, custId) { ok, _ ->
                                if (!ok) {
                                    firestore.collection("booking").document(id)
                                        .update("Book_Status", "cancelled")
                                }
                            }
                        }
                        hideOrder(id)
                        val removePos = orderIds.indexOf(id)
                        if (removePos >= 0) {
                            ordersList.removeAt(removePos)
                            orderIds.removeAt(removePos)
                            adapter.notifyItemRemoved(removePos)
                            val tv  = findViewById<TextView>(R.id.textNoMyOrders)
                            val rv2 = findViewById<RecyclerView>(R.id.recyclerMyOrders)
                            if (ordersList.isEmpty()) { tv.visibility = View.VISIBLE; rv2.visibility = View.GONE }
                        }
                        Toast.makeText(this@CustomerOrdersActivity, "Order hidden", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                                     dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
                val item = vh.itemView
                if (dX < 0) {
                    // Red background
                    c.drawRoundRect(RectF(item.right + dX, item.top.toFloat(),
                        item.right.toFloat(), item.bottom.toFloat()), 12f, 12f, swipePaint)
                    // Trash icon centered vertically, pinned to the right
                    val iconTop  = item.top + (item.height - iconSize) / 2
                    val iconLeft = item.right - iconMargin - iconSize
                    trashIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    trashIcon.setTint(Color.WHITE)
                    trashIcon.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }).attachToRecyclerView(recycler)
    }

    override fun onStart() {
        super.onStart()
        val userId       = SessionManager.getUserId(this)
        val recycler     = findViewById<RecyclerView>(R.id.recyclerMyOrders)
        val textNoOrders = findViewById<TextView>(R.id.textNoMyOrders)

        if (userId.isEmpty()) {
            textNoOrders.visibility = View.VISIBLE
            recycler.visibility     = View.GONE
            return
        }

        // Load customer's favourite driver IDs so stars render correctly
        firestore.collection("customer").document(userId).get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val favList = doc.get("Cust_FavouriteDrivers") as? List<*>
                favouriteDriverIds.clear()
                favList?.mapNotNull { it?.toString() }?.let { favouriteDriverIds.addAll(it) }
                adapter.notifyDataSetChanged()
            }

        ordersListener = firestore.collection("booking")
            .whereEqualTo("Book_CustID", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) { Toast.makeText(this, "Sync error: ${error.message}", Toast.LENGTH_SHORT).show(); return@addSnapshotListener }
                val hidden = getHiddenOrders()
                val allDocs = snap?.documents?.filter { it.id !in hidden } ?: emptyList()
                val sorted  = allDocs.sortedByDescending { doc ->
                    (doc.data?.get("Book_CreatedAt") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                }
                ordersList.clear()
                orderIds.clear()
                val driverIdsToFetch = mutableSetOf<String>()
                sorted.forEach { doc ->
                    doc.data?.let { data ->
                        ordersList.add(data)
                        orderIds.add(doc.id)
                        val dId = data["Book_DrvrID"]?.toString()
                        if (!dId.isNullOrEmpty() && dId != "null" && !driversMap.containsKey(dId)) {
                            driverIdsToFetch.add(dId)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                driverIdsToFetch.forEach { dId ->
                    firestore.collection("driver").document(dId).get().addOnSuccessListener { dDoc ->
                        if (dDoc.exists() && dDoc.data != null) {
                            val driverData = dDoc.data!!.toMutableMap()
                            driversMap[dId] = driverData
                            adapter.notifyDataSetChanged()
                            val vehicleId = dDoc.get("Drvr_VehicleID")?.toString()
                            if (!vehicleId.isNullOrEmpty() && vehicleId != "null") {
                                firestore.collection("vehicle").document(vehicleId).get()
                                    .addOnSuccessListener { vDoc ->
                                        val plate = vDoc.getString("Vhcl_PlateNumber")
                                        if (!plate.isNullOrEmpty()) {
                                            driverData["_VhclPlateNumber"] = plate
                                            adapter.notifyDataSetChanged()
                                        }
                                    }
                            }
                        }
                    }
                }
                if (ordersList.isEmpty()) {
                    textNoOrders.visibility = View.VISIBLE
                    recycler.visibility     = View.GONE
                } else {
                    textNoOrders.visibility = View.GONE
                    recycler.visibility     = View.VISIBLE
                }
            }
    }

    override fun onStop() {
        super.onStop()
        ordersListener?.remove()
        ordersListener = null
    }

    private fun navigateToDetail(position: Int) {
        val order = ordersList[position]
        val id    = orderIds[position]
        val fareVal = (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
        startActivity(Intent(this, CustomerOrderDetailActivity::class.java).apply {
            putExtra("ORDER_ID",      id)
            putExtra("MYSQL_ORDER_ID", order["Book_ID"]?.toString() ?: id)
            putExtra("PICKUP",   order["Book_Pickuploc"]?.toString() ?: "")
            putExtra("DROPOFF",  order["Book_Dropoffloc"]?.toString() ?: "")
            putExtra("FARE",     "₱${String.format("%.2f", fareVal)}")
            putExtra("VEHICLE",  order["Book_VhclTypeID"]?.toString() ?: "motorcycle")
            putExtra("PAYMENT",  order["Book_PaymentMethod"]?.toString() ?: "Cash")
            putExtra("NOTES",    order["Book_Notes"]?.toString() ?: "")
            putExtra("STATUS",   order["Book_Status"]?.toString() ?: "pending")
            putExtra("IS_RATED", order["Book_IsRated"] as? Boolean ?: false)
            putExtra("DRIVER_ID", order["Book_DrvrID"]?.toString() ?: "")
        })
    }

    private fun reorder(position: Int) {
        val order = ordersList[position]
        startActivity(Intent(this, VehicleSelectionActivity::class.java).apply {
            putExtra("PREFILL_PICKUP",  order["Book_Pickuploc"]?.toString() ?: "")
            putExtra("PREFILL_DROPOFF", order["Book_Dropoffloc"]?.toString() ?: "")
        })
    }

    private fun showReportDialog(position: Int) {
        val order    = ordersList[position]
        val driverId = order["Book_DrvrID"]?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
        if (driverId.isNullOrEmpty()) {
            Toast.makeText(this, "No driver to report.", Toast.LENGTH_SHORT).show()
            return
        }

        val categories    = listOf("driver_behavior", "overcharge", "non_delivery", "item_damage", "other")
        val categoryLabels = listOf("Driver Behavior", "Overcharge", "Non-Delivery", "Item Damage", "Other")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_driver, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerReportCategory)
        val inputDetails = dialogView.findViewById<EditText>(R.id.inputReportDetails)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryLabels)

        AlertDialog.Builder(this)
            .setTitle("Report Driver")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val catIdx   = spinner.selectedItemPosition
                val category = categories[catIdx]
                val subject  = categoryLabels[catIdx]
                val details  = inputDetails.text.toString().trim()
                ApiClient.reportDriver(acctId, driverId, category, subject, details) { ok, err ->
                    if (ok) Toast.makeText(this, "Report submitted.", Toast.LENGTH_SHORT).show()
                    else    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleFavDriver(position: Int) {
        val order    = ordersList[position]
        val driverId = order["Book_DrvrID"]?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
        if (driverId.isNullOrEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Driver Preference")
            .setItems(arrayOf("⭐ Favourite", "🚫 Block", "Remove preference")) { _, which ->
                val status = when (which) { 0 -> "favourited"; 1 -> "blocked"; else -> "none" }
                ApiClient.setDriverFavourite(acctId, driverId, status) { ok, _ ->
                    if (ok) {
                        val msg = when (status) {
                            "favourited" -> "Driver saved to Favourites ⭐"
                            "blocked"    -> "Driver blocked 🚫"
                            else         -> "Preference removed"
                        }
                        // Update local favourite set and refresh star icons
                        when (status) {
                            "favourited" -> favouriteDriverIds.add(driverId)
                            else         -> favouriteDriverIds.remove(driverId)
                        }
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showConfirmDeliveryDialog(position: Int) {
        val orderId = orderIds[position]
        AlertDialog.Builder(this)
            .setTitle("Confirm Delivery")
            .setMessage("Has your delivery been received successfully?")
            .setPositiveButton("Yes, Received") { _, _ ->
                firestore.collection("booking").document(orderId)
                    .update("Book_Status", "completed")
                    .addOnSuccessListener {
                        Toast.makeText(this, "Delivery confirmed!", Toast.LENGTH_SHORT).show()
                        showRateDriverDialog(position)
                    }
            }
            .setNegativeButton("Not Yet", null)
            .show()
    }

    private fun showRateDriverDialog(position: Int) {
        val orderId = orderIds[position]
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rate_driver, null)
        val stars = listOf(
            dialogView.findViewById<ImageView>(R.id.star1),
            dialogView.findViewById<ImageView>(R.id.star2),
            dialogView.findViewById<ImageView>(R.id.star3),
            dialogView.findViewById<ImageView>(R.id.star4),
            dialogView.findViewById<ImageView>(R.id.star5)
        )
        val inputFeedback  = dialogView.findViewById<EditText>(R.id.inputFeedback)
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
                firestore.collection("booking").document(orderId)
                    .update(mapOf(
                        "Book_Status"      to "completed",
                        "Book_IsRated"     to true,
                        "Book_RatingGiven" to selectedRating,
                        "Book_Feedback"    to inputFeedback.text.toString().trim()
                    ))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Thank you for your feedback! ⭐", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Skip", null)
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.remove()
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────
    class MyOrdersAdapter(
        private val orders: List<Map<String, Any?>>,
        private val drivers: Map<String, Map<String, Any?>>,
        private val orderIds: List<String>,
        private val favouriteDriverIds: Set<String>,
        private val onItemClick: (Int) -> Unit,
        private val onConfirmDelivery: (Int) -> Unit,
        private val onRateDriver: (Int) -> Unit,
        private val onReorder: (Int) -> Unit,
        private val onReportDriver: (Int) -> Unit,
        private val onFavDriver: (Int) -> Unit
    ) : RecyclerView.Adapter<MyOrdersAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val status: TextView         = view.findViewById(R.id.myOrderStatus)
            val fare: TextView           = view.findViewById(R.id.myOrderFare)
            val pickup: TextView         = view.findViewById(R.id.myOrderPickup)
            val dropoff: TextView        = view.findViewById(R.id.myOrderDropoff)
            val vehicle: TextView        = view.findViewById(R.id.myOrderVehicle)
            val btnAction: MaterialButton = view.findViewById(R.id.btnOrderAction)
            val driverSection: View      = view.findViewById(R.id.myOrderDriverSection)
            val driverText: TextView     = view.findViewById(R.id.myOrderDriverText)
            val secondaryRow: View       = view.findViewById(R.id.secondaryActionsRow)
            val btnReorder: MaterialButton = view.findViewById(R.id.btnReorder)
            val btnReport: MaterialButton  = view.findViewById(R.id.btnReportDriver)
            val btnFav: ImageView          = view.findViewById(R.id.btnFavDriver)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order      = orders[position]
            val fareVal    = (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
            val bookStatus = order["Book_Status"]?.toString() ?: "pending"
            val isRated    = order["Book_IsRated"] as? Boolean ?: false
            val driverId   = order["Book_DrvrID"]?.toString()

            holder.fare.text    = "₱${String.format("%.2f", fareVal)}"
            holder.pickup.text  = order["Book_Pickuploc"]?.toString() ?: ""
            holder.dropoff.text = order["Book_Dropoffloc"]?.toString() ?: ""
            holder.vehicle.text = (order["Book_VhclTypeID"]?.toString() ?: "").replaceFirstChar { it.uppercase() }

            holder.itemView.setOnClickListener { onItemClick(position) }

            // Driver section
            val driverData = drivers[driverId]
            if (driverData != null && bookStatus in listOf("driver_assigned", "accepted", "driver_en_route", "picked_up", "delivered", "completed")) {
                val fn    = driverData["Drvr_FirstName"]?.toString() ?: "Driver"
                val ln    = driverData["Drvr_LastName"]?.toString() ?: ""
                val plate = (driverData["_VhclPlateNumber"] ?: driverData["Drvr_LicenseNum"])
                    ?.toString()?.takeIf { it.isNotBlank() } ?: "—"
                val vType = driverData["Drvr_VhclTypeID"]?.toString()?.replaceFirstChar { it.uppercase() } ?: "Vehicle"
                val color = driverData["Drvr_MotorColor"]?.toString()?.replaceFirstChar { it.uppercase() } ?: ""
                val vehicleDesc = listOfNotNull(
                    color.takeIf { it.isNotEmpty() },
                    vType,
                    plate
                ).joinToString(", ")

                holder.driverSection.visibility = View.VISIBLE
                holder.driverText.text = if (bookStatus in listOf("driver_assigned", "accepted", "driver_en_route", "picked_up"))
                    "$fn $ln is on the way. Look for a $vehicleDesc."
                else
                    "Delivered by $fn $ln ($vehicleDesc)"
            } else {
                holder.driverSection.visibility = View.GONE
            }

            // Status badge
            when (bookStatus) {
                "pending" -> {
                    holder.status.text = "PENDING"
                    holder.status.setTextColor(Color.parseColor("#FF6B00"))
                    holder.status.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    holder.btnAction.visibility = View.GONE
                }
                "driver_assigned", "accepted", "driver_en_route" -> {
                    holder.status.text = "IN TRANSIT"
                    holder.status.setTextColor(Color.parseColor("#1976D2"))
                    holder.status.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    holder.btnAction.visibility = View.GONE
                }
                "delivered", "completed" -> {
                    if (!isRated) {
                        holder.status.text = "DELIVERED"
                        holder.status.setTextColor(Color.parseColor("#4CAF50"))
                        holder.status.setBackgroundColor(Color.parseColor("#E8F5E9"))
                        holder.btnAction.visibility = View.VISIBLE
                        holder.btnAction.text = "Rate Driver"
                        holder.btnAction.setTextColor(Color.parseColor("#FF6B00"))
                        holder.btnAction.setBackgroundColor(Color.parseColor("#FFF3E0"))
                        holder.btnAction.setOnClickListener { onRateDriver(position) }
                    } else {
                        holder.status.text = "DELIVERED ⭐"
                        holder.status.setTextColor(Color.parseColor("#4CAF50"))
                        holder.status.setBackgroundColor(Color.parseColor("#E8F5E9"))
                        holder.btnAction.visibility = View.GONE
                    }
                }
                "picked_up" -> {
                    holder.status.text = "PICKED UP"
                    holder.status.setTextColor(Color.parseColor("#0288D1"))
                    holder.status.setBackgroundColor(Color.parseColor("#E1F5FE"))
                    holder.btnAction.visibility = View.GONE
                }
                "cancelled" -> {
                    holder.status.text = "CANCELLED"
                    holder.status.setTextColor(Color.parseColor("#E53935"))
                    holder.status.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    holder.btnAction.visibility = View.GONE
                }
                else -> {
                    holder.status.text = bookStatus.uppercase()
                    holder.status.setTextColor(Color.parseColor("#888888"))
                    holder.status.setBackgroundColor(Color.parseColor("#F5F5F5"))
                    holder.btnAction.visibility = View.GONE
                }
            }

            // Secondary actions
            val showSecondary = bookStatus in listOf("delivered", "completed", "cancelled")
            val hasDriver     = !driverId.isNullOrEmpty() && driverId != "null"
            holder.secondaryRow.visibility = if (showSecondary) View.VISIBLE else View.GONE

            if (showSecondary) {
                holder.btnReorder.setOnClickListener { onReorder(position) }

                if (bookStatus in listOf("delivered", "completed") && hasDriver) {
                    holder.btnReport.visibility = View.VISIBLE
                    holder.btnFav.visibility    = View.VISIBLE
                    holder.btnReport.setOnClickListener { onReportDriver(position) }
                    holder.btnFav.setOnClickListener    { onFavDriver(position) }

                    // Tint star yellow if this driver is already favourited
                    val isFav = !driverId.isNullOrEmpty() && favouriteDriverIds.contains(driverId)
                    holder.btnFav.setColorFilter(
                        if (isFav) android.graphics.Color.parseColor("#FFC107")
                        else       android.graphics.Color.parseColor("#CCCCCC")
                    )
                } else {
                    holder.btnReport.visibility = View.GONE
                    holder.btnFav.visibility    = View.GONE
                }
            }
        }

        override fun getItemCount() = orders.size
    }
}
