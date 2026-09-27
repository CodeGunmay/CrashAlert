package com.crashalert.app.contacts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContact(val name: String, val phone: String)

object ContactRules {
    const val MAX_CONTACTS = 3

    fun normalizePhone(input: String): String? {
        val trimmed = input.trim()
        if (!trimmed.matches(Regex("\\+?[0-9 ()-]+"))) return null
        val compact = trimmed.replace(Regex("[ ()-]"), "")
        val digits = compact.removePrefix("+")
        return compact.takeIf { digits.length in 7..15 && digits.all(Char::isDigit) }
    }

    fun add(existing: List<TrustedContact>, name: String, phone: String): List<TrustedContact>? {
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone) ?: return null
        if (cleanName.isEmpty() || cleanName.length > 60 || existing.size >= MAX_CONTACTS) return null
        if (existing.any { it.phone == cleanPhone }) return null
        return existing + TrustedContact(cleanName, cleanPhone)
    }
}

/** Private on-device storage. Contacts are never read from the system address book. */
class TrustedContactStore(context: Context) {
    private val prefs = context.getSharedPreferences("trusted_contacts", Context.MODE_PRIVATE)

    fun load(): List<TrustedContact> = try {
        val array = JSONArray(prefs.getString("list", "[]"))
        (0 until minOf(array.length(), ContactRules.MAX_CONTACTS)).map { index ->
            val item = array.getJSONObject(index)
            TrustedContact(item.getString("name"), item.getString("phone"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(contacts: List<TrustedContact>) {
        val array = JSONArray()
        contacts.take(ContactRules.MAX_CONTACTS).forEach { contact ->
            array.put(JSONObject().put("name", contact.name).put("phone", contact.phone))
        }
        prefs.edit().putString("list", array.toString()).apply()
    }
}
