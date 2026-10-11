package com.example.noxapp

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.auth.AccessState
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.ui.goTo
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // TEMP sign-out button for testing auth
        findViewById<Button>(R.id.signOutButton).setOnClickListener {
            AuthRepo.signOut()
            goTo(LoginActivity::class.java)
        }
    }

    // Re-check verification + graduation lockout every time Home is shown
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val state = runCatching { AuthRepo.checkAccess() }.getOrNull() // null = offline
            if (state != null && state != AccessState.Allowed) goTo(LoginActivity::class.java)
        }
    }
}