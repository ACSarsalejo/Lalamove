package com.example.lalamove

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class InboxActivity : AppCompatActivity() {

    private lateinit var tabNotifications: TextView
    private lateinit var tabPromotions: TextView
    private lateinit var notificationsContent: LinearLayout
    private lateinit var promotionsContent: LinearLayout
    private lateinit var tabDivider: View

    private lateinit var firestore: FirebaseFirestore
    private var ordersListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        firestore = FirebaseFirestore.getInstance()

        val backButton = findViewById<View>(R.id.backButton)
        tabNotifications    = findViewById(R.id.tabNotifications)
        tabPromotions       = findViewById(R.id.tabPromotions)
        notificationsContent = findViewById(R.id.notificationsContent)
        promotionsContent    = findViewById(R.id.promotionsContent)
        tabDivider           = findViewById(R.id.tabDivider)

        backButton.setOnClickListener { finish() }
        tabNotifications.setOnClickListener { selectNotificationsTab() }
        tabPromotions.setOnClickListener    { selectPromotionsTab() }

        loadNotifications()
        loadPromotions()
    }

    private fun loadNotifications() {
        val userId = SessionManager.getUserId(this)

        ordersListener = firestore.collection("booking")
            .whereEqualTo("Book_CustID", userId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                val sorted = (snap?.documents ?: emptyList())
                    .sortedByDescending { it.getDate("Book_CreatedAt") }
                notificationsContent.removeAllViews()
                if (sorted.isEmpty()) {
                    notificationsContent.addView(makeEmptyLabel("No order notifications yet"))
                } else {
                    sorted.forEach { doc ->
                        val data    = doc.data ?: return@forEach
                        val status  = data["Book_Status"]?.toString() ?: "pending"
                        val pickup  = data["Book_Pickuploc"]?.toString() ?: "—"
                        val dropoff = data["Book_Dropoffloc"]?.toString() ?: "—"
                        notificationsContent.addView(makeNotifCard(status, pickup, dropoff))
                    }
                }
            }
    }

    private fun loadPromotions() {
        ApiClient.getActiveCoupons { arr ->
            promotionsContent.removeAllViews()

            if (arr == null || arr.length() == 0) {
                promotionsContent.addView(makeEmptyLabel("No active promotions right now"))
                return@getActiveCoupons
            }

            for (i in 0 until arr.length()) {
                val coupon = arr.getJSONObject(i)
                val code  = coupon.optString("Coup_Code", "")
                val type  = coupon.optString("Coup_Type", "fixed")
                val value = coupon.optDouble("Coup_Value", 0.0)
                val desc  = coupon.optString("Coup_Description", "")
                val expires = coupon.optString("Coup_ExpiresAt", "")
                val discount = if (type == "percent") "${value.toInt()}% OFF" else "₱${String.format("%.0f", value)} OFF"
                promotionsContent.addView(makePromoCard(code, discount, desc, expires))
            }
        }
    }

    private fun makeNotifCard(status: String, pickup: String, dropoff: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 12.dpToPx()
            layoutParams = lp
        }

        val (emoji, label, bgColor, textColor) = when (status) {
            "pending"   -> listOf("🕒", "Order Placed",     "#FFF3E0", "#FF6B00")
            "accepted"  -> listOf("🚗", "Driver Accepted",  "#E3F2FD", "#1976D2")
            "delivered" -> listOf("📦", "Out for Delivery", "#F3E5F5", "#7B1FA2")
            "completed" -> listOf("✅", "Delivered",        "#E8F5E9", "#388E3C")
            "cancelled" -> listOf("❌", "Cancelled",        "#FFEBEE", "#E53935")
            else        -> listOf("📋", status.replaceFirstChar { it.uppercase() }, "#F5F5F5", "#888888")
        }

        val inner = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            setBackgroundColor(Color.parseColor(bgColor as String))
            setPadding(14.dpToPx(), 12.dpToPx(), 14.dpToPx(), 12.dpToPx())
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = 10.dpToPx().toFloat()
            }
            background = shape
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
        }
        val emojiView = TextView(this).apply {
            text     = emoji as String
            textSize = 18f
            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp2.marginEnd = 8.dpToPx()
            layoutParams = lp2
        }
        val labelView = TextView(this).apply {
            text      = label as String
            textSize  = 14f
            setTextColor(Color.parseColor(textColor as String))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleRow.addView(emojiView)
        titleRow.addView(labelView)

        val locView = TextView(this).apply {
            text = "📍 $pickup → $dropoff"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp2.topMargin = 4.dpToPx()
            layoutParams = lp2
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        inner.addView(titleRow)
        inner.addView(locView)
        card.addView(inner)
        return card
    }

    private fun makePromoCard(code: String, discount: String, desc: String, expires: String): View {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = 12.dpToPx()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp
            setPadding(16.dpToPx(), 14.dpToPx(), 16.dpToPx(), 14.dpToPx())
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FFF3E0"))
                cornerRadius = 12.dpToPx().toFloat()
                setStroke(1.dpToPx(), Color.parseColor("#FFCC80"))
            }
            background = shape
        }

        val discountView = TextView(this).apply {
            text = discount
            textSize = 22f
            setTextColor(Color.parseColor("#FF6B00"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val codeView = TextView(this).apply {
            text = "Code: $code"
            textSize = 13f
            setTextColor(Color.parseColor("#444444"))
            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp2.topMargin = 4.dpToPx()
            layoutParams = lp2
        }
        if (desc.isNotEmpty()) {
            val descView = TextView(this).apply {
                text = desc
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp2.topMargin = 4.dpToPx()
                layoutParams = lp2
            }
            card.addView(discountView)
            card.addView(codeView)
            card.addView(descView)
        } else {
            card.addView(discountView)
            card.addView(codeView)
        }

        if (expires.isNotEmpty() && expires != "null") {
            val expView = TextView(this).apply {
                text = "Expires: ${expires.take(10)}"
                textSize = 11f
                setTextColor(Color.parseColor("#AAAAAA"))
                val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp2.topMargin = 4.dpToPx()
                layoutParams = lp2
            }
            card.addView(expView)
        }
        return card
    }

    private fun makeEmptyLabel(message: String): TextView {
        return TextView(this).apply {
            text     = message
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity  = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 24.dpToPx(), 0, 0)
            layoutParams = lp
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

    private fun selectNotificationsTab() {
        tabNotifications.setTextColor(ContextCompat.getColor(this, R.color.orange_main))
        tabPromotions.setTextColor(ContextCompat.getColor(this, R.color.gray_dark))
        notificationsContent.visibility = View.VISIBLE
        promotionsContent.visibility    = View.GONE
        val params = tabDivider.layoutParams as ConstraintLayout.LayoutParams
        params.horizontalBias = 0f
        tabDivider.layoutParams = params
    }

    private fun selectPromotionsTab() {
        tabPromotions.setTextColor(ContextCompat.getColor(this, R.color.orange_main))
        tabNotifications.setTextColor(ContextCompat.getColor(this, R.color.gray_dark))
        promotionsContent.visibility    = View.VISIBLE
        notificationsContent.visibility = View.GONE
        val params = tabDivider.layoutParams as ConstraintLayout.LayoutParams
        params.horizontalBias = 1f
        tabDivider.layoutParams = params
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.remove()
    }
}
