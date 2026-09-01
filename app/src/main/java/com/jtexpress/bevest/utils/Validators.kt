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
}
