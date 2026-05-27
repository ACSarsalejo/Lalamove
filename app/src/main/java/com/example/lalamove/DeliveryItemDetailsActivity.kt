package com.example.lalamove

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class DeliveryItemDetailsActivity : AppCompatActivity() {

    private var packageCount = 0

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                // Display the image
                val imagePhotoPreview = findViewById<ImageView>(R.id.imagePhotoPreview)
                imagePhotoPreview.setImageURI(uri)
                imagePhotoPreview.visibility = View.VISIBLE

                // Display the file name
                val textFileName = findViewById<TextView>(R.id.textFileName)
                textFileName.text = getFileName(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery_item_details)

        // Dropdowns setup
        val sizeOptions = arrayOf("Small", "Medium", "Large")
        val dropdownSize = findViewById<AutoCompleteTextView>(R.id.dropdownSize)
        dropdownSize.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, sizeOptions))

        val weightOptions = arrayOf("<5 kg", "5-20 kg", ">20 kg")
        val dropdownWeight = findViewById<AutoCompleteTextView>(R.id.dropdownWeight)
        dropdownWeight.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, weightOptions))

        // Basic components
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveItemDetails)
        val btnMinus = findViewById<ImageView>(R.id.btnMinus)
        val btnPlus = findViewById<ImageView>(R.id.btnPlus)
        val textPackageCount = findViewById<TextView>(R.id.textPackageCount)
        val btnChoosePhoto = findViewById<MaterialCardView>(R.id.btnChoosePhoto)

        btnCancel.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            // Collect Item Type
            val types = mutableListOf<String>()
            if (findViewById<CheckBox>(R.id.cbFood).isChecked) types.add("Food & beverages")
            if (findViewById<CheckBox>(R.id.cbAppliances).isChecked) types.add("Appliances / Furniture")
            if (findViewById<CheckBox>(R.id.cbOffice).isChecked) types.add("Office items / Documents")
            if (findViewById<CheckBox>(R.id.cbConstruction).isChecked) types.add("Construction materials")
            if (findViewById<CheckBox>(R.id.cbClothing).isChecked) types.add("Clothing & Accessories")
            if (findViewById<CheckBox>(R.id.cbElectronics).isChecked) types.add("Electronics and gadgets")
            if (findViewById<CheckBox>(R.id.cbOthers).isChecked) types.add("Others")
            
            val typeString = if (types.isNotEmpty()) types.joinToString(", ") else "Package"

            // Collect Handling
            val handling = mutableListOf<String>()
            if (findViewById<CheckBox>(R.id.cbNoHandling).isChecked) handling.add("No special handling")
            if (findViewById<CheckBox>(R.id.cbFragile).isChecked) handling.add("Fragile")
            if (findViewById<CheckBox>(R.id.cbPerishable).isChecked) handling.add("Perishable")
            if (findViewById<CheckBox>(R.id.cbTemperature).isChecked) handling.add("Temperature sensitive")
            if (findViewById<CheckBox>(R.id.cbKeepDry).isChecked) handling.add("Keep dry")

            val handlingString = if (handling.isNotEmpty()) handling.joinToString(", ") else "No special handling"

            // Collect Dropdowns
            val sizeStr = dropdownSize.text.toString().takeIf { it != "Select size" } ?: "Small"
            val weightStr = dropdownWeight.text.toString().takeIf { it != "Select weight (kg)" } ?: "<5 kg"
            
            val subtitleStr = "$packageCount package${if(packageCount>1)"s" else ""} . $weightStr . $sizeStr"

            val returnIntent = Intent().apply {
                putExtra("ITEM_TITLE", "$typeString . $handlingString")
                putExtra("ITEM_SUBTITLE", subtitleStr)
            }
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }

        btnMinus.setOnClickListener {
            if (packageCount > 0) {
                packageCount--
                textPackageCount.text = packageCount.toString()
                updateSaveButtonState(btnSave)
            }
        }

        btnPlus.setOnClickListener {
            packageCount++
            textPackageCount.text = packageCount.toString()
            updateSaveButtonState(btnSave)
        }

        // Photo Picker
        btnChoosePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickPhotoLauncher.launch(intent)
        }
    }

    private fun updateSaveButtonState(btnSave: MaterialButton) {
        if (packageCount > 0) {
            btnSave.setTextColor(getColor(android.R.color.white))
            btnSave.setBackgroundColor(getColor(R.color.orange_main))
        } else {
            btnSave.setTextColor(getColor(R.color.gray_dark))
            btnSave.setBackgroundColor(getColor(R.color.gray_light))
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "file_selected"
    }
}
