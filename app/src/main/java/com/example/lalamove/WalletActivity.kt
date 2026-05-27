package com.example.lalamove

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale

class WalletActivity : AppCompatActivity() {

    private lateinit var textBalance: TextView
    private lateinit var layoutTransactions: LinearLayout
    private lateinit var textNoTransactions: TextView

    private lateinit var chipAll: TextView
    private lateinit var chipEarnings: TextView
    private lateinit var chipFee: TextView
    private lateinit var chipWithdrawal: TextView

    private var userId = ""
    private var role   = "driver"

    private var allTransactions: JSONArray = JSONArray()
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        userId = SessionManager.getUserId(this)
        role   = SessionManager.getRole(this) ?: "driver"

        textBalance        = findViewById(R.id.textWalletBalance)
        layoutTransactions = findViewById(R.id.layoutTransactions)
        textNoTransactions = findViewById(R.id.textNoTransactions)

        chipAll        = findViewById(R.id.chipAll)
        chipEarnings   = findViewById(R.id.chipEarnings)
        chipFee        = findViewById(R.id.chipFee)
        chipWithdrawal = findViewById(R.id.chipWithdrawal)

        val chips = listOf(
            chipAll        to "all",
            chipEarnings   to "earnings",
            chipFee        to "platform_fee",
            chipWithdrawal to "withdrawal"
        )
        chips.forEach { (chip, filter) ->
            chip.setOnClickListener {
                currentFilter = filter
                chips.forEach { (c, _) -> setChipInactive(c) }
                setChipActive(chip)
                renderTransactions(filtered())
            }
        }

        // Coupons card not relevant for drivers — hide it
        findViewById<View>(R.id.cardCoupons)?.visibility = View.GONE

