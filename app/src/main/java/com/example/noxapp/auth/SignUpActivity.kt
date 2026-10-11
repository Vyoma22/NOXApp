package com.example.noxapp.auth

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.R
import com.example.noxapp.ui.applySystemBarPadding
import com.example.noxapp.ui.goTo
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date

// Collects name, school email, password, grad, creates the account
class SignUpActivity : AppCompatActivity() {
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var monthSpinner: Spinner
    private lateinit var yearSpinner: Spinner
    private lateinit var gradError: TextView
    private lateinit var signUpButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)
        applySystemBarPadding()

        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmLayout = findViewById(R.id.confirmLayout)
        monthSpinner = findViewById(R.id.monthSpinner)
        yearSpinner = findViewById(R.id.yearSpinner)
        gradError = findViewById(R.id.gradError)
        signUpButton = findViewById(R.id.signUpButton)
        progress = findViewById(R.id.progress)

        // Month names in the phone's language array has 13 entries, the last is blank
        val months = DateFormatSymbols().months.take(12)
        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        // This year through 6 years out covers 4-year degrees plus co-op/extensions.
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, (thisYear..thisYear + 6).toList())

        signUpButton.setOnClickListener { attemptSignUp() }
        findViewById<Button>(R.id.goToLoginButton).setOnClickListener { finish() }
    }

    // the big sign up functoin.
    private fun attemptSignUp() {
        val name = findViewById<TextInputEditText>(R.id.nameInput).text?.toString().orEmpty().trim()
        val email = findViewById<TextInputEditText>(R.id.emailInput).text?.toString().orEmpty().trim().lowercase()
        val password = findViewById<TextInputEditText>(R.id.passwordInput).text?.toString().orEmpty()
        val confirm = findViewById<TextInputEditText>(R.id.confirmInput).text?.toString().orEmpty()
        val gradMonth = monthSpinner.selectedItemPosition + 1   // +1 as 0 = Jan
        val gradYear = yearSpinner.selectedItem as Int

        // Validate everything and show every problem at once, not one at a time.
        nameLayout.error = if (name.isEmpty()) "Enter your name" else null
        emailLayout.error = if (EmailValidator.isAllowed(email)) null
        else "Use your @ontariotechu.net or @dcmail.ca email"
        passwordLayout.error = if (password.length < 8) "At least 8 characters" else null
        confirmLayout.error = if (confirm != password) "Passwords don't match" else null
        val gradInFuture = GradDate.accessExpiresAt(gradMonth, gradYear).after(Date())
        gradError.text = "Graduation date can't be in the past"
        gradError.isVisible = !gradInFuture

        val hasErrors = listOf(nameLayout, emailLayout, passwordLayout, confirmLayout).any { it.error != null }
        if (hasErrors || !gradInFuture) return

        setLoading(true)
        lifecycleScope.launch {
            try {
                AuthRepo.signUp(name, email, password, gradMonth, gradYear)
                goTo(VerifyEmailActivity::class.java)
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@SignUpActivity, AuthErrors.message(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.isVisible = loading
        signUpButton.isEnabled = !loading
    }
}