package com.example.lalamove

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val acctId get() = SessionManager.getAcctId(this)
    private val role   get() = SessionManager.getRole(this) ?: "customer"

    private val photoPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@registerForActivityResult
            val mime = contentResolver.getType(uri) ?: "image/jpeg"

            ApiClient.updatePhoto(acctId, role, bytes, mime) { success, urlOrError ->
                if (success) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    findViewById<ImageView>(R.id.profilePhoto).setImageBitmap(bmp)
                    findViewById<TextView>(R.id.profileInitials).visibility = android.view.View.GONE
                    NotificationHelper.showBanner(findViewById(R.id.settingsRoot), "Photo updated!", true)
                } else {
                    NotificationHelper.showBanner(findViewById(R.id.settingsRoot), urlOrError, false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("ll_notif_prefs", MODE_PRIVATE)

        val root              = findViewById<ViewGroup>(R.id.settingsRoot)
        val btnBack           = findViewById<ImageView>(R.id.btnBack)
        val profileInitials   = findViewById<TextView>(R.id.profileInitials)
        val profileName       = findViewById<TextView>(R.id.profileName)
        val profileEmail      = findViewById<TextView>(R.id.profileEmail)
        val btnChangePhoto    = findViewById<ImageView>(R.id.btnChangePhoto)
        val inputFirstName    = findViewById<TextInputEditText>(R.id.inputFirstName)
        val inputLastName     = findViewById<TextInputEditText>(R.id.inputLastName)
        val inputPhone        = findViewById<TextInputEditText>(R.id.inputPhone)
        val inputEmail        = findViewById<TextInputEditText>(R.id.inputEmail)
        val inputFNLayout     = findViewById<TextInputLayout>(R.id.inputFirstNameLayout)
        val inputLNLayout     = findViewById<TextInputLayout>(R.id.inputLastNameLayout)
        val btnSaveProfile    = findViewById<MaterialButton>(R.id.btnSaveProfile)
        val inputCurrentPw    = findViewById<TextInputEditText>(R.id.inputCurrentPw)
        val inputNewPw        = findViewById<TextInputEditText>(R.id.inputNewPw)
        val inputConfirmPw    = findViewById<TextInputEditText>(R.id.inputConfirmPw)
        val inputCurrPwLayout = findViewById<TextInputLayout>(R.id.inputCurrentPwLayout)
        val inputNewPwLayout  = findViewById<TextInputLayout>(R.id.inputNewPwLayout)
        val inputConfPwLayout = findViewById<TextInputLayout>(R.id.inputConfirmPwLayout)
        val btnChangePw       = findViewById<MaterialButton>(R.id.btnChangePassword)
        val switchOrderUpdates = findViewById<SwitchMaterial>(R.id.switchOrderUpdates)
        val switchDriverAlerts = findViewById<SwitchMaterial>(R.id.switchDriverAlerts)
        val switchPromos       = findViewById<SwitchMaterial>(R.id.switchPromos)

        btnBack.setOnClickListener { finish() }

        // ── Load saved notification prefs ──
        switchOrderUpdates.isChecked = prefs.getBoolean("order_updates", true)
        switchDriverAlerts.isChecked = prefs.getBoolean("driver_alerts", true)
        switchPromos.isChecked       = prefs.getBoolean("promos", false)

        listOf(switchOrderUpdates, switchDriverAlerts, switchPromos).forEach { sw ->
            sw.setOnCheckedChangeListener { _, _ ->
                prefs.edit()
                    .putBoolean("order_updates", switchOrderUpdates.isChecked)
                    .putBoolean("driver_alerts", switchDriverAlerts.isChecked)
                    .putBoolean("promos",        switchPromos.isChecked)
                    .apply()
            }
        }

        // ── Load profile from API ──
        val name  = SessionManager.getName(this)
        val email = SessionManager.getEmail(this)
        profileName.text  = name
        profileEmail.text = email
        val parts = name.split(" ", limit = 2)
        inputFirstName.setText(parts.getOrElse(0) { "" })
        inputLastName.setText(parts.getOrElse(1) { "" })
        inputPhone.setText(SessionManager.getPhone(this))
        inputEmail.setText(email)
        val initials = parts.mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
        profileInitials.text = initials.ifEmpty { "?" }

        ApiClient.getProfile(acctId, role) { json ->
            if (json != null) {
                inputFirstName.setText(json.optString("first_name"))
                inputLastName.setText(json.optString("last_name"))
                inputPhone.setText(json.optString("phone"))
                inputEmail.setText(json.optString("email"))
                val fn = json.optString("first_name"); val ln = json.optString("last_name")
                profileName.text = "$fn $ln".trim()
                profileEmail.text = json.optString("email")
                profileInitials.text = listOf(fn, ln).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").ifEmpty { "?" }
            }
        }

        // ── Photo picker ──
        btnChangePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            photoPicker.launch(intent)
        }

        // ── Save profile ──
        btnSaveProfile.setOnClickListener {
            val fn = inputFirstName.text.toString().trim()
            val ln = inputLastName.text.toString().trim()
            val ph = inputPhone.text.toString().trim()
            val em = inputEmail.text.toString().trim()
            var valid = true
            if (fn.isEmpty()) { inputFNLayout.error = "Required"; valid = false } else inputFNLayout.error = null
            if (ph.isEmpty()) { inputLNLayout.error = null; valid = valid } // last name optional
            if (em.isEmpty()) { inputFNLayout.error = null; valid = valid }
            if (!valid) return@setOnClickListener

            btnSaveProfile.isEnabled = false
            ApiClient.updateProfile(acctId, role, fn, ln, ph, em) { success, error ->
                btnSaveProfile.isEnabled = true
                if (success) {
                    // Update session cache
                    val updated = LoginResult(
                        role = SessionManager.getRole(this) ?: "customer",
                        userId = SessionManager.getUserId(this),
                        name = "$fn $ln".trim(),
                        email = em,
                        phone = ph,
                        isVerified = true
                    )
                    SessionManager.save(this, updated)
                    profileName.text = updated.name
                    profileEmail.text = em
                    NotificationHelper.showBanner(root, "Profile updated!", true)
                } else {
                    NotificationHelper.showBanner(root, error, false)
                }
            }
        }

        // ── Change password ──
        btnChangePw.setOnClickListener {
            val curr = inputCurrentPw.text.toString()
            val newp = inputNewPw.text.toString()
            val conf = inputConfirmPw.text.toString()
            var valid = true
            if (curr.isEmpty()) { inputCurrPwLayout.error = "Required"; valid = false } else inputCurrPwLayout.error = null
            if (newp.length < 6) { inputNewPwLayout.error = "Min 6 chars"; valid = false } else inputNewPwLayout.error = null
            if (newp != conf) { inputConfPwLayout.error = "Passwords don't match"; valid = false } else inputConfPwLayout.error = null
            if (!valid) return@setOnClickListener

            btnChangePw.isEnabled = false
            ApiClient.changePassword(acctId, curr, newp) { success, error ->
                btnChangePw.isEnabled = true
                if (success) {
                    inputCurrentPw.text?.clear()
                    inputNewPw.text?.clear()
                    inputConfirmPw.text?.clear()
                    NotificationHelper.showBanner(root, "Password updated successfully!", true)
                } else {
                    NotificationHelper.showBanner(root, error, false)
                }
            }
        }
    }
}
