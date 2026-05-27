package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Get the saved display name
        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val displayName = sharedPref.getString("displayName", "Achilly")

        // Update the user name in header
        val userName = findViewById<TextView>(R.id.userName)
        userName.text = displayName

        // Menu items
        val menuOrders = findViewById<LinearLayout>(R.id.menuOrders)
        val menuWallet = findViewById<LinearLayout>(R.id.menuWallet)
        val menuDeliveryForm = findViewById<LinearLayout>(R.id.menuDeliveryForm)
        val menuMyDrivers = findViewById<LinearLayout>(R.id.menuMyDrivers)
        val menuHelpCenter = findViewById<LinearLayout>(R.id.menuHelpCenter)
        val menuSettings = findViewById<LinearLayout>(R.id.menuSettings)
        val btnSignOut = findViewById<Button>(R.id.btnSignOut)

        // Set click listeners (just show toast for now)
        menuOrders.setOnClickListener {
            Toast.makeText(this, "Orders - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        menuWallet.setOnClickListener {
            Toast.makeText(this, "Wallet - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        menuDeliveryForm.setOnClickListener {
            Toast.makeText(this, "Delivery Form - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        menuMyDrivers.setOnClickListener {
            Toast.makeText(this, "My Drivers - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        menuHelpCenter.setOnClickListener {
            Toast.makeText(this, "Help Center - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        menuSettings.setOnClickListener {
            Toast.makeText(this, "Settings - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // Sign Out button click
        btnSignOut.setOnClickListener {
            // Clear saved user data
            val editor = sharedPref.edit()
            editor.clear()
            editor.apply()

            Toast.makeText(this, "Signed out successfully!", Toast.LENGTH_SHORT).show()

            // Navigate back to login screen
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}