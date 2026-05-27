package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray

class DeliveryFormActivity : AppCompatActivity() {

    private val acctId get() = SessionManager.getAcctId(this)
    private val addressList = mutableListOf<AddressItem>()
    private lateinit var adapter: AddressAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: LinearLayout

    data class AddressItem(
        val id: String,
        val label: String,
        val address: String,
        val type: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery_form)

        recycler   = findViewById(R.id.recyclerAddresses)
        emptyView  = findViewById(R.id.emptyAddresses)
        val fab    = findViewById<FloatingActionButton>(R.id.fabAddAddress)
        val btnBack = findViewById<ImageView>(R.id.btnBackDelivery)

        btnBack.setOnClickListener { finish() }

        adapter = AddressAdapter(
            items     = addressList,
            onTap     = { item -> returnAddress(item) },
            onEdit    = { item -> showAddressDialog(item) },
            onDelete  = { item -> confirmDelete(item) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener { showAddressDialog(null) }

        loadAddresses()
    }

    private fun loadAddresses() {
        ApiClient.getAddresses(acctId) { arr ->
            addressList.clear()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    addressList.add(
                        AddressItem(
                            id      = obj.optString("addr_id"),
                            label   = obj.optString("label"),
                            address = obj.optString("address"),
                            type    = obj.optString("type", "other")
                        )
                    )
                }
            }
            adapter.notifyDataSetChanged()
            updateEmpty()
        }
    }

    private fun updateEmpty() {
        if (addressList.isEmpty()) {
            emptyView.visibility  = View.VISIBLE
            recycler.visibility   = View.GONE
        } else {
            emptyView.visibility  = View.GONE
            recycler.visibility   = View.VISIBLE
        }
    }

    private fun showAddressDialog(existing: AddressItem?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_address, null)
        val inputLabel   = view.findViewById<TextInputEditText>(R.id.inputAddrLabel)
        val inputAddress = view.findViewById<TextInputEditText>(R.id.inputAddrAddress)
        val spinnerType  = view.findViewById<Spinner>(R.id.spinnerAddrType)

        val types = listOf("home", "work", "other")
        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Home", "Work", "Other"))

        existing?.let {
            inputLabel.setText(it.label)
            inputAddress.setText(it.address)
            spinnerType.setSelection(types.indexOf(it.type).coerceAtLeast(0))
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Address" else "Edit Address")
            .setView(view)
            .setPositiveButton(if (existing == null) "Add" else "Save") { _, _ ->
                val label   = inputLabel.text.toString().trim()
                val address = inputAddress.text.toString().trim()
                val type    = types[spinnerType.selectedItemPosition]
                if (label.isEmpty() || address.isEmpty()) return@setPositiveButton

                if (existing == null) {
                    ApiClient.addAddress(acctId, label, address, type) { ok, newId ->
                        if (ok) {
                            addressList.add(AddressItem(newId, label, address, type))
                            adapter.notifyItemInserted(addressList.lastIndex)
                            updateEmpty()
                        }
                    }
                } else {
                    ApiClient.updateAddress(acctId, existing.id, label, address, type) { ok ->
                        if (ok) {
                            val idx = addressList.indexOfFirst { it.id == existing.id }
                            if (idx >= 0) {
                                addressList[idx] = AddressItem(existing.id, label, address, type)
                                adapter.notifyItemChanged(idx)
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: AddressItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Address")
            .setMessage("Remove \"${item.label}\"?")
            .setPositiveButton("Delete") { _, _ ->
                ApiClient.deleteAddress(acctId, item.id) { ok ->
                    if (ok) {
                        val idx = addressList.indexOfFirst { it.id == item.id }
                        if (idx >= 0) {
                            addressList.removeAt(idx)
                            adapter.notifyItemRemoved(idx)
                            updateEmpty()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun returnAddress(item: AddressItem) {
        val intent = Intent().apply {
            putExtra("SAVED_ADDRESS", item.address)
            putExtra("SAVED_LABEL", item.label)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    // ─── Adapter ───────────────────────────────────────────────────────────────
    class AddressAdapter(
        private val items: List<AddressItem>,
        private val onTap: (AddressItem) -> Unit,
        private val onEdit: (AddressItem) -> Unit,
        private val onDelete: (AddressItem) -> Unit
    ) : RecyclerView.Adapter<AddressAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView   = view.findViewById(R.id.addrTypeIcon)
            val label: TextView   = view.findViewById(R.id.addrLabel)
            val address: TextView = view.findViewById(R.id.addrText)
            val btnEdit: ImageView   = view.findViewById(R.id.btnEditAddr)
            val btnDelete: ImageView = view.findViewById(R.id.btnDeleteAddr)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_address, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.label.text   = item.label
            holder.address.text = item.address
            holder.icon.setImageResource(
                when (item.type) {
                    "home" -> R.drawable.ic_home
                    "work" -> R.drawable.ic_work
                    else   -> R.drawable.ic_location
                }
            )
            holder.itemView.setOnClickListener { onTap(item) }
            holder.btnEdit.setOnClickListener   { onEdit(item) }
            holder.btnDelete.setOnClickListener { onDelete(item) }
        }

        override fun getItemCount() = items.size
    }
}
