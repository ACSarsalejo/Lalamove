package com.example.lalamove

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale

class WalletActivity : AppCompatActivity() {

    private lateinit var textBalance: TextView
    private lateinit var layoutTransactions: LinearLayout
    private lateinit var textNoTransactions: TextView

    private var userId = 0L
    private var role = "customer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        userId = SessionManager.getUserId(this)
        role   = SessionManager.getRole(this) ?: "customer"

        textBalance        = findViewById(R.id.textWalletBalance)
        layoutTransactions = findViewById(R.id.layoutTransactions)
        textNoTransactions = findViewById(R.id.textNoTransactions)

        findViewById<View>(R.id.btnBackWallet).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnTopUp).setOnClickListener { showTopUpDialog() }
        findViewById<View>(R.id.cardCoupons).setOnClickListener {
            startActivity(Intent(this, CouponsActivity::class.java))
        }

        loadBalance()
        loadTransactions()
    }

    override fun onResume() {
        super.onResume()
        loadBalance()
        loadTransactions()
    }

    private fun loadBalance() {
        ApiClient.walletBalance(userId, role) { balance ->
            textBalance.text = if (balance != null) "₱${String.format("%,.2f", balance)}" else "₱0.00"
        }
    }

    private fun loadTransactions() {
        ApiClient.walletTransactions(userId, role) { arr ->
            renderTransactions(arr)
        }
    }

    private fun renderTransactions(arr: JSONArray?) {
        // Remove all transaction rows (keep the "no transactions" view)
        val childCount = layoutTransactions.childCount
        if (childCount > 1) layoutTransactions.removeViews(1, childCount - 1)

        if (arr == null || arr.length() == 0) {
            textNoTransactions.visibility = View.VISIBLE
            return
        }
        textNoTransactions.visibility = View.GONE

        val inFmt  = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        val iconMap = mapOf(
            "topup"        to ("💳" to "#D1FAE5"),
            "payment"      to ("🛍️" to "#FEF3C7"),
            "earnings"     to ("💰" to "#D1FAE5"),
            "refund"       to ("↩️" to "#D1FAE5"),
            "deduction"    to ("➖" to "#FEF2F2"),
            "platform_fee" to ("🏷️" to "#FEF2F2")
        )
        val creditTypes = setOf("topup", "earnings", "refund")

        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val type   = t.optString("Tran_Type", "payment")
            val amount = t.optDouble("Tran_Amount", 0.0)
            val desc   = t.optString("Tran_Description", type.replaceFirstChar { it.uppercase() })
            val ref    = t.optString("Tran_ReferenceNum", "")
            val dateStr = t.optString("Tran_Date", "")

            val row = LayoutInflater.from(this).inflate(R.layout.item_transaction, layoutTransactions, false)

            val (emoji, bgHex) = iconMap[type] ?: ("📄" to "#F0F0F0")
            val iconView = row.findViewById<TextView>(R.id.txnIcon)
            iconView.text = emoji
            val ovalBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(bgHex))
            }
            iconView.background = ovalBg

            row.findViewById<TextView>(R.id.txnDesc).text = desc
            val refView = row.findViewById<TextView>(R.id.txnRef)
            if (ref.isNotEmpty()) { refView.text = "Ref: $ref"; refView.visibility = View.VISIBLE }
            else refView.visibility = View.GONE

            val isCredit = type in creditTypes
            val sign = if (isCredit) "+" else "-"
            val amtColor = if (isCredit) Color.parseColor("#059669") else Color.parseColor("#DC2626")
            val amtView = row.findViewById<TextView>(R.id.txnAmount)
            amtView.text = "$sign₱${String.format("%,.2f", amount)}"
            amtView.setTextColor(amtColor)

            val dateView = row.findViewById<TextView>(R.id.txnDate)
            dateView.text = try { outFmt.format(inFmt.parse(dateStr)!!) } catch (e: Exception) { dateStr }

            // Divider between rows
            if (i > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#F0EEEB"))
                }
                layoutTransactions.addView(divider)
            }

            layoutTransactions.addView(row)
        }
    }

    private fun showTopUpDialog() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_top_up, null)
        dialog.setContentView(view)

        var selectedMethod = ""

        val cardGcash  = view.findViewById<LinearLayout>(R.id.cardGcash)
        val cardMaya   = view.findViewById<LinearLayout>(R.id.cardMaya)
        val cardVisa   = view.findViewById<LinearLayout>(R.id.cardVisa)
        val inputAmount = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputCustomAmount)
        val btnConfirm  = view.findViewById<MaterialButton>(R.id.btnConfirmTopUp)

        val methodCards = listOf(
            Triple(cardGcash, "gcash",   "GCash"),
            Triple(cardMaya,  "paymaya", "Maya"),
            Triple(cardVisa,  "visa",    "Visa / Card"),
        )

        fun selectMethod(selected: LinearLayout, method: String) {
            methodCards.forEach { (card, _, _) ->
                card.background = resources.getDrawable(R.drawable.method_card_bg, theme)
            }
            selected.background = resources.getDrawable(R.drawable.method_card_bg_selected, theme)
            selectedMethod = method
        }

        // Default: select GCash
        selectMethod(cardGcash, "gcash")

        methodCards.forEach { (card, method, _) ->
            card.setOnClickListener { selectMethod(card, method) }
        }

        val presets = listOf(
            view.findViewById<MaterialButton>(R.id.preset100)  to 100.0,
            view.findViewById<MaterialButton>(R.id.preset250)  to 250.0,
            view.findViewById<MaterialButton>(R.id.preset500)  to 500.0,
            view.findViewById<MaterialButton>(R.id.preset1000) to 1000.0,
            view.findViewById<MaterialButton>(R.id.preset2000) to 2000.0,
            view.findViewById<MaterialButton>(R.id.preset5000) to 5000.0,
        )

        fun clearPresets() = presets.forEach { (btn, _) ->
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            btn.setTextColor(Color.parseColor("#555555"))
            btn.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        }

        presets.forEach { (btn, amount) ->
            btn.setOnClickListener {
                clearPresets()
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F15A22"))
                btn.setTextColor(Color.WHITE)
                inputAmount.setText(amount.toInt().toString())
            }
        }

        btnConfirm.setOnClickListener {
            val amount = inputAmount.text.toString().trim().toDoubleOrNull()
            if (amount == null || amount < 50 || amount > 50000) {
                Toast.makeText(this, "Amount must be between ₱50 and ₱50,000", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnConfirm.isEnabled = false
            btnConfirm.text = "Processing..."
            ApiClient.walletTopUp(userId, role, amount, selectedMethod) { success, newBalance, msg ->
                btnConfirm.isEnabled = true
                btnConfirm.text = "Add Funds"
                if (success) {
                    textBalance.text = "₱${String.format("%,.2f", newBalance)}"
                    dialog.dismiss()
                    Toast.makeText(this, "₱${String.format("%,.2f", amount)} added!", Toast.LENGTH_LONG).show()
                    loadTransactions()
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
}
