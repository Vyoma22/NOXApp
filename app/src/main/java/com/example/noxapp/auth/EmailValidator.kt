/**
 * Email Validator
 * Decides weather student is from Ontech or Durham college.
 *
 * This gives feedback in the app
 * */

package com.example.noxapp.auth

object EmailValidator {

    // Stores emails as proper school names in profiles and such
    private val ALLOWED_DOMAINS = mapOf(
        "ontariotechu.net" to "Ontario Tech University",
        "dcmail.ca" to "Durham College"
    )

    // Returns the school, or null if the domain isn't allowed
    fun schoolFor(email: String): String? {
        val normalized = email.trim().lowercase()
        val at = normalized.indexOf('@')
        // One @ and must have something before it
        if (at <= 0 || at != normalized.lastIndexOf('@')) return null
        return ALLOWED_DOMAINS[normalized.substring(at + 1)]
    }

    fun isAllowed(email: String): Boolean = schoolFor(email) != null

}