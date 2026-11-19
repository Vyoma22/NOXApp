package com.example.noxapp.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.noxapp.auth.AccessState
import com.example.noxapp.auth.AuthRepo
import com.example.noxapp.auth.LoginActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Re-checks access when a screen starts, then runs [onAllowed].
 *
 * Every screen that reads Firestore needs the same gate. The graduation lockout and the verified
 * email flag can change while the app is backgrounded, and a listener restarted without the check
 * answers with permission errors instead of routing through the existing login flow.
 *
 * A check that fails outright (offline, Firebase hiccup) answers null and keeps the current screen:
 * clearing the task and bouncing through login is worse than showing what is already loaded.
 *
 * Returns the Job so the screen can cancel it in onStop.
 */
fun AppCompatActivity.checkAccessOnStart(onAllowed: () -> Unit): Job =
    lifecycleScope.launch {
        val state = try {
            AuthRepo.checkAccess()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        // A signed-out result can be synchronous, before ON_START is dispatched.
        // onStop cancels this job, so no lifecycle-state check may skip the gate here.
        if (isFinishing || isDestroyed) return@launch
        if (state == null) return@launch
        if (state != AccessState.Allowed) {
            goTo(LoginActivity::class.java)
            return@launch
        }
        onAllowed()
    }