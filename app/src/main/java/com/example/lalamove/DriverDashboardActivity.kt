package com.example.lalamove

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class DriverDashboardActivity : AppCompatActivity() {

    private val pickupFragment  = DriverPickupFragment()
    private val recordFragment  = DriverRecordFragment()
    private val walletFragment  = DriverWalletFragment()
    private val profileFragment = DriverProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_dashboard)

        if (savedInstanceState == null) {
            showFragment(pickupFragment, "pickup")
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.driverBottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_pickup  -> { showFragment(pickupFragment,  "pickup");  true }
                R.id.tab_record  -> { showFragment(recordFragment,  "record");  true }
                R.id.tab_wallet  -> { showFragment(walletFragment,  "wallet");  true }
                R.id.tab_profile -> { showFragment(profileFragment, "profile"); true }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        // Hide all
        listOf(pickupFragment, recordFragment, walletFragment, profileFragment).forEach { f ->
            if (f.isAdded) tx.hide(f)
        }
        // Add or show the target
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.driverFragmentContainer, fragment, tag)
        } else {
            tx.show(fragment)
        }
        tx.commitNow()
    }

    // Shared adapter used by DriverPickupFragment
    class AvailableOrdersAdapter(
        private val orders: List<Map<String, Any?>>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<AvailableOrdersAdapter.ViewHolder>() {

        var orderDocIds: List<String> = emptyList()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fare:      TextView = view.findViewById(R.id.orderFare)
            val pickup:    TextView = view.findViewById(R.id.orderPickup)
            val dropoff:   TextView = view.findViewById(R.id.orderDropoff)
            val vehicle:   TextView = view.findViewById(R.id.orderVehicle)
            val payment:   TextView = view.findViewById(R.id.orderPayment)
            val bookingId: TextView = view.findViewById(R.id.orderBookingId)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_available_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order   = orders[position]
            val fareVal = (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
            holder.fare.text      = "₱${String.format("%.2f", fareVal)}"
            holder.pickup.text    = order["Book_Pickuploc"]?.toString() ?: "Pickup"
            holder.dropoff.text   = order["Book_Dropoffloc"]?.toString() ?: "Dropoff"
            holder.vehicle.text   = (order["Book_VhclTypeID"]?.toString() ?: "motorcycle").replaceFirstChar { it.uppercase() }
            holder.payment.text   = order["Book_PaymentMethod"]?.toString() ?: "Cash"
            holder.bookingId.text = "ID: ${if (orderDocIds.size > position) orderDocIds[position] else "..."}"
            holder.itemView.setOnClickListener { onItemClick(position) }
        }

        override fun getItemCount() = orders.size
    }
}