        findViewById<View>(R.id.btnBackWallet).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnTopUp).setOnClickListener { showTopUpDialog() }
        findViewById<MaterialButton>(R.id.btnWithdraw).setOnClickListener { showWithdrawDialog() }

        loadBalance()
        loadTransactions()
    }

    override fun onResume() {
        super.onResume()
        loadBalance()
        loadTransactions()
    }

    // ── Chip helpers ──────────────────────────────────────────────────────────

    private fun setChipActive(chip: TextView) {
        chip.background = resources.getDrawable(R.drawable.chip_active_bg, theme)
        chip.setTextColor(Color.WHITE)
    }

    private fun setChipInactive(chip: TextView) {
        chip.background = resources.getDrawable(R.drawable.chip_inactive_bg, theme)
        chip.setTextColor(Color.parseColor("#888888"))
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private fun loadBalance() {
        ApiClient.walletBalance(userId, role) { balance ->
            textBalance.text = if (balance != null) "₱${String.format("%,.2f", balance)}" else "₱0.00"
        }
    }

    private fun loadTransactions() {
        ApiClient.walletTransactions(userId, role) { arr ->
            allTransactions = arr ?: JSONArray()
            renderTransactions(filtered())
        }
    }

    private fun filtered(): JSONArray {
        if (currentFilter == "all") return allTransactions
        val out = JSONArray()
        for (i in 0 until allTransactions.length()) {
            val t    = allTransactions.getJSONObject(i)
            val type = t.optString("Tran_Type", "")
            val desc = t.optString("Tran_Description", "")
            val match = when (currentFilter) {
                "earnings"     -> type == "earnings"
                "platform_fee" -> type == "platform_fee"
                "withdrawal"   -> type == "deduction" && desc.contains("Withdrawal", ignoreCase = true)
                else           -> true
            }
            if (match) out.put(t)
        }
        return out
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderTransactions(arr: JSONArray) {
        val childCount = layoutTransactions.childCount
        if (childCount > 1) layoutTransactions.removeViews(1, childCount - 1)

        if (arr.length() == 0) {
            textNoTransactions.visibility = View.VISIBLE
            return
        }
        textNoTransactions.visibility = View.GONE

        val inFmt  = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        val iconMap = mapOf(
            "topup"        to ("💳" to "#1B5E20"),
            "payment"      to ("🛍️" to "#4A3000"),
            "earnings"     to ("💰" to "#1B5E20"),
            "refund"       to ("↩️" to "#1B5E20"),
            "deduction"    to ("🏦" to "#5C0000"),
            "platform_fee" to ("🏷️" to "#5C0000")
        )
        val creditTypes = setOf("topup", "earnings", "refund")

        for (i in 0 until arr.length()) {
            val t       = arr.getJSONObject(i)
            val type    = t.optString("Tran_Type", "payment")
            val amount  = t.optDouble("Tran_Amount", 0.0)
            val desc    = t.optString("Tran_Description", type.replaceFirstChar { it.uppercase() })
            val ref     = t.optString("Tran_ReferenceNum", "")
            val dateStr = t.optString("Tran_Date", "")

            val row = LayoutInflater.from(this).inflate(R.layout.item_transaction, layoutTransactions, false)

            val (emoji, bgHex) = iconMap[type] ?: ("📄" to "#2A2A2A")
            val iconView = row.findViewById<TextView>(R.id.txnIcon)
            iconView.text = emoji
            iconView.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(bgHex))
            }

            row.findViewById<TextView>(R.id.txnDesc).text = desc
            val refView = row.findViewById<TextView>(R.id.txnRef)
            if (ref.isNotEmpty()) { refView.text = "Ref: $ref"; refView.visibility = View.VISIBLE }
            else refView.visibility = View.GONE

            val isCredit = type in creditTypes
            val amtView  = row.findViewById<TextView>(R.id.txnAmount)
            amtView.text = "${if (isCredit) "+" else "-"}₱${String.format("%,.2f", amount)}"
            amtView.setTextColor(if (isCredit) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))

            val dateView = row.findViewById<TextView>(R.id.txnDate)
            dateView.text = try { outFmt.format(inFmt.parse(dateStr)!!) } catch (e: Exception) { dateStr }

            if (i > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#2C2C2C"))
                }
                layoutTransactions.addView(divider)
            }
            layoutTransactions.addView(row)
        }
    }

    // ── Withdraw dialog ───────────────────────────────────────────────────────

    private fun showWithdrawDialog() {
        val currentBalance = textBalance.text.toString()
            .replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0

        if (currentBalance <= 0) {
            Toast.makeText(this, "No balance to withdraw.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Enter amount"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#888888"))
            setText(String.format("%.2f", currentBalance))
            setPadding(48, 32, 48, 32)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Withdraw Funds")
            .setMessage("Available: ₱${String.format("%,.2f", currentBalance)}\nWithdrawals are processed in 2–3 business days.")
            .setView(input)
            .setPositiveButton("Withdraw") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull()
                when {
                    amount == null || amount <= 0 ->
                        Toast.makeText(this, "Enter a valid amount.", Toast.LENGTH_SHORT).show()
                    amount > currentBalance ->
                        Toast.makeText(this, "Insufficient balance.", Toast.LENGTH_SHORT).show()
                    else -> {
                        ApiClient.walletCashOut(userId, role, amount) { success, newBalance, msg ->
                            if (success) {
                                textBalance.text = "₱${String.format("%,.2f", newBalance)}"
                                Toast.makeText(this,
                                    "✅ Withdrawal of ₱${String.format("%,.2f", amount)} submitted! Ref: $msg",
                                    Toast.LENGTH_LONG).show()
                                loadTransactions()
                            } else {
                                Toast.makeText(this, msg.ifEmpty { "Withdrawal failed." }, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Top-up dialog ─────────────────────────────────────────────────────────

    private fun showTopUpDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view   = LayoutInflater.from(this).inflate(R.layout.dialog_top_up, null)
        dialog.setContentView(view)

        var selectedMethod = "gcash"

        val cardGcash   = view.findViewById<LinearLayout>(R.id.cardGcash)
        val cardMaya    = view.findViewById<LinearLayout>(R.id.cardMaya)
        val cardVisa    = view.findViewById<LinearLayout>(R.id.cardVisa)
        val inputAmount = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputCustomAmount)
        val btnConfirm  = view.findViewById<MaterialButton>(R.id.btnConfirmTopUp)

        val methodCards = listOf(
            Triple(cardGcash, "gcash",   "GCash"),
            Triple(cardMaya,  "paymaya", "Maya"),
            Triple(cardVisa,  "visa",    "Visa / Card"),
        )

        fun selectMethod(selected: LinearLayout, method: String) {
            methodCards.forEach { (card, _, _) -> card.background = resources.getDrawable(R.drawable.method_card_bg, theme) }
            selected.background = resources.getDrawable(R.drawable.method_card_bg_selected, theme)
            selectedMethod = method
        }

        selectMethod(cardGcash, "gcash")
        methodCards.forEach { (card, method, _) -> card.setOnClickListener { selectMethod(card, method) } }

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
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF6B00"))
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
