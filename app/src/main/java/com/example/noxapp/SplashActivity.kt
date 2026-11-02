package com.example.noxapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.auth.VerifyEmailActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * First frame of the app. Firebase restores a signed-in student synchronously, so the
 * destination is chosen without showing the login form to someone who is already signed in.
 */
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        val user = FirebaseAuth.getInstance().currentUser
        val target = when {
            user == null -> LoginActivity::class.java
            !user.isEmailVerified -> VerifyEmailActivity::class.java
            else -> MainActivity::class.java
        }
        startActivity(
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
