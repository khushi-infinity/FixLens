package com.fixlens.app

import com.fixlens.app.data.BackendConfigStore
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendConfigStoreTest {

    @Test
    fun `parses simple properties`() {
        val props = BackendConfigStore.parseProperties(
            listOf("backend.url=http://127.0.0.1:8000", "# comment", "", "timeout=20"),
        )
        assertEquals("http://127.0.0.1:8000", props["backend.url"])
        assertEquals("20", props["timeout"])
    }

    @Test
    fun `parses values containing equals signs`() {
        val props = BackendConfigStore.parseProperties(listOf("token=a=b=c"))
        assertEquals("a=b=c", props["token"])
    }

    @Test
    fun `ignores malformed lines`() {
        val props = BackendConfigStore.parseProperties(listOf("no-equals-sign", "=novalue"))
        assertEquals(0, props.size)
    }
}
