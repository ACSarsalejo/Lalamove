package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide the action bar if present
        supportActionBar?.hide()

        Handler(Looper.getMainLooper()).postDelayed({
            val hasSession = SessionManager.getUserId(this).isNotEmpty()
            if (hasSession) {
                // Ensure Firebase is signed in anonymously if not already signed in
                if (FirebaseAuth.getInstance().currentUser == null) {
                    FirebaseAuth.getInstance().signInAnonymously()
                }
                // Already logged in — go straight to the right dashboard
                val role = SessionManager.getRole(this) ?: "customer"
                val dest = if (role == "driver") {
                    val isVerified = SessionManager.isVerified(this)
                    if (isVerified) DriverDashboardActivity::class.java
                    else DriverPendingActivity::class.java
                } else {
                    VehicleSelectionActivity::class.java
                }
                startActivity(Intent(this, dest))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 1800) // 1.8 second splash
    }
}
