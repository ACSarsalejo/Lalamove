package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class DriverSignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_sign_up)

        val driverSignupRootLayout = findViewById<ViewGroup>(R.id.driverSignupRootLayout)
        val inputFirstNameLayout   = findViewById<TextInputLayout>(R.id.inputFirstNameLayout)
        val inputFirstName         = findViewById<TextInputEditText>(R.id.inputFirstName)
        val inputLastNameLayout    = findViewById<TextInputLayout>(R.id.inputLastNameLayout)
        val inputLastName          = findViewById<TextInputEditText>(R.id.inputLastName)
        val inputPhoneLayout       = findViewById<TextInputLayout>(R.id.inputPhoneLayout)
        val inputPhone             = findViewById<TextInputEditText>(R.id.inputPhone)
        val inputEmailLayout       = findViewById<TextInputLayout>(R.id.inputEmailLayout)
        val inputEmail             = findViewById<TextInputEditText>(R.id.inputEmail)
        val inputPasswordLayout    = findViewById<TextInputLayout>(R.id.inputPasswordLayout)
        val inputPassword          = findViewById<TextInputEditText>(R.id.inputPassword)
        val vehicleTypeAutoComplete = findViewById<AutoCompleteTextView>(R.id.vehicleTypeAutoComplete)
        val serviceAreaAutoComplete = findViewById<AutoCompleteTextView>(R.id.serviceAreaAutoComplete)
        val btnSubmitApplication   = findViewById<Button>(R.id.btnSubmitApplication)
        val loginLink              = findViewById<TextView>(R.id.loginLink)

        val vehicleOptions = arrayOf("motorcycle", "sedan", "suv_small", "suv_large", "van", "truck")
        val vehicleLabels  = arrayOf("Motorcycle / Bike", "200 kg Sedan", "300 kg Subcompact SUV", "600 kg 7-seater SUV", "1000 kg Closed Van", "2000 kg Large Truck")
        vehicleTypeAutoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, vehicleLabels))

        val areaLabels = arrayOf("Cebu Islandwide")
        serviceAreaAutoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, areaLabels))
        serviceAreaAutoComplete.setText("Cebu Islandwide", false)

        btnSubmitApplication.setOnClickListener {
            val fn = inputFirstName.text.toString().trim()
            val ln = inputLastName.text.toString().trim()
            val ph = inputPhone.text.toString().trim()
            val em = inputEmail.text.toString().trim()
            val pw = inputPassword.text.toString().trim()
            val selectedIndex = vehicleLabels.indexOf(vehicleTypeAutoComplete.text.toString())
            val vehicleTypeId = if (selectedIndex >= 0) vehicleOptions[selectedIndex] else "motorcycle"

            if (!validate(fn, ln, ph, em, pw,
                    inputFirstNameLayout, inputLastNameLayout, inputPhoneLayout, inputEmailLayout, inputPasswordLayout)) return@setOnClickListener

            NotificationHelper.showBanner(driverSignupRootLayout, "Submitting application...", true)
            btnSubmitApplication.isEnabled = false

            // Firebase Auth creates the account; ApiClient.registerDriver writes the Firestore driver doc
            ApiClient.registerDriver(fn, ln, ph, em, pw, vehicleTypeId) { success, uid, error ->
                if (success) {
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("SIGNUP_SUCCESS", true)
                    intent.putExtra("USER_NAME", fn)
                    startActivity(intent)
                    finish()
                } else {
                    btnSubmitApplication.isEnabled = true
                    NotificationHelper.showBanner(driverSignupRootLayout, error.ifEmpty { "Registration failed." }, false)
                }
            }
        }

        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun validate(fn: String, ln: String, ph: String, em: String, pw: String,
                         fl: TextInputLayout, ll: TextInputLayout, pl: TextInputLayout,
                         el: TextInputLayout, psl: TextInputLayout): Boolean {
        var v = true
        if (fn.isEmpty())    { fl.error  = "Required";    v = false } else fl.error  = null
        if (ln.isEmpty())    { ll.error  = "Required";    v = false } else ll.error  = null
        if (ph.isEmpty())    { pl.error  = "Required";    v = false } else pl.error  = null
        if (em.isEmpty())    { el.error  = "Required";    v = false } else el.error  = null
        if (pw.length < 6)   { psl.error = "Min 6 chars"; v = false } else psl.error = null
        return v
    }
}
