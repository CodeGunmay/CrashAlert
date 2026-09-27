package com.crashalert.app.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactRulesTest {
    @Test fun acceptsFormattedNumbersAndNormalizesThem() {
        assertEquals("+919876543210", ContactRules.normalizePhone("+91 98765-43210"))
    }

    @Test fun rejectsUnexpectedCharactersAndTooShortNumbers() {
        assertNull(ContactRules.normalizePhone("9876"))
        assertNull(ContactRules.normalizePhone("+91;12345678"))
        assertNull(ContactRules.normalizePhone("++919876543210"))
    }

    @Test fun enforcesLimitAndDuplicateNumber() {
        val a = ContactRules.add(emptyList(), "A", "9876543210")!!
        assertNull(ContactRules.add(a, "B", "98765 43210"))
        val b = ContactRules.add(a, "B", "9876543211")!!
        val c = ContactRules.add(b, "C", "9876543212")!!
        assertNull(ContactRules.add(c, "D", "9876543213"))
    }
}
