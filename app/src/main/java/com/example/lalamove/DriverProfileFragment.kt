package com.example.lalamove

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class DriverProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_driver_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name  = SessionManager.getName(requireContext())
        val email = SessionManager.getEmail(requireContext())

        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "D"
        view.findViewById<TextView>(R.id.textDriverInitial).text  = initial
        view.findViewById<TextView>(R.id.textProfileName).text    = name.ifEmpty { "Driver" }
        view.findViewById<TextView>(R.id.textProfileEmail).text   = email.ifEmpty { "" }
        view.findViewById<TextView>(R.id.textProfileNameRow).text  = name.ifEmpty { "—" }
        view.findViewById<TextView>(R.id.textProfileEmailRow).text = email.ifEmpty { "—" }

        view.findViewById<MaterialButton>(R.id.btnDriverLogout).setOnClickListener {
            SessionManager.clear(requireContext())
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
        }
    }
}
