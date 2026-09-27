package com.crashalert.app.profile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RiderProfile(
    val fullName: String,
    val dateOfBirth: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val medications: String = "",
    val includeMedicalInDraft: Boolean = false
)

object ProfileRules {
    val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")

    fun valid(profile: RiderProfile): Boolean {
        if (profile.fullName.trim().length !in 2..80) return false
        if (profile.bloodGroup.isNotEmpty() && profile.bloodGroup !in bloodGroups) return false
        if (listOf(profile.allergies, profile.conditions, profile.medications).any { it.length > 200 }) return false
        if (profile.dateOfBirth.isNotBlank()) {
            val dob = try { LocalDate.parse(profile.dateOfBirth) } catch (_: Exception) { return false }
            if (dob.isAfter(LocalDate.now()) || dob.isBefore(LocalDate.now().minusYears(120))) return false
        }
        return true
    }

    /** Only included in a user-opened SMS draft after the rider enables sharing. */
    fun medicalSummary(profile: RiderProfile): String = if (!profile.includeMedicalInDraft) "" else buildList {
        add("Medical ID: ${profile.fullName.trim()}")
        if (profile.dateOfBirth.isNotBlank()) add("DOB ${profile.dateOfBirth}")
        if (profile.bloodGroup.isNotBlank()) add("Blood ${profile.bloodGroup}")
        if (profile.allergies.isNotBlank()) add("Allergies: ${profile.allergies.trim()}")
        if (profile.conditions.isNotBlank()) add("Conditions: ${profile.conditions.trim()}")
        if (profile.medications.isNotBlank()) add("Medications: ${profile.medications.trim()}")
    }.joinToString("; ")
}

/** Encrypts the medical card locally with a non-exportable Android Keystore AES key. */
class RiderProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("rider_profile", Context.MODE_PRIVATE)
    private val alias = "crashalert_rider_profile_v1"

    fun load(): RiderProfile? {
        return try {
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val ciphertext = Base64.decode(prefs.getString("body", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val obj = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        RiderProfile(
            obj.getString("fullName"), obj.optString("dateOfBirth"), obj.optString("bloodGroup"),
            obj.optString("allergies"), obj.optString("conditions"), obj.optString("medications"),
            obj.optBoolean("includeMedicalInDraft", false)
        ).takeIf(ProfileRules::valid)
        } catch (_: Exception) { null }
    }

    fun save(profile: RiderProfile): Boolean {
        if (!ProfileRules.valid(profile)) return false
        return try {
            val obj = JSONObject()
                .put("fullName", profile.fullName.trim()).put("dateOfBirth", profile.dateOfBirth.trim())
                .put("bloodGroup", profile.bloodGroup).put("allergies", profile.allergies.trim())
                .put("conditions", profile.conditions.trim()).put("medications", profile.medications.trim())
                .put("includeMedicalInDraft", profile.includeMedicalInDraft)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encoded = cipher.doFinal(obj.toString().toByteArray(Charsets.UTF_8))
            prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("body", Base64.encodeToString(encoded, Base64.NO_WRAP)).commit()
        } catch (_: Exception) { false }
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
