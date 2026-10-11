package com.example.noxapp.ui

import android.app.Activity
import android.content.Intent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.noxapp.R

// Opens [target] and clears the back stack
fun Activity.goTo(target: Class<out Activity>) {
    startActivity(
        Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}

// insert handling, pads the root view
fun Activity.applySystemBarPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}