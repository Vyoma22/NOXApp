package com.example.noxapp

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.goTo
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        applyEdgeToEdgeInsets(R.id.settingsRoot, scrollId = R.id.settingsScroll)

        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.settingsAccountEmail).text =
            FirebaseAuth.getInstance().currentUser?.email ?: getString(R.string.settings_signed_out)

        findViewById<Button>(R.id.signOutButton).setOnClickListener {
            AuthRepo.signOut()
            goTo(LoginActivity::class.java)
        }
    }
}
