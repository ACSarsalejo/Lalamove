package com.example.lalamove

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class DriverEarningsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_earnings)

        val firestore = FirebaseFirestore.getInstance()
        val driverId = SessionManager.getUserId(this)

        val textTotalEarnings  = findViewById<TextView>(R.id.textTotalEarnings)
        val textTotalTrips     = findViewById<TextView>(R.id.textTotalTrips)
        val textCommissionRate = findViewById<TextView>(R.id.textCommissionRate)
        val recyclerEarnings   = findViewById<RecyclerView>(R.id.recyclerEarnings)
        val textNoEarnings     = findViewById<TextView>(R.id.textNoEarnings)
        val btnCashOut         = findViewById<MaterialButton>(R.id.btnCashOut)

        findViewById<ImageView>(R.id.btnBackEarnings).setOnClickListener { finish() }

        btnCashOut.setOnClickListener {
            val totalText = textTotalEarnings.text.toString()
                .replace("₱", "").replace(",", "").trim()
            val total = totalText.toDoubleOrNull() ?: 0.0
            if (total <= 0) {
                Toast.makeText(this, "No earnings to cash out!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Cash Out Earnings")
                .setMessage("Withdraw ₱${String.format("%,.2f", total)} to your linked bank account?")
                .setPositiveButton("Withdraw") { _, _ ->
                    btnCashOut.isEnabled = false
                    btnCashOut.text      = "Processing..."
                    ApiClient.walletCashOut(driverId, "driver", total) { success, newBalance, msg ->
                        btnCashOut.isEnabled = true
                        btnCashOut.text      = "Cash Out"
                        if (success) {
                            textTotalEarnings.text = "₱0.00"
                            Toast.makeText(
                                this,
                                "✅ Withdrawal of ₱${String.format("%,.2f", total)} recorded! Ref: $msg",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this, msg.ifEmpty { "Withdrawal failed. Try again." }, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Fetch driver's commission rate from Firestore driver document
        var commissionRate = 0.80
        firestore.collection("driver").document(driverId).get()
            .addOnSuccessListener { doc ->
                val rate = doc.getDouble("Drvr_CommissionRate")
                if (rate != null) commissionRate = rate
                val pct = (commissionRate * 100).toInt()
                textCommissionRate.text = "Commission rate: $pct% of fare"
            }

        // Query completed deliveries from Firestore
        firestore.collection("booking")
            .whereEqualTo("Book_DrvrID", driverId)
            .whereEqualTo("Book_Status", "delivered")
            .get()
            .addOnSuccessListener { docs ->
                val deliveries = mutableListOf<Map<String, Any?>>()
                var total = 0.0
                docs.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    deliveries.add(data)
                    val fare = (data["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
                    total += fare * commissionRate   // driver earns commissionRate % of fare
                }
                textTotalEarnings.text = "₱${String.format("%,.2f", total)}"
                textTotalTrips.text    = "${deliveries.size} completed deliveries"
                if (deliveries.isEmpty()) {
                    textNoEarnings.visibility   = View.VISIBLE
                    recyclerEarnings.visibility = View.GONE
                } else {
                    textNoEarnings.visibility   = View.GONE
                    recyclerEarnings.visibility = View.VISIBLE
                    recyclerEarnings.layoutManager = LinearLayoutManager(this)
                    recyclerEarnings.adapter = EarningsAdapter(deliveries, commissionRate)
                }
            }
    }

    class EarningsAdapter(
        private val deliveries: List<Map<String, Any?>>,
        private val commissionRate: Double = 0.80
    ) : RecyclerView.Adapter<EarningsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fare:    TextView = view.findViewById(R.id.orderFare)
            val pickup:  TextView = view.findViewById(R.id.orderPickup)
            val dropoff: TextView = view.findViewById(R.id.orderDropoff)
            val vehicle: TextView = view.findViewById(R.id.orderVehicle)
            val payment: TextView = view.findViewById(R.id.orderPayment)
            val status:  TextView = view.findViewById(R.id.orderStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_available_order, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order   = deliveries[position]
            val fareVal = (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
            val earned  = fareVal * commissionRate
            holder.fare.text    = "₱${String.format("%,.2f", earned)}"
            holder.pickup.text  = order["Book_Pickuploc"]?.toString() ?: ""
            holder.dropoff.text = order["Book_Dropoffloc"]?.toString() ?: ""
            holder.vehicle.text = (order["Book_VhclTypeID"]?.toString() ?: "").replaceFirstChar { it.uppercase() }
            holder.payment.text = order["Book_PaymentMethod"]?.toString() ?: "Cash"
            holder.status.text  = "COMPLETED"
            holder.status.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.parseColor("#1B5E20"))
                alpha = 40
                cornerRadius = 40f
            }
            holder.status.background = bg
        }

        override fun getItemCount() = deliveries.size
    }
}
