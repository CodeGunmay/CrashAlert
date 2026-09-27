package com.crashalert.app.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test fun medicalDetailsRequireExplicitDraftConsent() {
        val profile = RiderProfile(fullName = "Rider Example", bloodGroup = "B+", allergies = "Peanuts")
        assertTrue(ProfileRules.medicalSummary(profile).isEmpty())
        val summary = ProfileRules.medicalSummary(profile.copy(includeMedicalInDraft = true))
        assertTrue(summary.contains("Blood B+"))
        assertTrue(summary.contains("Allergies: Peanuts"))
    }

    @Test fun rejectsInvalidDateAndBloodGroup() {
        assertFalse(ProfileRules.valid(RiderProfile(fullName = "A", bloodGroup = "B+")))
        assertFalse(ProfileRules.valid(RiderProfile(fullName = "Valid Name", bloodGroup = "X+")))
        assertFalse(ProfileRules.valid(RiderProfile(fullName = "Valid Name", dateOfBirth = "2035-01-01")))
        assertTrue(ProfileRules.valid(RiderProfile(fullName = "Valid Name", dateOfBirth = "2001-03-14")))
    }
}
