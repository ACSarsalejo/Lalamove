package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.text.TextWatcher
import android.text.Editable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val rootLayout    = findViewById<View>(R.id.loginRootLayout)
        val inputUsername = findViewById<TextInputEditText>(R.id.inputUsername)
        val inputPassword = findViewById<TextInputEditText>(R.id.inputPassword)
        val inputUsernameLayout = findViewById<TextInputLayout>(R.id.inputUsernameLayout)
        val btnLogin      = findViewById<Button>(R.id.btnLogin)
        val forgotPassword = findViewById<TextView>(R.id.forgotPassword)
        val signUpLink    = findViewById<TextView>(R.id.signUpLink)
        val lastUsedHint = findViewById<TextView>(R.id.lastUsedHint)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        // Back button
        btnBack.setOnClickListener { finish() }

        // Smart detection: morph based on input type (email vs phone)
        inputUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString()?.trim() ?: ""
                if (input.isEmpty()) {
                    inputUsernameLayout.hint = "Mobile number or Email"
                } else if (input.contains("@")) {
                    inputUsernameLayout.hint = "Email Address"
                } else if (input.all { it.isDigit() }) {
                    inputUsernameLayout.hint = "Mobile Number"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Show welcome banner if coming from sign-up
        val signupSuccess = intent.getBooleanExtra("SIGNUP_SUCCESS", false)
        val userName = intent.getStringExtra("USER_NAME") ?: ""
        if (signupSuccess) {
            rootLayout.postDelayed({
                NotificationHelper.showBanner(rootLayout as ViewGroup, "Welcome, $userName! Registration successful.", true)
            }, 500)
        }

        btnLogin.setOnClickListener {
            val email    = inputUsername.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showSnackbar(rootLayout, "Please enter your email/phone and password.", success = false)
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Logging in…"

            ApiClient.login(email, password) { result, error ->
                if (result != null) {
                    if (result.role == "admin") {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log In"
                        NotificationHelper.showBanner(rootLayout as ViewGroup, "Admin accounts must use the web portal.", false)
                        return@login
                    }

                    SessionManager.save(this, result)

                    // Upsert Firestore document so website-registered users are visible to the app
                    val firestore = FirebaseFirestore.getInstance()
                    val nameParts = result.name.split(" ", limit = 2)
                    val firstName = nameParts[0]
                    val lastName  = if (nameParts.size > 1) nameParts[1] else ""
                    val collection = if (result.role == "driver") "driver" else "customer"
                    val docData: Map<String, Any?> = if (result.role == "driver") mapOf(
                        "Drvr_ID"        to result.userId,
                        "Drvr_AcctId"    to result.acctId,
                        "Drvr_FirstName" to firstName,
                        "Drvr_LastName"  to lastName,
                        "Drvr_PhoneNum"  to result.phone,
                        "Drvr_IsVerified" to result.isVerified
                    ) else mapOf(
                        "Cust_ID"        to result.userId,
                        "Cust_AcctId"    to result.acctId,
                        "Cust_FirstName" to firstName,
                        "Cust_LastName"  to lastName,
                        "Cust_Phone"     to result.phone,
                        "Cust_Email"     to result.email,
                        "Cust_IsBanned"  to false
                    )
                    firestore.collection(collection)
                        .document(result.userId.toString())
                        .set(docData, SetOptions.merge())

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
                    btnLogin.isEnabled = true
                    btnLogin.text = "Log In"
                    NotificationHelper.showBanner(rootLayout as ViewGroup, error, false)
                }
            }
        }

        forgotPassword.setOnClickListener {
            Toast.makeText(this, "Password recovery coming soon!", Toast.LENGTH_SHORT).show()
        }

        signUpLink.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
        }
    }

    private fun showSnackbar(view: View, message: String, success: Boolean) {
        val snack = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snack.setBackgroundTint(
            if (success) getColor(R.color.orange_main)
            else getColor(android.R.color.holo_red_dark)
        )
        snack.setTextColor(getColor(android.R.color.white))
        snack.show()
    }
}
