
package com.example.noxapp

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.noxapp.core.Prefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NoxApp : Application() {

    companion object {
        // TEMPORARY: local Firebase testing only.
        // Change to false before sharing the finished code.
        private const val USE_LOCAL_FIREBASE_EMULATORS = false
    }

    override fun onCreate() {
        super.onCreate()

        val isDebugBuild =
            (applicationInfo.flags and
                    ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Never redirect a release build to local emulators.
        if (isDebugBuild && USE_LOCAL_FIREBASE_EMULATORS) {

            FirebaseAuth.getInstance().useEmulator(
                "10.0.2.2",
                9099
            )

            FirebaseFirestore.getInstance().useEmulator(
                "10.0.2.2",
                8080
            )
        }

        // Keep the existing NOX theme functionality.
        Prefs.applyTheme(this)
    }
}
