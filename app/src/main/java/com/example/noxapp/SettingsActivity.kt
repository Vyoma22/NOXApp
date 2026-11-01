package com.example.noxapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.auth.AuthErrors
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import com.example.noxapp.data.BrowseSession
import com.example.noxapp.data.ListingRepo
import com.example.noxapp.data.Prefs
import com.example.noxapp.models.SortOption
import com.example.noxapp.models.UserProfile
import com.example.noxapp.ui.applyEdgeToEdgeInsets
import com.example.noxapp.ui.goTo
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.DateFormat

class SettingsActivity : AppCompatActivity() {

    private var profile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        applyEdgeToEdgeInsets(R.id.settingsRoot, scrollId = R.id.settingsScroll)

        findViewById<ImageButton>(R.id.settingsBackButton).setOnClickListener { finish() }

        bindRows()
        bindPreferences()
        bindVersion()
        findViewById<MaterialButton>(R.id.signOutButton).setOnClickListener { confirmSignOut() }
        findViewById<View>(R.id.settingsDeleteAccount).setOnClickListener { showDeleteAccountDialog() }

        loadProfile()
    }

    private fun bindRows() {
        bindRow(R.id.settingsEmailRow, R.string.settings_email, showChevron = false)
        bindRow(R.id.settingsSchoolRow, R.string.settings_school, showChevron = false)
        bindRow(R.id.settingsAccessRow, R.string.settings_access_ends, showChevron = false)
        bindRow(R.id.settingsPasswordRow, R.string.settings_change_password) { showPasswordDialog() }
        bindRow(R.id.settingsSafetyRow, R.string.settings_safety_tips) {
            showInfo(R.string.settings_safety_tips, R.string.settings_safety_message)
        }
        bindRow(R.id.settingsTermsRow, R.string.settings_terms) {
            showInfo(R.string.settings_terms, R.string.settings_terms_message)
        }
        bindRow(R.id.settingsPrivacyRow, R.string.settings_privacy_policy) {
            showInfo(R.string.settings_privacy_policy, R.string.settings_privacy_message)
        }
        bindRow(R.id.settingsHelpRow, R.string.settings_help) {
            showInfo(R.string.settings_help, R.string.settings_help_message)
        }
        bindRow(R.id.settingsReportRow, R.string.settings_report) { reportProblem() }
    }

    private fun bindPreferences() {
        bindRow(
            R.id.settingsThemeRow,
            R.string.settings_theme,
            getString(Prefs.theme(this).labelRes)
        ) { showThemeDialog() }
        bindRow(
            R.id.settingsSortRow,
            R.string.settings_default_sort,
            getString(Prefs.sortLabelRes(Prefs.defaultSort(this)))
        ) { showSortDialog() }
    }

    @Suppress("DEPRECATION")
    private fun bindVersion() {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = info.versionName ?: "1.0"
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode.toString()
        } else {
            info.versionCode.toString()
        }
        bindRow(
            R.id.settingsVersionRow,
            R.string.settings_version,
            getString(R.string.settings_version_value, version, code),
            showChevron = false
        )
    }

    private fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ListingRepo.getUserProfile(uid) { loaded ->
            if (isDestroyed) return@getUserProfile
            profile = loaded
            renderProfile()
        }
    }

    private fun renderProfile() {
        val email = profile?.email ?: FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        val school = profile?.school.orEmpty()
        val gradYear = profile?.gradYear?.toInt()

        findViewById<TextView>(R.id.settingsAvatarInitial).text =
            name.take(1).uppercase().ifEmpty { "?" }
        findViewById<TextView>(R.id.settingsProfileName).text = name
        findViewById<TextView>(R.id.settingsProfileSchool).text = listOfNotNull(
            school.takeIf { it.isNotBlank() },
            gradYear?.let { "Class of $it" }
        ).joinToString(" • ").ifBlank { getString(R.string.settings_profile_missing) }

        setRowValue(R.id.settingsEmailRow, email)
        setRowValue(R.id.settingsSchoolRow, school.ifBlank { "—" })
        setRowValue(
            R.id.settingsAccessRow,
            profile?.accessExpiresAt?.toDate()?.let {
                DateFormat.getDateInstance(DateFormat.LONG).format(it)
            } ?: "—"
        )
    }

    private fun bindRow(
        rowId: Int,
        titleRes: Int,
        value: String? = null,
        showChevron: Boolean = true,
        onClick: (() -> Unit)? = null
    ) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.settingsRowTitle).setText(titleRes)
        row.findViewById<TextView>(R.id.settingsRowValue).apply {
            text = value.orEmpty()
            isVisible = !value.isNullOrBlank()
        }
        row.findViewById<View>(R.id.settingsRowChevron).isVisible = showChevron
        if (onClick != null) {
            row.setOnClickListener { onClick() }
        } else {
            row.setOnClickListener(null)
            row.isClickable = false
            row.isFocusable = false
            row.background = null
        }
    }

    private fun setRowValue(rowId: Int, value: String) {
        findViewById<View>(rowId).findViewById<TextView>(R.id.settingsRowValue).apply {
            text = value
            isVisible = value.isNotBlank()
        }
    }

    private fun showThemeDialog() {
        val themes = Prefs.Theme.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(
                themes.map { getString(it.labelRes) }.toTypedArray(),
                themes.indexOf(Prefs.theme(this))
            ) { dialog, which ->
                Prefs.setTheme(this, themes[which])
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun showSortDialog() {
        val sorts = SortOption.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_default_sort)
            .setSingleChoiceItems(
                sorts.map { getString(Prefs.sortLabelRes(it)) }.toTypedArray(),
                sorts.indexOf(Prefs.defaultSort(this))
            ) { dialog, which ->
                Prefs.setDefaultSort(this, sorts[which])
                setRowValue(R.id.settingsSortRow, getString(Prefs.sortLabelRes(sorts[which])))
                dialog.dismiss()
            }
            .show()
    }

    private fun showPasswordDialog() {
        val container = dialogContainer()
        val currentLayout = passwordField(R.string.settings_current_password)
        val newLayout = passwordField(R.string.settings_new_password)
        val confirmLayout = passwordField(R.string.settings_confirm_password)
        container.addView(currentLayout)
        container.addView(newLayout)
        container.addView(confirmLayout)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_password_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val current = currentLayout.editText?.text?.toString().orEmpty()
                val new = newLayout.editText?.text?.toString().orEmpty()
                val confirm = confirmLayout.editText?.text?.toString().orEmpty()
                newLayout.error = if (new.length < 8) getString(R.string.settings_password_short) else null
                confirmLayout.error = if (new != confirm) getString(R.string.settings_password_mismatch) else null
                if (newLayout.error != null || confirmLayout.error != null) return@setOnClickListener
                if (current.isEmpty()) return@setOnClickListener

                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                lifecycleScope.launch {
                    try {
                        AuthRepo.changePassword(current, new)
                        Toast.makeText(this@SettingsActivity, R.string.settings_password_updated, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, AuthErrors.message(e), Toast.LENGTH_LONG).show()
                        button.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteAccountDialog() {
        val container = dialogContainer()
        val passwordLayout = passwordField(R.string.settings_current_password)
        val confirmLayout = TextInputLayout(this).apply {
            hint = getString(R.string.settings_delete_confirm_hint)
        }
        val confirmInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        confirmLayout.addView(confirmInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(passwordLayout)
        container.addView(confirmLayout)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_delete_account_title)
            .setMessage(R.string.settings_delete_account_message)
            .setView(container)
            .setPositiveButton(R.string.settings_delete, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = passwordLayout.editText?.text?.toString().orEmpty()
                val confirmation = confirmInput.text?.toString()?.trim().orEmpty()
                confirmLayout.error = if (confirmation != getString(R.string.settings_delete_confirm_word)) {
                    getString(R.string.settings_delete_confirm_hint)
                } else {
                    null
                }
                if (confirmLayout.error != null || password.isEmpty()) return@setOnClickListener

                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.isEnabled = false
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                lifecycleScope.launch {
                    try {
                        AuthRepo.reauthenticate(password)
                        ListingRepo.deleteAccountData(uid)
                        AuthRepo.deleteCurrentUser()
                        BrowseSession.clear()
                        dialog.dismiss()
                        goTo(LoginActivity::class.java)
                    } catch (e: Exception) {
                        Toast.makeText(this@SettingsActivity, AuthErrors.message(e), Toast.LENGTH_LONG).show()
                        button.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_sign_out_title)
            .setMessage(R.string.settings_sign_out_message)
            .setPositiveButton(R.string.sign_out) { _, _ ->
                AuthRepo.signOut()
                BrowseSession.clear()
                goTo(LoginActivity::class.java)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInfo(titleRes: Int, messageRes: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun reportProblem() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_report_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.settings_report_body))
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.settings_report)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.settings_update_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun dialogContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(4), dp(24), 0)
    }

    private fun passwordField(hintRes: Int): TextInputLayout = TextInputLayout(this).apply {
        hint = getString(hintRes)
        endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        addView(
            TextInputEditText(this@SettingsActivity).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
