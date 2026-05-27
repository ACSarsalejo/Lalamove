package com.example.lalamove

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject

class SavedAddressesActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var textEmpty: TextView
    private val addresses = mutableListOf<JSONObject>()
    private val acctId get() = SessionManager.getAcctId(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_addresses)

        recycler   = findViewById(R.id.recyclerAddresses)
        textEmpty  = findViewById(R.id.textNoAddresses)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter       = AddressAdapter()

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnAddAddress).setOnClickListener { showAddDialog() }

        loadAddresses()
    }

    private fun loadAddresses() {
        ApiClient.getAddresses(acctId) { arr ->
            addresses.clear()
            if (arr != null) {
                for (i in 0 until arr.length()) addresses.add(arr.getJSONObject(i))
            }
            updateEmpty()
            recycler.adapter?.notifyDataSetChanged()
        }
    }

    private fun updateEmpty() {
        textEmpty.visibility  = if (addresses.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility   = if (addresses.isEmpty()) View.GONE   else View.VISIBLE
    }

    private fun showAddDialog(existing: JSONObject? = null) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_address, null)
        val inputLabel   = view.findViewById<EditText>(R.id.inputAddrLabel)
        val inputAddress = view.findViewById<EditText>(R.id.inputAddrAddress)
        val radioGroup   = view.findViewById<RadioGroup>(R.id.radioAddrType)

        if (existing != null) {
            inputLabel.setText(existing.optString("Addr_Label"))
            inputAddress.setText(existing.optString("Addr_Address"))
            when (existing.optString("Addr_Type", "other")) {
                "home" -> radioGroup.check(R.id.radioHome)
                "work" -> radioGroup.check(R.id.radioWork)
                else   -> radioGroup.check(R.id.radioOther)
            }
        } else {
            radioGroup.check(R.id.radioOther)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Address" else "Edit Address")
            .setView(view)
            .setPositiveButton(if (existing == null) "Save" else "Update") { _, _ ->
                val label   = inputLabel.text.toString().trim()
                val address = inputAddress.text.toString().trim()
                val type    = when (radioGroup.checkedRadioButtonId) {
                    R.id.radioHome -> "home"
                    R.id.radioWork -> "work"
                    else           -> "other"
                }
                if (label.isEmpty() || address.isEmpty()) {
                    Toast.makeText(this, "Label and address are required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    ApiClient.addAddress(acctId, label, address, type) { success, _ ->
                        if (success) { loadAddresses(); Toast.makeText(this, "Address saved!", Toast.LENGTH_SHORT).show() }
                        else Toast.makeText(this, "Failed to save address.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    ApiClient.updateAddress(acctId, existing.optInt("Addr_ID"), label, address, type) { success ->
                        if (success) { loadAddresses(); Toast.makeText(this, "Address updated!", Toast.LENGTH_SHORT).show() }
                        else Toast.makeText(this, "Failed to update address.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class AddressAdapter : RecyclerView.Adapter<AddressAdapter.VH>() {

        inner class VH(val view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_saved_address, parent, false))

        override fun getItemCount() = addresses.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val addr  = addresses[position]
            val label = addr.optString("Addr_Label", "Address")
            val text  = addr.optString("Addr_Address", "")
            val type  = addr.optString("Addr_Type", "other")

            val iconRes = when (type) { "home" -> R.drawable.ic_home; "work" -> R.drawable.ic_work; else -> R.drawable.ic_location }
            holder.view.findViewById<ImageView>(R.id.addrTypeIcon).setImageResource(iconRes)
            holder.view.findViewById<TextView>(R.id.addrLabel).text = label
            holder.view.findViewById<TextView>(R.id.addrText).text  = text

            holder.view.setOnClickListener { showAddDialog(addr) }
            holder.view.findViewById<ImageView>(R.id.btnEditAddr).setOnClickListener { showAddDialog(addr) }

            holder.view.findViewById<ImageView>(R.id.btnDeleteAddr).setOnClickListener {
                AlertDialog.Builder(this@SavedAddressesActivity)
                    .setTitle("Delete Address")
                    .setMessage("Remove \"$label\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        ApiClient.deleteAddress(acctId, addr.optInt("Addr_ID")) { success ->
                            if (success) {
                                addresses.removeAt(holder.adapterPosition)
                                notifyItemRemoved(holder.adapterPosition)
                                updateEmpty()
                            } else {
                                Toast.makeText(this@SavedAddressesActivity, "Delete failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
