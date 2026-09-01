package com.jtexpress.bevest.utils

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `valid email passes`() {
        assertNull(Validators.email("sso@bevest.com"))
    }

    @Test
    fun `blank and malformed email fail`() {
        assertNotNull(Validators.email(""))
        assertNotNull(Validators.email("not-an-email"))
    }

    @Test
    fun `short password fails`() {
        assertNotNull(Validators.password("abc"))
        assertNull(Validators.password("abcdef"))
    }

    @Test
    fun `worker id must be alphanumeric`() {
        assertNull(Validators.workerId("WRK-0023"))
        assertNotNull(Validators.workerId("no spaces!"))
    }

    @Test
    fun `optional phone allows blank`() {
        assertNull(Validators.phone("", required = false))
        assertNotNull(Validators.phone("", required = true))
    }
}
