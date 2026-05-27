package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.FirebaseFirestore

class DriverFoundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_found)

        val driverId     = intent.getStringExtra("DRIVER_ID") ?: ""
        val orderId      = intent.getStringExtra("ORDER_ID") ?: ""
        val mysqlOrderId = intent.getStringExtra("MYSQL_ORDER_ID") ?: orderId
        val pickup       = intent.getStringExtra("PICKUP") ?: ""
        val dropoff      = intent.getStringExtra("DROPOFF") ?: ""
        val fare         = intent.getStringExtra("FARE") ?: "₱0.00"
        val vehicle      = intent.getStringExtra("VEHICLE") ?: "motorcycle"
        val payment      = intent.getStringExtra("PAYMENT") ?: "Cash"
        val notes        = intent.getStringExtra("NOTES") ?: ""
        val status       = intent.getStringExtra("STATUS") ?: "accepted"
        val isRated      = intent.getBooleanExtra("IS_RATED", false)

        val textMessage  = findViewById<TextView>(R.id.textDriverFoundMessage)
        val textName     = findViewById<TextView>(R.id.textDriverName)
        val imgPhoto     = findViewById<ShapeableImageView>(R.id.imgDriverPhoto)

        // Show placeholder immediately
        Glide.with(this)
            .load(R.drawable.ic_profile_placeholder)
            .into(imgPhoto)

        // Fetch driver profile from Firestore
        if (driverId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("driver").document(driverId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val firstName = doc.getString("Drvr_FirstName") ?: "Your driver"
                        val lastName  = doc.getString("Drvr_LastName") ?: ""
                        val fullName  = "$firstName $lastName".trim()
                        val vhclType  = doc.getString("Drvr_VhclTypeID")
                            ?.replaceFirstChar { it.uppercase() } ?: "vehicle"
                        val plateNum  = doc.getString("Drvr_LicenseNum")
                            ?.takeIf { it.isNotBlank() && !it.startsWith("TEMP-") } ?: ""
                        val color     = doc.getString("Drvr_MotorColor") ?: ""
                        val photoUrl  = doc.getString("Drvr_ProfilePhoto") ?: ""

                        // Show driver name
                        textName.text = fullName

                        // Load photo or keep placeholder
                        if (photoUrl.isNotEmpty()) {
                            Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .error(R.drawable.ic_profile_placeholder)
                                .centerCrop()
                                .into(imgPhoto)
                        }

                        // Build message
                        val vehicleDesc = listOfNotNull(
                            color.takeIf { it.isNotEmpty() },
                            vhclType,
                            plateNum.takeIf { it.isNotEmpty() }?.let { "($it)" }
                        ).joinToString(" ")
                        textMessage.text = if (vehicleDesc.isNotEmpty())
                            "$fullName has accepted your booking and is on the way. Look for a $vehicleDesc."
                        else
                            "$fullName has accepted your booking and is on the way!"

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

        // Continue → order detail
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
