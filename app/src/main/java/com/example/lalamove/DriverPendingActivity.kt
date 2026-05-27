package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore

class DriverPendingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_pending)

        val stepIdentity   = findViewById<TextView>(R.id.stepIdentityStatus)
        val stepVehicle    = findViewById<TextView>(R.id.stepVehicleStatus)
        val stepActivation = findViewById<TextView>(R.id.stepActivationStatus)

        fun setStepStatus(tv: TextView, status: String) {
            when (status) {
                "approved" -> { tv.text = "APPROVED"; tv.setTextColor(android.graphics.Color.parseColor("#4CAF50")) }
                "rejected" -> { tv.text = "REJECTED"; tv.setTextColor(android.graphics.Color.parseColor("#F44336")) }
                "waiting"  -> { tv.text = "WAITING";  tv.setTextColor(android.graphics.Color.parseColor("#555555")) }
                else       -> { tv.text = "PENDING";  tv.setTextColor(android.graphics.Color.parseColor("#FF6B00")) }
            }
        }

        fun checkStatus() {
            val driverId = SessionManager.getUserId(this)
            // Check Firestore driver document for verification status
            FirebaseFirestore.getInstance().collection("driver").document(driverId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val isVerified = doc.getBoolean("Drvr_IsVerified") ?: false
                        if (isVerified) {
                            // Verified! Update session and go to dashboard
                            Toast.makeText(this, "🎉 Your account has been approved!", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, DriverDashboardActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            finish()
                            return@addOnSuccessListener
                        }

                        // Show step-by-step status from Firestore fields (if set by admin)
                        val identityStep   = doc.getString("Drvr_IdentityStatus")   ?: "pending"
                        val vehicleStep    = doc.getString("Drvr_VehicleStatus")    ?: "pending"

                        setStepStatus(stepIdentity,   identityStep)
                        setStepStatus(stepVehicle,    vehicleStep)

                        // Activation is only approved when all others are approved
                        val allApproved = identityStep == "approved" && vehicleStep == "approved"
                        setStepStatus(stepActivation, if (allApproved) "pending" else "waiting")
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Could not fetch status. Check your connection.", Toast.LENGTH_SHORT).show()
                }
        }

        checkStatus()

        findViewById<MaterialButton>(R.id.btnRefreshPending).setOnClickListener {
            Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
            checkStatus()
        }

        findViewById<MaterialButton>(R.id.btnLogoutPending).setOnClickListener {
            SessionManager.clear(this)
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }
}
