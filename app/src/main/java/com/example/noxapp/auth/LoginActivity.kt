package com.example.noxapp.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.MainActivity
import com.example.noxapp.R
import com.example.noxapp.ui.applySystemBarPadding
import com.example.noxapp.ui.goTo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// App entry point
class LoginActivity : AppCompatActivity() {

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        applySystemBarPadding()

        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        progress = findViewById(R.id.progress)

        loginButton.setOnClickListener { attemptLogin() }
        findViewById<Button>(R.id.forgotPasswordButton).setOnClickListener { sendPasswordReset() }
        findViewById<Button>(R.id.goToSignUpButton).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Firebase remembers sign-ins between launches
        if (AuthRepo.isSignedIn()) lifecycleScope.launch { route() }
    }

    private fun email() = emailInput.text?.toString().orEmpty().trim().lowercase()

    private fun attemptLogin() {
        val email = email()
        val password = passwordInput.text?.toString().orEmpty()
        emailLayout.error = if (EmailValidator.isAllowed(email)) null
        else "Use your @ontariotechu.net or @dcmail.ca email"
        passwordLayout.error = if (password.isEmpty()) "Enter your password" else null
        if (emailLayout.error != null || passwordLayout.error != null) return

        lifecycleScope.launch {
            setLoading(true)
            try {
                AuthRepo.signIn(email, password)
                route()
            } catch (e: Exception) {
                setLoading(false)
                toast(AuthErrors.message(e))
            }
        }
    }

    // Sends the user to the right screen based on [AuthRepo.checkAccess]
    private suspend fun route() {
        setLoading(true)
        val state = try {
            AuthRepo.checkAccess()
        } catch (e: Exception) {
            setLoading(false)
            toast(AuthErrors.message(e))
            return
        }
        setLoading(false)
        when (state) {
            AccessState.Allowed -> goTo(MainActivity::class.java)
            AccessState.NeedsVerification -> goTo(VerifyEmailActivity::class.java)
            is AccessState.Expired -> showAccessEnded(state.endedOn)
            AccessState.SignedOut -> Unit // stay here and show the form
        }
    }

    private fun showAccessEnded(endedOn: Date) {
        val date = DateFormat.getDateInstance(DateFormat.LONG).format(endedOn)
        MaterialAlertDialogBuilder(this)
            .setTitle("Your NOX access has ended")
            .setMessage("NOX is only for current students. Your access ended on $date, after your graduation month.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun sendPasswordReset() {
        val email = email()
        if (!EmailValidator.isAllowed(email)) {
            emailLayout.error = "Enter your school email first"
            return
        }
        lifecycleScope.launch {
            try {
                AuthRepo.sendPasswordReset(email)
                toast("If an account exists for $email, a reset link is on its way.")
            } catch (e: Exception) {
                toast(AuthErrors.message(e))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.isVisible = loading
        loginButton.isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}