package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Only step 1 is used — Firebase sends the reset email directly.
        // Hide step 2 (new password fields) since Firebase handles that via email link.
        val step2 = findViewById<View>(R.id.step2Layout)
        step2.visibility = View.GONE

        val inputEmail = findViewById<TextInputEditText>(R.id.inputForgotEmail)
        val btnCheck   = findViewById<MaterialButton>(R.id.btnCheckEmail)

        findViewById<ImageView>(R.id.btnBackForgot).setOnClickListener { finish() }

        btnCheck.text = "Send Reset Email"

        btnCheck.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnCheck.isEnabled = false
            btnCheck.text = "Sending…"

            ApiClient.forgotPassword(email) { success, msg ->
                btnCheck.isEnabled = true
                btnCheck.text = "Send Reset Email"
                if (success) {
                    Toast.makeText(this, "✅ Reset email sent! Check your inbox and follow the link.", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
