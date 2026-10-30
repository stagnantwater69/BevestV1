package com.jtexpress.bevest.utils

/** Input validation used by forms across the app (plan section 31.2, section 9). */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PHONE_REGEX = Regex("^[+]?[0-9 ()-]{7,20}$")

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !EMAIL_REGEX.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun password(value: String): String? = when {
        value.isBlank() -> "Password is required"
        value.length < 6 -> "Password must be at least 6 characters"
        else -> null
    }

    fun required(value: String, field: String): String? =
        if (value.isBlank()) "$field is required" else null

    fun phone(value: String, required: Boolean = true): String? = when {
        value.isBlank() -> if (required) "Phone number is required" else null
        !PHONE_REGEX.matches(value.trim()) -> "Enter a valid phone number"
        else -> null
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
