package com.example.noxapp

import android.app.Application
import com.example.noxapp.data.Prefs

class NoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply the saved theme before any activity inflates a layout.
        Prefs.applyTheme(this)
    }
}
