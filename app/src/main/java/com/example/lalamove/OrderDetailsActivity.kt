package com.example.lalamove

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class OrderDetailsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var savedItemTitle = ""
    private var savedItemSubtitle = ""
    private var originalFareAmount: Double = 0.0
    private var currentFareAmount: Double = 0.0
    private var stopsList = mutableListOf<String>()

    // Holds the EditText inside the contact bottom sheet so the contact picker can fill it
    private var contactSheetInput: EditText? = null

    private val contactPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            var cursor: Cursor? = null
            try {
                cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(uri.lastPathSegment),
                    null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val raw = cursor.getString(0).replace("[^0-9]".toRegex(), "")
                    // Normalise to 10-digit PH local number
                    val local = when {
                        raw.startsWith("63") && raw.length >= 12 -> raw.substring(2)
                        raw.startsWith("0") && raw.length >= 11  -> raw.substring(1)
                        else -> raw
                    }.take(10)
                    contactSheetInput?.setText(formatPhoneDisplay(local))
                    contactSheetInput?.setSelection(contactSheetInput?.text?.length ?: 0)
                }
            } finally {
                cursor?.close()
            }
        }
    }

    private var appliedCouponCode = ""

    private val couponLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            appliedCouponCode = data.getStringExtra("COUPON_CODE") ?: ""
            val type     = data.getStringExtra("COUPON_TYPE") ?: "fixed"
            val value    = data.getDoubleExtra("COUPON_VALUE", 0.0)
            var discount = data.getDoubleExtra("COUPON_DISCOUNT", 0.0)
            if (discount == 0.0 && value > 0.0) {
                discount = if (type == "percentage") originalFareAmount * (value / 100.0) else value
            }
            currentFareAmount = maxOf(0.0, originalFareAmount - discount)
            val textOrderTotal = findViewById<TextView>(R.id.textOrderTotal)
            val textCoupon = findViewById<TextView>(R.id.textCoupon)
            textOrderTotal.text = "₱${String.format("%.2f", currentFareAmount)}"
            textCoupon.text = "$appliedCouponCode applied!"
            textCoupon.setTextColor(android.graphics.Color.parseColor("#FF6B00"))
        }
    }

    private val itemDetailsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val title = data?.getStringExtra("ITEM_TITLE")
            val subtitle = data?.getStringExtra("ITEM_SUBTITLE")

            val optItemDetailsView = findViewById<android.view.View>(R.id.optItemDetails)
            val titleView = optItemDetailsView.findViewById<TextView>(R.id.optTitle)
            val subtitleView = optItemDetailsView.findViewById<TextView>(R.id.optSubtitle)
            val iconView = optItemDetailsView.findViewById<ImageView>(R.id.optIcon)

            if (title != null) {
                savedItemTitle = title
                titleView.text = title
                titleView.setTextColor(android.graphics.Color.parseColor("#222222"))
                iconView.setColorFilter(android.graphics.Color.parseColor("#FF6B00"))
            }
            if (subtitle != null) {
                savedItemSubtitle = subtitle
                subtitleView.text = subtitle
                subtitleView.visibility = android.view.View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_details)

        firestore = FirebaseFirestore.getInstance()

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnReviewOrder = findViewById<MaterialButton>(R.id.btnReviewOrder)
        val textOrderTotal = findViewById<TextView>(R.id.textOrderTotal)
        val textContactNumber = findViewById<TextView>(R.id.textContactNumber)
        val optContactLayout = findViewById<View>(R.id.optContactLayout)
        val optPaymentLayout = findViewById<View>(R.id.optPaymentLayout)
        val textPaymentMethod = findViewById<TextView>(R.id.textPaymentMethod)
        val textPaymentPayer = findViewById<TextView>(R.id.textPaymentPayer)
        val optIconCash = findViewById<ImageView>(R.id.optIconCash)
        val optCouponLayout = findViewById<View>(R.id.optCouponLayout)
        val textCoupon = findViewById<TextView>(R.id.textCoupon)

        val total = intent.getStringExtra("TOTAL_PRICE") ?: "₱0.00"
        val vehicleType = intent.getStringExtra("VEHICLE_TYPE") ?: "motorcycle"
        val addServices = intent.getStringExtra("ADD_SERVICES") ?: ""
        val pickup = intent.getStringExtra("PICKUP") ?: ""
        val dropoff = intent.getStringExtra("DROPOFF") ?: ""
        val distanceKm = intent.getDoubleExtra("DISTANCE_KM", 0.0)

        if (dropoff.isNotEmpty()) stopsList.add(dropoff)

        originalFareAmount = total.replace("₱", "").replace(",", "").toDoubleOrNull() ?: 0.0
        currentFareAmount = originalFareAmount

        textOrderTotal.text = total

        val userId = SessionManager.getUserId(this)

        firestore.collection("accounts").document(userId).get()
            .addOnSuccessListener { doc ->
                val phone = doc.getString("Acct_Phone")
                if (phone != null) textContactNumber.text = phone
            }

        optContactLayout.setOnClickListener {
            showContactBottomSheet(textContactNumber)
        }

        optPaymentLayout.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_method, null)
            val rgPaymentType = dialogView.findViewById<RadioGroup>(R.id.rgPaymentType)
            val rgPaidBy = dialogView.findViewById<RadioGroup>(R.id.rgPaidBy)
            if (textPaymentMethod.text == "Wallet") dialogView.findViewById<RadioButton>(R.id.rbWallet).isChecked = true
            if (textPaymentPayer.text == "Paid by receiver") dialogView.findViewById<RadioButton>(R.id.rbReceiver).isChecked = true
            AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Confirm") { _, _ ->
                    val isCash = rgPaymentType.checkedRadioButtonId == R.id.rbCash
                    val isSender = rgPaidBy.checkedRadioButtonId == R.id.rbSender
                    textPaymentMethod.text = if (isCash) "Cash" else "Wallet"
                    textPaymentPayer.text = if (isSender) "Paid by sender" else "Paid by receiver"
                    if (!isCash) {
                        optIconCash.setImageResource(R.drawable.ic_coupon)
                    } else {
                        optIconCash.setImageResource(R.drawable.ic_cash)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        optCouponLayout.setOnClickListener {
            val intent = Intent(this, CouponsActivity::class.java)
            intent.putExtra("FARE", originalFareAmount)
            couponLauncher.launch(intent)
        }

        btnBack.setOnClickListener { finish() }

        findViewById<android.view.View>(R.id.optItemDetails).setOnClickListener {
            itemDetailsLauncher.launch(Intent(this, DeliveryItemDetailsActivity::class.java))
        }

        val notesHeader = findViewById<View>(R.id.notesHeader)
        val notesExpandableArea = findViewById<View>(R.id.notesExpandableArea)
        val inputDriverNotes = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputDriverNotes)
        val textCharCount = findViewById<TextView>(R.id.textCharCount)

        notesHeader.setOnClickListener {
            if (notesExpandableArea.visibility == View.GONE) {
                notesExpandableArea.visibility = View.VISIBLE
                notesExpandableArea.alpha = 0f
                notesExpandableArea.animate().alpha(1f).setDuration(300).start()
            } else {
                notesExpandableArea.animate().alpha(0f).setDuration(300).withEndAction {
                    notesExpandableArea.visibility = View.GONE
                }.start()
            }
        }

        inputDriverNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textCharCount.text = "${500 - (s?.length ?: 0)} characters left"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnReviewOrder.setOnClickListener {
            btnReviewOrder.isEnabled = false
            btnReviewOrder.text = "Placing order..."

            val bottomSheetDialog = BottomSheetDialog(this)
            val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_review_order, null)

            sheetView.findViewById<TextView>(R.id.reviewPickup).text = pickup
            sheetView.findViewById<TextView>(R.id.reviewDropoff).text = dropoff
            sheetView.findViewById<TextView>(R.id.reviewVehicle).text = "Delivery • ${vehicleType.replaceFirstChar { it.uppercase() }}"

            if (distanceKm > 0) {
                val tvDist = sheetView.findViewById<TextView>(R.id.reviewDistance)
                tvDist?.text = "Total Distance: %.1f km".format(distanceKm)
                tvDist?.visibility = View.VISIBLE
            }

            val tvServices = sheetView.findViewById<TextView>(R.id.reviewServices)
            if (addServices.isNotEmpty()) {
                tvServices.text = addServices
                tvServices.visibility = View.VISIBLE
            } else {
                tvServices.visibility = View.GONE
            }

            val isCash = textPaymentMethod.text.toString() == "Cash"
            sheetView.findViewById<TextView>(R.id.reviewPaymentText).text = "Paid by ${if (isCash) "cash" else "wallet"}"
            sheetView.findViewById<ImageView>(R.id.reviewPaymentIcon).setImageResource(if (isCash) R.drawable.ic_cash else R.drawable.business_tag_bg)
            sheetView.findViewById<TextView>(R.id.reviewTotalFare).text = total

            val btnPlaceOrderFinal = sheetView.findViewById<MaterialButton>(R.id.btnPlaceOrderFinal)
            btnPlaceOrderFinal.setOnClickListener {
                btnPlaceOrderFinal.isEnabled = false
                btnPlaceOrderFinal.text = "Placing order..."

                val driverNotes = inputDriverNotes.text.toString().trim()
                val paymentMethod = textPaymentMethod.text.toString()
                val paidBy = textPaymentPayer.text.toString()
                val couponCode = appliedCouponCode
                val contactPhone = textContactNumber.text.toString()

                // Use Firestore auto-generated document ID — no counter document needed.
                // The MySQL Book_ID is assigned by the website's sync job after it lands in MySQL.
                val bookingRef = firestore.collection("booking").document()
                val orderId    = bookingRef.id

                val bookingData = hashMapOf<String, Any?>(
                    "Book_FirebaseDocID" to orderId,
                    "Book_CustID"        to userId,
                    "Book_DrvrID"        to null,
                    "Book_VhclTypeID"    to vehicleType,
                    "Book_TotalFare"     to currentFareAmount,
                    "Book_Status"        to "pending",
                    "Book_CreatedAt"     to FieldValue.serverTimestamp(),
                    "Book_Pickuploc"     to pickup,
                    "Book_Dropoffloc"    to dropoff,
                    "Book_Notes"         to driverNotes,
                    "Book_AddServices"   to addServices,
                    "Book_IsRated"       to false,
                    "Book_RatingGiven"   to null,
                    "Book_PaymentMethod" to paymentMethod,
                    "Book_PaidBy"        to paidBy,
                    "Book_CouponCode"    to couponCode,
                    "Book_ItemTitle"     to savedItemTitle,
                    "Book_ItemSubtitle"  to savedItemSubtitle,
                    "Book_ContactPhone"  to contactPhone,
                    "Book_Feedback"      to null,
                    "Book_Distance"      to distanceKm
                )
                bookingRef.set(bookingData)
                    .addOnSuccessListener {
                        if (paymentMethod == "Wallet") {
                            val uid  = SessionManager.getUserId(this@OrderDetailsActivity)
                            val role = SessionManager.getRole(this@OrderDetailsActivity) ?: "customer"
                            ApiClient.walletDeduct(uid, role, currentFareAmount, "Payment for order $orderId") { _, _, _ -> }
                        }
                        stopsList.forEachIndexed { index, stopAddress ->
                            val deliveryData = hashMapOf<String, Any?>(
                                "Dlvr_BookID"          to orderId,
                                "Dlvr_StopNumber"      to index + 1,
                                "Dlvr_Address"         to stopAddress,
                                "Dlvr_ContactName"     to null,
                                "Dlvr_ContactPhone"    to contactPhone,
                                "Dlvr_Status"          to "pending",
                                "Dlvr_ProofOfDelivery" to null
                            )
                            firestore.collection("delivery").add(deliveryData)
                                .addOnSuccessListener { dlvrRef ->
                                    val itemData = hashMapOf<String, Any?>(
                                        "Item_DlvrID"   to dlvrRef.id,
                                        "Item_Name"     to if (index == 0) savedItemTitle else "Package",
                                        "Item_Category" to "Delivery",
                                        "Item_Quantity" to 1,
                                        "Item_Size"     to "Small",
                                        "Item_WeightKG" to 0.0,
                                        "Item_Photo"    to null
                                    )
                                    firestore.collection("item").add(itemData)
                                }
                        }
                        val userName = SessionManager.getName(this@OrderDetailsActivity).ifEmpty { "User" }
                        val findIntent = Intent(this@OrderDetailsActivity, FindingDriverActivity::class.java).apply {
                            putExtra("ORDER_ID",       orderId)
                            putExtra("PICKUP",         pickup)
                            putExtra("DROPOFF",        dropoff)
                            putExtra("TOTAL_FARE",     total)
                            putExtra("VEHICLE_TYPE",   vehicleType)
                            putExtra("PAYMENT_METHOD", paymentMethod)
                            putExtra("CONTACT_NUMBER", contactPhone)
                            putExtra("DRIVER_NOTES",   driverNotes)
                            putExtra("ITEM_DETAILS",   "$paymentMethod • $total")
                            putExtra("USER_NAME",      userName)
                        }
                        bottomSheetDialog.dismiss()
                        startActivity(findIntent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        btnPlaceOrderFinal.isEnabled = true
                        btnPlaceOrderFinal.text = "Place Order"
                        android.util.Log.e("OrderDetails", "Booking write failed", e)
                        AlertDialog.Builder(this@OrderDetailsActivity)
                            .setTitle("Booking Failed")
                            .setMessage(e.message ?: "Unknown error. Please try again.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
            }

            bottomSheetDialog.setContentView(sheetView)
            bottomSheetDialog.show()
        }
    }

    // ── Contact bottom sheet ───────────────────────────────────────────────────

    private fun showContactBottomSheet(textContactNumber: TextView) {
        val sheet = BottomSheetDialog(this)
        val sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_contact_number, null)

        val inputPhone   = sheetView.findViewById<EditText>(R.id.inputPhone)
        val btnClear     = sheetView.findViewById<ImageButton>(R.id.btnClearPhone)
        val btnContacts  = sheetView.findViewById<ImageButton>(R.id.btnPickContact)
        val btnUpdate    = sheetView.findViewById<MaterialButton>(R.id.btnUpdatePhone)

        contactSheetInput = inputPhone

        // Pre-fill: strip "+63 " or "+63" prefix then format
        val current = textContactNumber.text.toString()
        val digits = current.replace("[^0-9]".toRegex(), "")
        val local = when {
            digits.startsWith("63") && digits.length > 2 -> digits.substring(2)
            digits.startsWith("0")  && digits.length > 1 -> digits.substring(1)
            else -> digits
        }.take(10)
        inputPhone.setText(formatPhoneDisplay(local))
        inputPhone.setSelection(inputPhone.text.length)

        // Show/hide clear button based on content
        btnClear.visibility = if (local.isNotEmpty()) View.VISIBLE else View.GONE

        // Auto-format as user types + toggle clear icon
        var isFormatting = false
        inputPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val raw = e.toString().replace("[^0-9]".toRegex(), "").take(10)
                val formatted = formatPhoneDisplay(raw)
                inputPhone.setText(formatted)
                inputPhone.setSelection(formatted.length)
                btnClear.visibility = if (raw.isNotEmpty()) View.VISIBLE else View.GONE
                isFormatting = false
            }
        })

        btnClear.setOnClickListener {
            inputPhone.setText("")
            btnClear.visibility = View.GONE
        }

        btnContacts.setOnClickListener {
            contactPickerLauncher.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            )
        }

        btnUpdate.setOnClickListener {
            val digits10 = inputPhone.text.toString().replace("[^0-9]".toRegex(), "")
            if (digits10.length == 10) {
                textContactNumber.text = "+63 $digits10"
                sheet.dismiss()
            } else {
                inputPhone.error = "Enter a 10-digit mobile number"
            }
        }

        sheet.setContentView(sheetView)
        sheet.show()
    }

    /** Formats a string of up to 10 digits as "XXX XXX XXXX" */
    private fun formatPhoneDisplay(digits: String): String {
        val d = digits.take(10)
        return when {
            d.length > 6 -> "${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}"
            d.length > 3 -> "${d.substring(0, 3)} ${d.substring(3)}"
            else -> d
        }
    }
}
