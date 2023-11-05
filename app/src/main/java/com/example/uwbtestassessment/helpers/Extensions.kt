package com.example.uwbtestassessment.helpers

import android.app.Activity
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat

object Extensions {

    fun Activity.changeColorStatusBar(color: Int) {
        val window: Window = window
        val decorView = window.decorView
        val wic = WindowInsetsControllerCompat(window, decorView)
        wic.isAppearanceLightStatusBars = true
        window.statusBarColor = ContextCompat.getColor(this, color)
    }

}