package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject

class CouponsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var layoutNoCoupons: LinearLayout
    private val coupons = mutableListOf<JSONObject>()
    private lateinit var adapter: CouponAdapter

    private var userId = 0L
    private var fare = 0.0
    private var isSelectionMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coupons)

        userId = SessionManager.getUserId(this)
        fare   = intent.getDoubleExtra("FARE", 0.0)
        isSelectionMode = fare > 0.0

        recycler       = findViewById(R.id.recyclerCoupons)
        layoutNoCoupons = findViewById(R.id.layoutNoCoupons)

        adapter = CouponAdapter(coupons) { coupon ->
            if (isSelectionMode) returnCoupon(coupon)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.btnBackCoupons).setOnClickListener { finish() }

        val inputCode  = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputCouponCode)
        val btnApply   = findViewById<MaterialButton>(R.id.btnApplyCode)

        btnApply.setOnClickListener {
            val code = inputCode.text.toString().trim().uppercase()
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter a coupon code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnApply.isEnabled = false
            btnApply.text = "..."
            ApiClient.couponValidate(code, userId, fare) { result ->
                btnApply.isEnabled = true
                btnApply.text = "Apply"
                if (result == null) {
                    Toast.makeText(this, "Cannot reach server", Toast.LENGTH_SHORT).show()
                    return@couponValidate
                }
                if (result.optBoolean("success")) {
                    if (isSelectionMode) {
                        returnCoupon(result)
                    } else {
                        Toast.makeText(this, "Valid coupon: ${result.optString("code")}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, result.optString("error", "Invalid coupon"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        loadCoupons()
    }

    private fun loadCoupons() {
        ApiClient.couponList(userId) { arr ->
            coupons.clear()
            if (arr != null && arr.length() > 0) {
                for (i in 0 until arr.length()) {
                    coupons.add(arr.getJSONObject(i))
                }
                recycler.visibility = View.VISIBLE
                layoutNoCoupons.visibility = View.GONE
            } else {
                recycler.visibility = View.GONE
                layoutNoCoupons.visibility = View.VISIBLE
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun returnCoupon(coupon: JSONObject) {
        val intent = Intent()
        intent.putExtra("COUPON_CODE",  coupon.optString("Coup_Code").ifEmpty { coupon.optString("code") })
        intent.putExtra("COUPON_TYPE",  coupon.optString("Coup_Type").ifEmpty { coupon.optString("type") })
        intent.putExtra("COUPON_VALUE", coupon.optDouble("Coup_DiscountValue", coupon.optDouble("value", 0.0)))
        intent.putExtra("COUPON_DISCOUNT", coupon.optDouble("discount", 0.0))
        setResult(RESULT_OK, intent)
        finish()
    }

    inner class CouponAdapter(
        private val items: List<JSONObject>,
        private val onClick: (JSONObject) -> Unit
    ) : RecyclerView.Adapter<CouponAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val code: TextView     = view.findViewById(R.id.textCouponCode)
            val discount: TextView = view.findViewById(R.id.textCouponDiscount)
            val desc: TextView     = view.findViewById(R.id.textCouponDesc)
            val minSpend: TextView = view.findViewById(R.id.textCouponMinSpend)
            val expiry: TextView   = view.findViewById(R.id.textCouponExpiry)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_coupon, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            val codeStr  = c.optString("Coup_Code", "")
            val type     = c.optString("Coup_Type", "fixed")
            val value    = c.optDouble("Coup_DiscountValue", 0.0)
            val minSpend = c.optDouble("Coup_MinSpend", 0.0)
            val expiry   = c.optString("Coup_ExpiryDate", "")
            val descStr  = c.optString("Coup_Description", "")

            holder.code.text = codeStr
            holder.discount.text = if (type == "percentage") "${value.toInt()}% OFF" else "₱${String.format("%.0f", value)} OFF"
            holder.desc.text = descStr.ifEmpty { if (type == "percentage") "${value.toInt()}% off your order" else "₱${String.format("%.0f", value)} off your order" }
            holder.minSpend.text = if (minSpend > 0) "Min. spend: ₱${String.format("%.0f", minSpend)}" else "No minimum spend"
            holder.expiry.text = "Exp: $expiry"

            holder.itemView.setOnClickListener { onClick(c) }
        }
    }
}
