package com.example.lalamove

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LocationAdapter(
    private val context: AppCompatActivity,
    private val locations: List<LocationItem>
) : ArrayAdapter<LocationItem>(context, 0, locations) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val location = getItem(position)

        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)

        text1.text = location?.name
        text2.text = location?.address

        return view
    }
}