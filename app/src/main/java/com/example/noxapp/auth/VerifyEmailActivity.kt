package com.example.noxapp.auth

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.MainActivity
import com.example.noxapp.R
import com.example.noxapp.ui.applySystemBarPadding
import com.example.noxapp.ui.goTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Waits for the user to click auth link
class VerifyEmailActivity : AppCompatActivity() {
    private lateinit var checkButton: Button
    private lateinit var resendButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_verify_email)
        applySystemBarPadding()

        checkButton = findViewById(R.id.checkButton)
        resendButton = findViewById(R.id.resendButton)
        progress = findViewById(R.id.progress)

        findViewById<TextView>(R.id.verifyMessage).text =
            "We sent a verification link to ${AuthRepo.currentEmail()}.\n\n" +
                    "Open it on any device, then come back and tap the button below. " +
                    "Not there? Check your Junk or Quarantine folder."

        checkButton.setOnClickListener { checkVerified() }
        resendButton.setOnClickListener { resend() }
        findViewById<Button>(R.id.changeAccountButton).setOnClickListener {
            AuthRepo.signOut()
            goTo(LoginActivity::class.java)
        }
    }

    private fun checkVerified() {
        progress.isVisible = true
        checkButton.isEnabled = false
        lifecycleScope.launch {
            try {
                when (AuthRepo.checkAccess()) {
                    AccessState.Allowed -> goTo(MainActivity::class.java)
                    AccessState.NeedsVerification -> toast("Not verified yet. Tap the link in the email first.")
                    else -> goTo(LoginActivity::class.java)
                }
            } catch (e: Exception) {
                toast(AuthErrors.message(e))
            } finally {
                progress.isVisible = false
                checkButton.isEnabled = true
            }
        }
    }

    private fun resend() {
        resendButton.isEnabled = false
        lifecycleScope.launch {
            try {
                AuthRepo.resendVerification()
                toast("Sent! Check your inbox.")
            } catch (e: Exception) {
                toast(AuthErrors.message(e))
            }
            delay(60_000) // Firebase rate-limits resends
            resendButton.isEnabled = true
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}