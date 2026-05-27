package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class DriverFoundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_found)

        val driverId = intent.getStringExtra("DRIVER_ID") ?: ""
        val orderId = intent.getStringExtra("ORDER_ID") ?: ""
        val mysqlOrderId = intent.getStringExtra("MYSQL_ORDER_ID") ?: orderId
        val pickup = intent.getStringExtra("PICKUP") ?: ""
        val dropoff = intent.getStringExtra("DROPOFF") ?: ""
        val fare = intent.getStringExtra("FARE") ?: "₱0.00"
        val vehicle = intent.getStringExtra("VEHICLE") ?: "motorcycle"
        val payment = intent.getStringExtra("PAYMENT") ?: "Cash"
        val notes = intent.getStringExtra("NOTES") ?: ""
        val status = intent.getStringExtra("STATUS") ?: "accepted"
        val isRated = intent.getBooleanExtra("IS_RATED", false)

        val textMessage = findViewById<TextView>(R.id.textDriverFoundMessage)

        // Fetch driver details from Firestore to build the personalized message
        if (driverId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("driver").document(driverId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val firstName = doc.getString("Drvr_FirstName") ?: "Your driver"
                        val lastName = doc.getString("Drvr_LastName") ?: ""
                        val vhclType = doc.getString("Drvr_VhclTypeID")?.replaceFirstChar { it.uppercase() } ?: "vehicle"
                        val plateNumber = doc.getString("Drvr_LicenseNum")?.takeIf { it.isNotBlank() } ?: "2938EA"
                        val color = doc.getString("Drvr_MotorColor") ?: "red"

                        textMessage.text = "$firstName $lastName has accepted your booking and is on the way. Look for a $color $vhclType with plate number $plateNumber."
                    } else {
                        textMessage.text = "A driver has accepted your booking and is on the way!"
                    }
                }
                .addOnFailureListener {
                    textMessage.text = "A driver has accepted your booking and is on the way!"
                }
        } else {
            textMessage.text = "A driver has accepted your booking and is on the way!"
        }

        // Continue button → navigate to order detail
        findViewById<MaterialButton>(R.id.btnContinueToOrder).setOnClickListener {
            val detailIntent = Intent(this, CustomerOrderDetailActivity::class.java).apply {
                putExtra("ORDER_ID", orderId)
                putExtra("MYSQL_ORDER_ID", mysqlOrderId)
                putExtra("PICKUP", pickup)
                putExtra("DROPOFF", dropoff)
                putExtra("FARE", fare)
                putExtra("VEHICLE", vehicle)
                putExtra("PAYMENT", payment)
                putExtra("NOTES", notes)
                putExtra("STATUS", status)
                putExtra("IS_RATED", isRated)
                putExtra("DRIVER_ID", driverId)
            }
            startActivity(detailIntent)
            finish()
        }
    }
}
