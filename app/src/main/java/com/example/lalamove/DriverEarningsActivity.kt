package com.example.lalamove

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        val driverId = SessionManager.getUserId(this).toString()

        val textTotalEarnings = findViewById<TextView>(R.id.textTotalEarnings)
        val textTotalTrips = findViewById<TextView>(R.id.textTotalTrips)
        val recyclerEarnings = findViewById<RecyclerView>(R.id.recyclerEarnings)
        val textNoEarnings = findViewById<TextView>(R.id.textNoEarnings)

        findViewById<ImageView>(R.id.btnBackEarnings).setOnClickListener { finish() }

        // Cash Out Logic
        val btnCashOut = findViewById<MaterialButton>(R.id.btnCashOut)
        btnCashOut.setOnClickListener {
            val total = textTotalEarnings.text.toString().replace("₱", "").toDoubleOrNull() ?: 0.0
            if (total <= 0) {
                Toast.makeText(this, "No earnings to cash out!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Cash Out Earnings")
                .setMessage("Withdraw ₱${String.format("%.2f", total)} to your account?")
                .setPositiveButton("Withdraw") { _, _ ->
                    btnCashOut.isEnabled = false
                    btnCashOut.text = "Processing..."
                    
                    // Simulate API call to driver_actions.php?action=cashout
                    Handler(Looper.getMainLooper()).postDelayed({
                        Toast.makeText(this, "✅ Withdrawal successful! Check your bank in 2-3 days.", Toast.LENGTH_LONG).show()
                        textTotalEarnings.text = "₱0.00"
                        btnCashOut.text = "Cash Out"
                        btnCashOut.isEnabled = true
                    }, 2000)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Query completed deliveries assigned to this driver
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
                    total += (data["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
                }

                textTotalEarnings.text = "₱${String.format("%.2f", total)}"
                textTotalTrips.text = "${deliveries.size} completed deliveries"

                if (deliveries.isEmpty()) {
                    textNoEarnings.visibility = View.VISIBLE
                    recyclerEarnings.visibility = View.GONE
                } else {
                    textNoEarnings.visibility = View.GONE
                    recyclerEarnings.visibility = View.VISIBLE
                    recyclerEarnings.layoutManager = LinearLayoutManager(this)
                    recyclerEarnings.adapter = EarningsAdapter(deliveries)
                }
            }
    }

    // Simple adapter for completed deliveries
    class EarningsAdapter(
        private val deliveries: List<Map<String, Any?>>
    ) : RecyclerView.Adapter<EarningsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fare: TextView = view.findViewById(R.id.orderFare)
            val pickup: TextView = view.findViewById(R.id.orderPickup)
            val dropoff: TextView = view.findViewById(R.id.orderDropoff)
            val vehicle: TextView = view.findViewById(R.id.orderVehicle)
            val payment: TextView = view.findViewById(R.id.orderPayment)
            val status: TextView = view.findViewById(R.id.orderStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_available_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val order = deliveries[position]
            val fareVal = (order["Book_TotalFare"] as? Number)?.toDouble() ?: 0.0
            holder.fare.text = "₱${String.format("%.2f", fareVal)}"
            holder.pickup.text = order["Book_Pickuploc"]?.toString() ?: ""
            holder.dropoff.text = order["Book_Dropoffloc"]?.toString() ?: ""
            holder.vehicle.text = (order["Book_VhclTypeID"]?.toString() ?: "").replaceFirstChar { it.uppercase() }
            holder.payment.text = order["Book_PaymentMethod"]?.toString() ?: "Cash"
            holder.status.text = "COMPLETED"
            holder.status.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.status.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
        }

        override fun getItemCount() = deliveries.size
    }
}
