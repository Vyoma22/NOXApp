package com.example.noxapp.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

// makes firebase errors into readable messages
object AuthErrors {
    fun message (e: Exception): String = when (e) {
        // run through each error
        is FirebaseAuthUserCollisionException -> "An account with this email already exists. Try logging in."
        is FirebaseAuthWeakPasswordException -> "Password must have at least 8 characters."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
        is FirebaseAuthInvalidUserException -> "Email is not linked to an account, try creating an account."
        is FirebaseTooManyRequestsException -> "Too many failed attempts. Try again in a few minutes."
        is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        is FirebaseFirestoreException ->
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) "Access denied." else "Database error: ${e.code}"
        else -> e.localizedMessage ?: "Something went wrong. Please try again."
    }
}