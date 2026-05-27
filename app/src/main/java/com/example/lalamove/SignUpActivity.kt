package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        val signupRootLayout       = findViewById<ViewGroup>(R.id.signupRootLayout)
        val btnBack                = findViewById<ImageView>(R.id.btnBack)
        val btnSignUp              = findViewById<Button>(R.id.btnSignUp)
        val loginLink              = findViewById<TextView>(R.id.loginLink)

        val inputDisplayNameLayout = findViewById<TextInputLayout>(R.id.inputDisplayNameLayout)
        val inputDisplayName       = findViewById<TextInputEditText>(R.id.inputDisplayName)
        val inputPhoneLayout       = findViewById<TextInputLayout>(R.id.inputPhoneLayout)
        val inputPhone             = findViewById<TextInputEditText>(R.id.inputPhone)
        val inputEmailLayout       = findViewById<TextInputLayout>(R.id.inputEmailLayout)
        val inputEmail             = findViewById<TextInputEditText>(R.id.inputEmail)
        val inputPasswordLayout    = findViewById<TextInputLayout>(R.id.inputPasswordLayout)
        val inputPassword          = findViewById<TextInputEditText>(R.id.inputPassword)

        btnBack.setOnClickListener { finish() }
        loginLink.setOnClickListener { navigateToLogin() }

        btnSignUp.setOnClickListener {
            val name     = inputDisplayName.text.toString().trim()
            val phone    = inputPhone.text.toString().trim()
            val email    = inputEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (!validate(name, phone, email, password,
                    inputDisplayNameLayout, inputPhoneLayout, inputEmailLayout, inputPasswordLayout)) return@setOnClickListener

            NotificationHelper.showBanner(signupRootLayout, "Creating account...", true)
            btnSignUp.isEnabled = false

            val parts     = name.split(" ", limit = 2)
            val firstName = parts[0]
            val lastName  = if (parts.size > 1) parts[1] else ""

            // Firebase Auth creates the account; Firestore stores the profile
            ApiClient.register(firstName, lastName, phone, email, password) { success, uid, error ->
                if (success) {
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("SIGNUP_SUCCESS", true)
                    intent.putExtra("USER_NAME", firstName)
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
                    finish()
                } else {
                    btnSignUp.isEnabled = true
                    NotificationHelper.showBanner(signupRootLayout, error.ifEmpty { "Registration failed." }, false)
                }
            }
        }
    }

    private fun validate(name: String, phone: String, email: String, password: String,
                         nl: TextInputLayout, pl: TextInputLayout, el: TextInputLayout, psl: TextInputLayout): Boolean {
        var v = true
        if (name.isEmpty())      { nl.error  = "Required";    v = false } else nl.error  = null
        if (phone.isEmpty())     { pl.error  = "Required";    v = false } else pl.error  = null
        if (email.isEmpty())     { el.error  = "Required";    v = false } else el.error  = null
        if (password.length < 6) { psl.error = "Min 6 chars"; v = false } else psl.error = null
        return v
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in_activity, R.anim.fade_out_activity)
        finish()
    }
}
