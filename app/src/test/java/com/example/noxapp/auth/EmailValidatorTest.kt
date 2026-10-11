package com.example.noxapp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailValidatorTest {

    @Test fun acceptsOntarioTechStudent() =
        assertEquals("Ontario Tech University", EmailValidator.schoolFor("jane.doe@ontariotechu.net"))

    @Test fun acceptsDurhamStudentIgnoringCaseAndSpaces() =
        assertEquals("Durham College", EmailValidator.schoolFor("  John.Smith@DCMail.ca "))

    @Test fun rejectsStaffDomains() {
        assertFalse(EmailValidator.isAllowed("prof@ontariotechu.ca"))
        assertFalse(EmailValidator.isAllowed("staff@durhamcollege.ca"))
    }

    @Test fun rejectsLookalikesAndMalformed() {
        assertFalse(EmailValidator.isAllowed("someone@gmail.com"))
        assertFalse(EmailValidator.isAllowed("x@ontariotechu.net.evil.com"))
        assertFalse(EmailValidator.isAllowed("x@fakeontariotechu.net"))
        assertFalse(EmailValidator.isAllowed("a@b@ontariotechu.net"))
        assertNull(EmailValidator.schoolFor("@dcmail.ca"))
    }

    @Test fun acceptsBoth() {
        assertTrue(EmailValidator.isAllowed("a@ontariotechu.net"))
        assertTrue(EmailValidator.isAllowed("b@dcmail.ca"))
    }
}