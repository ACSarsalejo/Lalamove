package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val rootLayout        = findViewById<View>(R.id.loginRootLayout)
        val inputUsername     = findViewById<TextInputEditText>(R.id.inputUsername)
        val inputPassword     = findViewById<TextInputEditText>(R.id.inputPassword)
        val inputUsernameLayout = findViewById<TextInputLayout>(R.id.inputUsernameLayout)
        val btnLogin          = findViewById<Button>(R.id.btnLogin)
        val forgotPassword    = findViewById<TextView>(R.id.forgotPassword)
        val signUpLink        = findViewById<TextView>(R.id.signUpLink)
        val btnBack           = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        inputUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.trim() ?: ""
                inputUsernameLayout.hint = when {
                    input.isEmpty()          -> "Mobile number or Email"
                    input.contains("@")      -> "Email Address"
                    input.all { it.isDigit() }-> "Mobile Number"
                    else                     -> "Mobile number or Email"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val signupSuccess = intent.getBooleanExtra("SIGNUP_SUCCESS", false)
        val userName      = intent.getStringExtra("USER_NAME") ?: ""
        if (signupSuccess) {
            rootLayout.postDelayed({
                NotificationHelper.showBanner(rootLayout as ViewGroup, "Welcome, $userName! Registration successful.", true)
            }, 500)
        }

        btnLogin.setOnClickListener {
            val email    = inputUsername.text.toString().trim()
            val password = inputPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                showSnackbar(rootLayout, "Please enter your email and password.", false)
                return@setOnClickListener
            }
            btnLogin.isEnabled = false
            btnLogin.text = "Logging in…"

            ApiClient.login(email, password) { result, error ->
                btnLogin.isEnabled = true
                btnLogin.text = "Log In"
                if (result != null) {
                    SessionManager.save(this, result)
                    val dest = if (result.role == "driver") {
                        if (result.isVerified) Intent(this, DriverDashboardActivity::class.java)
                        else Intent(this, DriverPendingActivity::class.java)
                    } else {
                        Intent(this, VehicleSelectionActivity::class.java)
                    }
                    startActivity(dest)
                    overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
                    finish()
                } else {
                    NotificationHelper.showBanner(rootLayout as ViewGroup, error, false)
                }
            }
        }

        forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
        }
        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
        }
    }

    private fun showSnackbar(view: View, message: String, success: Boolean) {
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snack.setBackgroundTint(if (success) getColor(R.color.orange_main) else getColor(android.R.color.holo_red_dark))
        snack.setTextColor(getColor(android.R.color.white))
        snack.show()
    }
}
