package com.jtexpress.bevest.utils

/** Input validation used by forms across the app (plan section 31.2, section 9). */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    private val PHONE_REGEX = Regex("^[+]?[0-9 ()-]{7,20}$")

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(value.trim()) -> "Invalid email address"
        else -> null
    }

    fun password(value: String): String? = when {
        value.isBlank() -> "Password is required"
        value.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    fun required(value: String, field: String): String? =
        if (value.isBlank()) "$field is required" else null

    /**
     * Sanitizes a local Philippine (+63) phone input string:
     * - Digits only
     * - Removes leading '0' automatically once another digit is entered
     * - Maximum 10 digits
     */
    fun sanitizePhPhoneLocal(raw: String, preserveSingleZero: Boolean = false): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("0") && (!preserveSingleZero || digits.length > 1)) {
            digits = digits.trimStart('0')
        }
        return digits.take(10)
    }

    fun phone(value: String, required: Boolean = true): String? {
        val clean = value.trim()
        if (clean.isEmpty()) return if (required) "Phone number is required" else null
        if (clean.startsWith("+63")) {
            val localDigits = clean.removePrefix("+63").filter { it.isDigit() }
            return if (localDigits.length == 10 && !localDigits.startsWith("0")) null else "Phone number must be 10 digits"
        }
        return if (!PHONE_REGEX.matches(clean)) "Enter a valid phone number" else null
    }

    /**
     * Formats any non-blank phone number so that it always includes the country code (default +63).
     * Strips any leading '0' from local numbers when using '+63' (e.g. "09171234567" -> "+63 9171234567").
     */
    fun formatPhoneWithCountryCode(raw: String?, defaultCode: String = "+63"): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty() || clean.equals("Not set", ignoreCase = true) || clean == "--") return ""
        if (clean.startsWith("+")) {
            if (clean.startsWith(defaultCode)) {
                val rest = clean.removePrefix(defaultCode).trim()
                val normalizedLocal = if (defaultCode == "+63") rest.trimStart('0').trim() else rest
                return if (normalizedLocal.isNotEmpty()) "$defaultCode $normalizedLocal" else ""
            }
            return clean
        }
        if (defaultCode == "+63" && clean.startsWith("63") && clean.length >= 11) {
            val rest = clean.removePrefix("63").trim().trimStart('0').trim()
            return if (rest.isNotEmpty()) "+63 $rest" else ""
        }
        val local = if (defaultCode == "+63") clean.trimStart('0').trim() else clean
        return if (local.isNotEmpty()) "$defaultCode $local" else ""
    }

    /**
     * Extracts the local subscriber number without the country code prefix for display inside
     * a phone input field that already renders a fixed country code prefix.
     */
    fun stripCountryCode(raw: String?, defaultCode: String = "+63", preserveSingleZero: Boolean = false): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty() || clean.equals("Not set", ignoreCase = true) || clean == "--") return ""
        if (clean.startsWith(defaultCode)) {
            val rest = clean.removePrefix(defaultCode).trim()
            return if (defaultCode == "+63") sanitizePhPhoneLocal(rest, preserveSingleZero) else rest
        }
        if (defaultCode == "+63" && clean.startsWith("63") && clean.length >= 11) {
            return sanitizePhPhoneLocal(clean.removePrefix("63"), preserveSingleZero)
        }
        if (clean.startsWith("+")) {
            val spaceIdx = clean.indexOf(' ')
            if (spaceIdx > 0) return clean.substring(spaceIdx + 1).trim()
        }
        return if (defaultCode == "+63") sanitizePhPhoneLocal(clean, preserveSingleZero) else clean
    }

    fun workerId(value: String): String? = when {
        value.isBlank() -> "Worker ID is required"
        !Regex("^[A-Za-z0-9-]{3,20}$").matches(value.trim()) -> "3-20 letters, numbers or dashes"
        else -> null
    }

    fun vestId(value: String): String? = when {
        value.isBlank() -> "Vest ID is required"
        !Regex("^[A-Za-z0-9-]{3,20}$").matches(value.trim()) -> "3-20 letters, numbers or dashes"
        else -> null
    }

    /**
     * Checks if two MAC addresses belong to the same ESP32 hardware device.
     * Accounts for ESP32 hardware architecture where:
     * - Wi-Fi STA MAC = Base MAC (+0)
     * - Wi-Fi SoftAP MAC = Base MAC + 1
     * - Bluetooth / BLE MAC = Base MAC + 2
     */
    fun isMatchingEsp32Mac(mac1: String?, mac2: String?): Boolean {
        if (mac1.isNullOrBlank() || mac2.isNullOrBlank()) return true
        val clean1 = mac1.replace(Regex("[^A-Fa-f0-9]"), "").uppercase()
        val clean2 = mac2.replace(Regex("[^A-Fa-f0-9]"), "").uppercase()
        if (clean1 == clean2) return true
        if (clean1.length == 12 && clean2.length == 12) {
            val num1 = clean1.toLongOrNull(16) ?: return false
            val num2 = clean2.toLongOrNull(16) ?: return false
            return kotlin.math.abs(num1 - num2) <= 2L
        }
        return false
    }
}
