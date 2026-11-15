package com.jtexpress.bevest.utils

import java.security.SecureRandom

/**
 * Temporary passwords for new accounts: `PREFIX-xxxx-xxxx`, drawn from a secure source
 * (about 63 bits) and skipping look-alike characters (0/O, 1/l/I), since they are
 * usually read out or copied by hand. The account must change it on first sign-in.
 */
object TempPassword {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    fun generate(prefix: String): String {
        val chunk = { (1..5).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }
        return "$prefix-${chunk()}-${chunk()}"
    }
}
