package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ForgotPasswordActivity : AppCompatActivity() {

    private var verifiedEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val step1       = findViewById<View>(R.id.step1Layout)
        val step2       = findViewById<View>(R.id.step2Layout)
        val inputEmail  = findViewById<TextInputEditText>(R.id.inputForgotEmail)
        val btnContinue = findViewById<MaterialButton>(R.id.btnCheckEmail)
        val verifiedTv  = findViewById<TextView>(R.id.textVerifiedEmail)
        val inputNewPw  = findViewById<TextInputEditText>(R.id.inputNewPassword)
        val inputConfPw = findViewById<TextInputEditText>(R.id.inputConfirmPassword)
        val btnReset    = findViewById<MaterialButton>(R.id.btnResetPassword)

        // Start on step 1
        step1.visibility = View.VISIBLE
        step2.visibility = View.GONE

        findViewById<ImageView>(R.id.btnBackForgot).setOnClickListener {
            if (step2.visibility == View.VISIBLE) {
                // Go back to step 1 instead of closing
                step2.visibility = View.GONE
                step1.visibility = View.VISIBLE
            } else {
                finish()
            }
        }

        // ── Step 1: Verify email exists ─────────────────────────────────────
        btnContinue.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnContinue.isEnabled = false
            btnContinue.text = "Checking…"

            ApiClient.checkForgotEmail(email) { found, msg ->
                btnContinue.isEnabled = true
                btnContinue.text = "Continue"
                if (found) {
                    verifiedEmail = email
                    verifiedTv.text = "Resetting password for: $email"
                    step1.visibility = View.GONE
                    step2.visibility = View.VISIBLE
                    inputNewPw.requestFocus()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        // ── Step 2: Set new password ─────────────────────────────────────────
        btnReset.setOnClickListener {
            val newPw  = inputNewPw.text.toString()
            val confPw = inputConfPw.text.toString()

            when {
                newPw.length < 6 -> {
                    Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newPw != confPw -> {
                    Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            btnReset.isEnabled = false
            btnReset.text = "Saving…"

            ApiClient.resetForgotPassword(verifiedEmail, newPw) { ok, msg ->
                btnReset.isEnabled = true
                btnReset.text = "Reset Password"
                if (ok) {
                    Toast.makeText(this, "✅ Password reset! Please log in.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
