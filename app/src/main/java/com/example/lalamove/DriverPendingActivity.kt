package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DriverPendingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_pending)

        findViewById<Button>(R.id.btnLogoutPending).setOnClickListener {
            SessionManager.clear(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
