package com.example.lalamove

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat

object NotificationHelper {

    fun showBanner(rootView: ViewGroup, message: String, isSuccess: Boolean = true) {
        val context = rootView.context
        val inflater = LayoutInflater.from(context)
        val bannerView = inflater.inflate(R.layout.layout_notification_banner, rootView, false)

        // Customize appearance
        val card = bannerView.findViewById<View>(R.id.bannerCard)
        val textView = bannerView.findViewById<TextView>(R.id.bannerText)
        textView.text = message

        if (!isSuccess) {
            card.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
        }

        // Add to root
        rootView.addView(bannerView)

        // Setup initial position (above screen)
        bannerView.translationY = -300f
        bannerView.alpha = 0f

        // Slide Down Animation
        bannerView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // Wait and Slide Up
                bannerView.postDelayed({
                    bannerView.animate()
                        .translationY(-300f)
                        .alpha(0f)
                        .setDuration(500)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            rootView.removeView(bannerView)
                        }
                        .start()
                }, 3000)
            }
            .start()
    }
}
