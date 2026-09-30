package com.fixlens.app

import com.fixlens.app.data.BackendConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackendConfigTest {

    @Test
    fun `normalizes bare port to localhost http url`() {
        assertEquals("http://127.0.0.1:8000", BackendConfig.normalize("8000"))
    }

    @Test
    fun `normalizes colon port`() {
        assertEquals("http://127.0.0.1:8000", BackendConfig.normalize(":8000"))
    }

    @Test
    fun `normalizes host and port`() {
        assertEquals("http://192.168.1.20:8000", BackendConfig.normalize("192.168.1.20:8000"))
    }

    @Test
    fun `keeps full http url and strips trailing slash`() {
        assertEquals("http://192.168.1.20:8000", BackendConfig.normalize("http://192.168.1.20:8000/"))
    }

    @Test
    fun `rejects empty url`() {
        try {
            BackendConfig.normalize("  ")
            fail("Expected empty URL to be rejected")
        } catch (expected: IllegalArgumentException) {
            // pass
        }
    }

    @Test
    fun `rejects non http scheme`() {
        try {
            BackendConfig.normalize("ftp://example.com")
            fail("Expected non-http scheme to be rejected")
        } catch (expected: IllegalArgumentException) {
            // pass
        }
    }

    @Test
    fun `rejects missing host`() {
        try {
            BackendConfig.normalize("http://")
            fail("Expected missing host to be rejected")
        } catch (expected: IllegalArgumentException) {
            // pass
        }
    }
}
