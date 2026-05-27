package com.example.lalamove

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

object CustomDialog {
    fun show(
        context: Context,
        title: String,
        message: String,
        isSuccess: Boolean = true,
        onAction: () -> Unit = {}
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val dialogIcon = dialog.findViewById<ImageView>(R.id.dialogIcon)
        val dialogTitle = dialog.findViewById<TextView>(R.id.dialogTitle)
        val dialogMessage = dialog.findViewById<TextView>(R.id.dialogMessage)
        val btnAction = dialog.findViewById<Button>(R.id.btnDialogAction)

        dialogTitle.text = title
        dialogMessage.text = message
        
        // You can set different icons based on success/error here if you have them
        // For now using default ic_search with different colors
        if (!isSuccess) {
            btnAction.setBackgroundColor(Color.RED)
            // icon can be changed if you have an error drawable
        }

        btnAction.setOnClickListener {
            dialog.dismiss()
            onAction()
        }

        dialog.show()
    }
}
