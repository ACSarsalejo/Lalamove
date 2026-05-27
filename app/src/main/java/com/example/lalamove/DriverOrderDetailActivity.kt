package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class DriverOrderDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_order_detail)

        val firestore = FirebaseFirestore.getInstance()

        val orderId = intent.getStringExtra("ORDER_ID") ?: ""
        val pickup = intent.getStringExtra("PICKUP") ?: ""
        val dropoff = intent.getStringExtra("DROPOFF") ?: ""
        val fare = intent.getStringExtra("FARE") ?: "₱0.00"
        val vehicle = intent.getStringExtra("VEHICLE") ?: "motorcycle"
        val payment = intent.getStringExtra("PAYMENT") ?: "Cash"
        val contact = intent.getStringExtra("CONTACT") ?: ""
        val notes = intent.getStringExtra("NOTES") ?: ""
        val itemTitle = intent.getStringExtra("ITEM_TITLE") ?: ""
        val itemSubtitle = intent.getStringExtra("ITEM_SUBTITLE") ?: ""

        // Populate views
        findViewById<TextView>(R.id.drvBookingId).text = orderId
        findViewById<TextView>(R.id.drvOrderFare).text = fare
        findViewById<TextView>(R.id.drvOrderPickup).text = pickup
        findViewById<TextView>(R.id.drvOrderDropoff).text = dropoff
        findViewById<TextView>(R.id.drvOrderVehicle).text = vehicle.replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.drvOrderPayment).text = "$payment • Paid by sender"
        findViewById<TextView>(R.id.drvOrderContact).text = contact

        // Notes
        if (notes.isNotEmpty()) {
            findViewById<View>(R.id.drvNotesSection).visibility = View.VISIBLE
            findViewById<TextView>(R.id.drvOrderNotes).text = notes
        }

        // Item Details — from intent extras first, then fallback to Firestore query
        if (itemTitle.isNotEmpty()) {
            showItemDetails(itemTitle, itemSubtitle)
        } else {
            // Query Firestore for item details linked to this booking
            fetchItemDetailsFromFirestore(firestore, orderId)
        }

        // Back
        findViewById<ImageView>(R.id.btnBackOrderReview).setOnClickListener { finish() }

        // Take Order
        val btnTakeOrder = findViewById<MaterialButton>(R.id.btnTakeOrder)
        btnTakeOrder.setOnClickListener {
            btnTakeOrder.isEnabled = false
            btnTakeOrder.text = "Taking order..."

            val driverId = SessionManager.getUserId(this@DriverOrderDetailActivity)

            // Update booking status and assign driver
            firestore.collection("booking").document(orderId)
                .update(
                    mapOf(
                        "Book_Status" to "accepted",
                        "Book_DrvrID" to driverId
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "Order accepted!", Toast.LENGTH_SHORT).show()
                    // Navigate to Active Delivery screen
                    val intent = Intent(this, DriverActiveDeliveryActivity::class.java).apply {
                        putExtra("ORDER_ID", orderId)
                        putExtra("PICKUP", pickup)
                        putExtra("DROPOFF", dropoff)
                        putExtra("FARE", fare)
                        putExtra("VEHICLE", vehicle)
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    btnTakeOrder.isEnabled = true
                    btnTakeOrder.text = "Take Order"
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showItemDetails(title: String, subtitle: String) {
        val section = findViewById<LinearLayout>(R.id.drvItemSection)
        section.visibility = View.VISIBLE
        findViewById<TextView>(R.id.drvItemTitle).text = title
        if (subtitle.isNotEmpty()) {
            findViewById<TextView>(R.id.drvItemSubtitle).text = subtitle
            findViewById<TextView>(R.id.drvItemSubtitle).visibility = View.VISIBLE
        }
    }

    private fun fetchItemDetailsFromFirestore(firestore: FirebaseFirestore, bookingId: String) {
        // Find delivery linked to this booking
        firestore.collection("delivery")
            .whereEqualTo("Dlvr_BookID", bookingId)
            .limit(1)
            .get()
            .addOnSuccessListener { deliveryDocs ->
                if (!deliveryDocs.isEmpty) {
                    val dlvrDoc = deliveryDocs.documents[0]
                    val dlvrId = dlvrDoc.id

                    // Now find items linked to this delivery
                    firestore.collection("item")
                        .whereEqualTo("Item_DlvrID", dlvrId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { itemDocs ->
                            if (!itemDocs.isEmpty) {
                                val itemDoc = itemDocs.documents[0]
                                val category = itemDoc.getString("Item_Category") ?: ""
                                val name = itemDoc.getString("Item_Name") ?: itemDoc.getString("Item_Description") ?: ""
                                val size = itemDoc.getString("Item_Size") ?: ""
                                val weight = itemDoc.get("Item_WeightKG")?.toString() ?: ""
                                val quantity = itemDoc.get("Item_Quantity")?.toString() ?: ""

                                val title = if (category.isNotEmpty()) category else name
                                val parts = mutableListOf<String>()
                                if (quantity.isNotEmpty() && quantity != "0") parts.add("$quantity package(s)")
                                if (weight.isNotEmpty() && weight != "0.0") parts.add("${weight} kg")
                                if (size.isNotEmpty() && size != "Select size") parts.add(size)
                                val subtitle = parts.joinToString(" · ")

                                if (title.isNotEmpty()) {
                                    showItemDetails(title, subtitle)
                                }
                            }
                        }
                }
            }

        // Also check if booking itself has item info stored
        firestore.collection("booking").document(bookingId).get()
            .addOnSuccessListener { doc ->
                val bookItemTitle = doc.getString("Book_ItemTitle") ?: ""
                val bookItemSubtitle = doc.getString("Book_ItemSubtitle") ?: ""
                if (bookItemTitle.isNotEmpty()) {
                    showItemDetails(bookItemTitle, bookItemSubtitle)
                }
            }
    }
}

