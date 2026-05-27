package com.example.lalamove

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MyDriversActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var emptyIcon: TextView
    private lateinit var emptyText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tabFav: TextView
    private lateinit var tabBlocked: TextView

    private val driverList = mutableListOf<DriverItem>()
    private lateinit var adapter: DriverAdapter
    private var currentFilter = "favourited"

    private val acctId get() = SessionManager.getAcctId(this)

    data class DriverItem(
        val uid: String,
        val name: String,
        val vehicle: String,
        val rating: Float,
        val status: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_drivers)

        recycler    = findViewById(R.id.recyclerDrivers)
        emptyView   = findViewById(R.id.emptyDrivers)
        emptyIcon   = findViewById(R.id.emptyDriversIcon)
        emptyText   = findViewById(R.id.emptyDriversText)
        progress    = findViewById(R.id.driversProgress)
        tabFav      = findViewById(R.id.tabFavourites)
        tabBlocked  = findViewById(R.id.tabBlocked)

        findViewById<ImageView>(R.id.btnBackDrivers).setOnClickListener { finish() }

        adapter = DriverAdapter(driverList) { item -> confirmRemove(item) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        tabFav.setOnClickListener { switchTab("favourited") }
        tabBlocked.setOnClickListener { switchTab("blocked") }

        loadDrivers("favourited")
    }

    private fun switchTab(filter: String) {
        currentFilter = filter
        if (filter == "favourited") {
            tabFav.setTextColor(Color.parseColor("#FF6B00"))
            tabFav.setBackgroundResource(R.drawable.tab_selected)
            tabBlocked.setTextColor(Color.parseColor("#888888"))
            tabBlocked.setBackgroundResource(R.drawable.tab_unselected)
            emptyIcon.text = "⭐"
            emptyText.text = "No favourite drivers yet"
        } else {
            tabBlocked.setTextColor(Color.parseColor("#FF6B00"))
            tabBlocked.setBackgroundResource(R.drawable.tab_selected)
            tabFav.setTextColor(Color.parseColor("#888888"))
            tabFav.setBackgroundResource(R.drawable.tab_unselected)
            emptyIcon.text = "🚫"
            emptyText.text = "No blocked drivers"
        }
        loadDrivers(filter)
    }

    private fun loadDrivers(filter: String) {
        progress.visibility = View.VISIBLE
        recycler.visibility = View.GONE
        emptyView.visibility = View.GONE

        ApiClient.getMyDrivers(acctId, filter) { arr ->
            progress.visibility = View.GONE
            driverList.clear()

            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val fn  = obj.optString("Drvr_FirstName")
                    val ln  = obj.optString("Drvr_LastName")
                    val uid = obj.optString("Drvr_ID")
                    val vehicleType = obj.optString("Drvr_VhclTypeID", "Vehicle")
                    driverList.add(DriverItem(
                        uid    = uid,
                        name   = "$fn $ln".trim().ifEmpty { "Driver" },
                        vehicle = vehicleType,
                        rating  = obj.optDouble("Drvr_Rating", 0.0).toFloat(),
                        status  = currentFilter
                    ))
                }
            }

            adapter.notifyDataSetChanged()

            if (driverList.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recycler.visibility  = View.GONE
            } else {
                emptyView.visibility = View.GONE
                recycler.visibility  = View.VISIBLE
            }
        }
    }

    private fun confirmRemove(item: DriverItem) {
        AlertDialog.Builder(this)
            .setTitle("Remove Driver")
            .setMessage("Remove ${item.name} from your ${item.status} list?")
            .setPositiveButton("Remove") { _, _ ->
                ApiClient.setDriverFavourite(acctId, item.uid, "none") { ok, _ ->
                    if (ok) {
                        val idx = driverList.indexOfFirst { it.uid == item.uid }
                        if (idx >= 0) {
                            driverList.removeAt(idx)
                            adapter.notifyItemRemoved(idx)
                            if (driverList.isEmpty()) {
                                emptyView.visibility = View.VISIBLE
                                recycler.visibility  = View.GONE
                            }
                        }
                    } else {
                        Toast.makeText(this, "Failed to remove driver.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────
    class DriverAdapter(
        private val items: List<DriverItem>,
        private val onRemove: (DriverItem) -> Unit
    ) : RecyclerView.Adapter<DriverAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val initials: TextView   = view.findViewById(R.id.driverInitials)
            val name: TextView       = view.findViewById(R.id.driverName)
            val vehicle: TextView    = view.findViewById(R.id.driverVehicle)
            val rating: TextView     = view.findViewById(R.id.driverRating)
            val badge: TextView      = view.findViewById(R.id.driverStatusBadge)
            val btnRemove: ImageView = view.findViewById(R.id.btnRemoveDriver)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_driver_card, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val parts = item.name.split(" ")
            holder.initials.text = parts.mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
                .ifEmpty { "DR" }
            holder.name.text    = item.name
            holder.vehicle.text = item.vehicle
            holder.rating.text  = if (item.rating > 0) String.format("%.1f", item.rating) else "N/A"

            if (item.status == "favourited") {
                holder.badge.text = "Favourited"
                holder.badge.setTextColor(Color.parseColor("#FF6B00"))
                holder.badge.setBackgroundColor(Color.parseColor("#FFF3E0"))
            } else {
                holder.badge.text = "Blocked"
                holder.badge.setTextColor(Color.parseColor("#E53935"))
                holder.badge.setBackgroundColor(Color.parseColor("#FFEBEE"))
            }

            holder.btnRemove.setOnClickListener { onRemove(items[position]) }
        }

        override fun getItemCount() = items.size
    }
}
