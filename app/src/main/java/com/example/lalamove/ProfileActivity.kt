package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val name  = SessionManager.getName(this)
        val email = SessionManager.getEmail(this)

        // Header
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        findViewById<TextView>(R.id.profileInitial).text = initial
        findViewById<TextView>(R.id.userName).text       = name.ifEmpty { "Customer" }
        findViewById<TextView>(R.id.userEmail).text      = email

        // Menu items
        findViewById<LinearLayout>(R.id.menuOrders).setOnClickListener {
            startActivity(Intent(this, CustomerOrdersActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuWallet).setOnClickListener {
            val role = SessionManager.getRole(this) ?: "customer"
            val dest = if (role == "driver") WalletActivity::class.java else CustomerWalletActivity::class.java
            startActivity(Intent(this, dest))
        }

        findViewById<LinearLayout>(R.id.menuSavedAddresses).setOnClickListener {
            startActivity(Intent(this, SavedAddressesActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuDeliveryForm).setOnClickListener {
            startActivity(Intent(this, DeliveryFormActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuMyDrivers).setOnClickListener {
            startActivity(Intent(this, MyDriversActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuHelpCenter).setOnClickListener {
            Toast.makeText(this, "Help Center coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Sign out
        findViewById<MaterialButton>(R.id.btnSignOut).setOnClickListener {
            SessionManager.clear(this)
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh name/email in case settings were updated
        val name  = SessionManager.getName(this)
        val email = SessionManager.getEmail(this)
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        findViewById<TextView>(R.id.profileInitial).text = initial
        findViewById<TextView>(R.id.userName).text       = name.ifEmpty { "Customer" }
        findViewById<TextView>(R.id.userEmail).text      = email
    }
}
