package com.example.lalamove

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DriverPickupFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private var ordersListener: ListenerRegistration? = null
    private val ordersList = mutableListOf<Map<String, Any?>>()
    private val orderIds   = mutableListOf<String>()
    private lateinit var adapter: DriverDashboardActivity.AvailableOrdersAdapter

    private lateinit var btnStatusDropdown: LinearLayout
    private lateinit var textStatusLabel: TextView
    private lateinit var statusDot: View
    private lateinit var offlineOverlay: View
    private lateinit var onlineContent: View
    private lateinit var recyclerOrders: RecyclerView
    private lateinit var textNoOrders: TextView

    private var isOnline = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_driver_pickup, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()

        btnStatusDropdown = view.findViewById(R.id.btnStatusDropdown)
        textStatusLabel   = view.findViewById(R.id.textStatusLabel)
        statusDot         = view.findViewById(R.id.statusDot)
        offlineOverlay    = view.findViewById(R.id.offlineOverlay)
        onlineContent     = view.findViewById(R.id.onlineContent)
        recyclerOrders    = view.findViewById(R.id.recyclerOrders)
        textNoOrders      = view.findViewById(R.id.textNoOrders)

        adapter = DriverDashboardActivity.AvailableOrdersAdapter(ordersList) { position ->
            val order   = ordersList[position]
            val orderId = orderIds[position]
            val intent  = Intent(requireContext(), DriverOrderDetailActivity::class.java).apply {
                putExtra("ORDER_ID",       orderId)
                putExtra("PICKUP",         order["Book_Pickuploc"]?.toString() ?: "")
                putExtra("DROPOFF",        order["Book_Dropoffloc"]?.toString() ?: "")
                putExtra("FARE",           "₱${String.format("%.2f", (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0)}")
                putExtra("VEHICLE",        order["Book_VhclTypeID"]?.toString() ?: "motorcycle")
                putExtra("PAYMENT",        order["Book_PaymentMethod"]?.toString() ?: "Cash")
                putExtra("CONTACT",        order["Book_ContactPhone"]?.toString() ?: "")
                putExtra("NOTES",          order["Book_Notes"]?.toString() ?: "")
                putExtra("ITEM_TITLE",     order["Book_ItemTitle"]?.toString() ?: "")
                putExtra("ITEM_SUBTITLE",  order["Book_ItemSubtitle"]?.toString() ?: "")
            }
            startActivity(intent)
        }
        recyclerOrders.layoutManager = LinearLayoutManager(requireContext())
        recyclerOrders.adapter = adapter

        val prefs = requireActivity().getSharedPreferences("DriverPrefs", android.content.Context.MODE_PRIVATE)
        isOnline = prefs.getBoolean("isOnline", false)
        applyOnlineState(isOnline)

        btnStatusDropdown.setOnClickListener { showStatusMenu() }

        view.findViewById<ImageView>(R.id.btnNotifications).setOnClickListener {
            Toast.makeText(requireContext(), "Notifications coming soon", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            Toast.makeText(requireContext(), "Settings coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStatusMenu() {
        val popup = PopupMenu(requireContext(), btnStatusDropdown)
        popup.menu.add(0, 1, 0, if (isOnline) "Go Offline" else "Go Online")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                if (isOnline) {
                    setOnline(false)
                } else {
                    goOnlineWithBalanceCheck()
                }
            }
            true
        }
        popup.show()
    }

    private fun goOnlineWithBalanceCheck() {
        val userId = SessionManager.getUserId(requireContext())
        val role   = SessionManager.getRole(requireContext()) ?: "driver"
        ApiClient.walletBalance(userId, role) { balance ->
            val bal = balance ?: 0.0
            if (bal < 100.0) {
                Toast.makeText(
                    requireContext(),
                    "You need at least ₱100 in your wallet to go online.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                setOnline(true)
            }
        }
    }

    private fun setOnline(online: Boolean) {
        isOnline = online
        requireActivity().getSharedPreferences("DriverPrefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("isOnline", online).apply()
        applyOnlineState(online)
    }

    private fun applyOnlineState(online: Boolean) {
        if (online) {
            textStatusLabel.text = "Online"
            textStatusLabel.setTextColor(Color.parseColor("#4CAF50"))
            offlineOverlay.visibility = View.GONE
            onlineContent.visibility  = View.VISIBLE
            startListeningForOrders()
        } else {
            textStatusLabel.text = "Offline"
            textStatusLabel.setTextColor(Color.parseColor("#CCCCCC"))
            offlineOverlay.visibility = View.VISIBLE
            onlineContent.visibility  = View.GONE
            stopListeningForOrders()
        }
    }

    private fun startListeningForOrders() {
        ordersListener = firestore.collection("booking")
            .whereEqualTo("Book_Status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null || !isAdded) return@addSnapshotListener
                ordersList.clear()
                orderIds.clear()
                snapshots?.documents?.forEach { doc ->
                    ordersList.add(doc.data ?: emptyMap())
                    orderIds.add(doc.id)
                }
                adapter.orderDocIds = orderIds
                adapter.notifyDataSetChanged()
                val empty = ordersList.isEmpty()
                textNoOrders.visibility  = if (empty) View.VISIBLE else View.GONE
                recyclerOrders.visibility = if (empty) View.GONE   else View.VISIBLE
            }
    }

    private fun stopListeningForOrders() {
        ordersListener?.remove()
        ordersListener = null
    }

    override fun onResume() {
        super.onResume()
        if (isOnline) startListeningForOrders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListeningForOrders()
    }
}
