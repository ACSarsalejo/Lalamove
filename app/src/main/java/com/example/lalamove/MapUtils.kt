package com.example.lalamove

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.views.MapView

object MapUtils {
    /**
     * Applies a premium light-themed map style to the given MapView.
     * This makes all roads pure white (removing any yellow/orange road tint),
     * land/background a very light grey, buildings a slightly darker light grey,
     * and water/ocean a beautiful very light blue.
     */
    fun stylizeMap(mapView: MapView) {
        val lightThemeMatrix = ColorMatrix(floatArrayOf(
             0.20f,  0.70f, -0.10f, 0f, 55f,  // Red
             0.20f,  0.70f, -0.10f, 0f, 55f,  // Green
             0.10f,  0.70f,  0.10f, 0f, 55f,  // Blue
             0f,     0f,     0f,    1f, 0f    // Alpha
        ))
        mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(lightThemeMatrix))
    }
}
