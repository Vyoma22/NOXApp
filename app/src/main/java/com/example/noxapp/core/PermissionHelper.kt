package com.example.noxapp.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PermissionHelper {

    fun hasAll(context: Context, vararg permissions: String): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Checks whether the user selected "Don't ask again" (or denied twice on Android 11+).
     * Call this ONLY inside the ActivityResultCallback after a permission comes back denied.
     */
    fun isPermanentlyDenied(activity: Activity, permissions: Array<String>): Boolean =
        permissions.any { perm ->
            !hasAll(activity, perm) &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }

    /**
     * Shows a clear explanation dialog when a user denies a permission.
     * Offers "Open Settings" if permanently denied, or "Try Again" if they can still be prompted.
     */
    fun showDeniedDialog(
        activity: Activity,
        title: String,
        message: String,
        permanentlyDenied: Boolean,
        onRetry: (() -> Unit)? = null
    ) {
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Not Now", null)

        if (permanentlyDenied) {
            builder.setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null)
                )
                activity.startActivity(intent)
            }
        } else if (onRetry != null) {
            builder.setPositiveButton("Try Again") { _, _ -> onRetry() }
        }

        builder.show()
    }
}