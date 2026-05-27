package com.example.lalamove

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class DriverRecordFragment : Fragment() {

    private lateinit var recyclerRecords: RecyclerView
    private lateinit var layoutNoRecords: LinearLayout
    private val allRecords = mutableListOf<Map<String, Any?>>()
    private lateinit var adapter: RecordAdapter
    private var currentFilter = "all"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_driver_record, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerRecords = view.findViewById(R.id.recyclerRecords)
        layoutNoRecords = view.findViewById(R.id.layoutNoRecords)

        adapter = RecordAdapter(mutableListOf())
        recyclerRecords.layoutManager = LinearLayoutManager(requireContext())
        recyclerRecords.adapter = adapter

        val filterAll       = view.findViewById<MaterialButton>(R.id.filterAll)
        val filterActive    = view.findViewById<MaterialButton>(R.id.filterActive)
        val filterCompleted = view.findViewById<MaterialButton>(R.id.filterCompleted)
        val filterCancelled = view.findViewById<MaterialButton>(R.id.filterCancelled)

        fun setActiveFilter(btn: MaterialButton, filter: String) {
            currentFilter = filter
            listOf(filterAll, filterActive, filterCompleted, filterCancelled).forEach { b ->
                b.setTextColor(Color.parseColor("#555555"))
                b.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            }
            btn.setTextColor(Color.parseColor("#FF6B00"))
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFF0E8"))
            applyFilter()
        }

        filterAll.setOnClickListener       { setActiveFilter(filterAll, "all") }
        filterActive.setOnClickListener    { setActiveFilter(filterActive, "active") }
        filterCompleted.setOnClickListener { setActiveFilter(filterCompleted, "delivered") }
        filterCancelled.setOnClickListener { setActiveFilter(filterCancelled, "cancelled") }

        loadRecords()
    }

    private fun loadRecords() {
        val driverId = SessionManager.getUserId(requireContext()).toString()
        FirebaseFirestore.getInstance()
            .collection("booking")
            .whereEqualTo("Book_DrvrID", driverId)
            .get()
            .addOnSuccessListener { docs ->
                if (!isAdded) return@addOnSuccessListener
                allRecords.clear()
                docs.documents.forEach { doc -> doc.data?.let { allRecords.add(it) } }
                applyFilter()
            }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            "all"       -> allRecords
            "active"    -> allRecords.filter { it["Book_Status"]?.toString() in listOf("accepted", "active", "picked_up") }
            "delivered" -> allRecords.filter { it["Book_Status"]?.toString() == "delivered" }
            "cancelled" -> allRecords.filter { it["Book_Status"]?.toString() == "cancelled" }
            else        -> allRecords
        }
        adapter.updateData(filtered)
        val empty = filtered.isEmpty()
        recyclerRecords.visibility = if (empty) View.GONE   else View.VISIBLE
        layoutNoRecords.visibility = if (empty) View.VISIBLE else View.GONE
    }

    class RecordAdapter(
        private val records: MutableList<Map<String, Any?>>
    ) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fare:    TextView = view.findViewById(R.id.recordFare)
            val status:  TextView = view.findViewById(R.id.recordStatus)
            val pickup:  TextView = view.findViewById(R.id.recordPickup)
            val dropoff: TextView = view.findViewById(R.id.recordDropoff)
            val vehicle: TextView = view.findViewById(R.id.recordVehicle)
            val payment: TextView = view.findViewById(R.id.recordPayment)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_driver_record, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = records[position]
            val fareVal = (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
            holder.fare.text    = "₱${String.format("%.2f", fareVal)}"
            holder.pickup.text  = order["Book_Pickuploc"]?.toString() ?: ""
            holder.dropoff.text = order["Book_Dropoffloc"]?.toString() ?: ""
            holder.vehicle.text = (order["Book_VhclTypeID"]?.toString() ?: "").replaceFirstChar { it.uppercase() }
            holder.payment.text = order["Book_PaymentMethod"]?.toString() ?: "Cash"

            val rawStatus = order["Book_Status"]?.toString() ?: ""
            val (label, textColor, bgColor) = when (rawStatus) {
                "delivered"  -> Triple("COMPLETED", "#1B5E20", "#E8F5E9")
                "cancelled"  -> Triple("CANCELLED", "#B71C1C", "#FFEBEE")
                "accepted",
                "active",
                "picked_up"  -> Triple("ACTIVE", "#E65100", "#FFF3E0")
                "pending"    -> Triple("PENDING", "#1565C0", "#E3F2FD")
                else         -> Triple(rawStatus.uppercase(), "#555555", "#F5F5F5")
            }
            holder.status.text = label
            holder.status.setTextColor(Color.parseColor(textColor))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(Color.parseColor(bgColor))
                cornerRadius = 40f
            }
            holder.status.background = bg
        }

        override fun getItemCount() = records.size

        fun updateData(newData: List<Map<String, Any?>>) {
            records.clear()
            records.addAll(newData)
            notifyDataSetChanged()
        }
    }
}
