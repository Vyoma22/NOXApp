package com.example.noxapp.auth

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

// checking whether the current user may use the app
sealed interface AccessState {
    data object SignedOut : AccessState
    data object NeedsVerification : AccessState
    data class Expired(val endedOn: Date) : AccessState
    data object Allowed : AccessState
}

// this is where the application talks to the firebase auth
object AuthRepo {
    private val auth get() = FirebaseAuth.getInstance()
    private val users get() = FirebaseFirestore.getInstance().collection("users")

    fun isSignedIn(): Boolean = auth.currentUser != null
    fun currentEmail(): String? = auth.currentUser?.email
    fun signOut() = auth.signOut()

    // main fun that does like everything
    // creates the account, saves the profile, emails a verification link
    suspend fun signUp(name: String, email: String, password: String, gradMonth: Int, gradYear: Int) {
        val school = requireNotNull(EmailValidator.schoolFor(email)) {
            // not Ontech or Durham? pff, get outta here
            "Entered Email Domain not allowed"
        }
        // valid email? --> create account
        val user = requireNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)

        try {
            // Valid? Save the account
            val profile = hashMapOf(
                "displayName" to name,
                "email" to user.email,  // Firebase's copy, so it exactly matches the auth token
                "school" to school,
                "gradMonth" to gradMonth,
                "gradYear" to gradYear,
                "accessExpiresAt" to Timestamp(GradDate.accessExpiresAt(gradMonth, gradYear)),
                "createdAt" to FieldValue.serverTimestamp(),
            )
            users.document(user.uid).set(profile).await()

            // this is where we call the verification email link!
            user.sendEmailVerification().await()
        } catch (e: Exception) {
            // this lets user retry making accounts (could happen if link expires)
            user.delete().await()
            throw e
        }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    // resends email verification
    suspend fun resendVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    // the most important function, checks: 1. signed in 2: email verified. 3: not expired account
    suspend fun checkAccess(): AccessState {
        val cached = auth.currentUser ?: return AccessState.SignedOut

        try {
            cached.reload().await() // reload asks for verified flag from firebase, the phone doesn't know on its own
        } catch (e: FirebaseAuthInvalidUserException) {
            // The account was deleted or disabled in the Firebase console
            auth.signOut()
            return AccessState.SignedOut
        }
        val user = auth.currentUser ?: return AccessState.SignedOut
        if (!user.isEmailVerified) return AccessState.NeedsVerification

        // Get a fresh ID token so Security Rules see email_verified = true
        user.getIdToken(true).await()

        val expiresAt = users.document(user.uid).get().await()
            .getTimestamp("accessExpiresAt")?.toDate()
        if (expiresAt == null) {
            // No profile (e.g. deleted by hand during testing) Treat as signed out
            auth.signOut()
            return AccessState.SignedOut
        }
        if (!expiresAt.after(Date())) {
            auth.signOut()
            return AccessState.Expired(expiresAt)
        }
        return AccessState.Allowed
    }

}