package com.fixlens.app

import com.fixlens.app.data.BackendConfig
import com.fixlens.app.network.ApiClient
import com.fixlens.app.network.ApiError
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): ApiClient =
        ApiClient.create(BackendConfig(rawUrl = server.url("/").toString()))

    @Test
    fun `health parses ok response`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"status":"ok","version":"0.1.0"}""")
                .addHeader("Content-Type", "application/json"),
        )
        val health = client().health()
        assertEquals("ok", health.status)
        assertEquals("0.1.0", health.version)
    }

    @Test
    fun `health ignores unknown fields`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"status":"ok","extra":"ignored"}""")
                .addHeader("Content-Type", "application/json"),
        )
        assertEquals("ok", client().health().status)
    }

    @Test
    fun `health maps http error to typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            client().health()
            fail("Expected ApiError.Http")
        } catch (e: ApiError.Http) {
            assertEquals(500, e.code)
        }
    }

    @Test
    fun `health maps malformed body to invalid response error`() = runTest {
        server.enqueue(
            MockResponse().setBody("not json").addHeader("Content-Type", "application/json"),
        )
        try {
            client().health()
            fail("Expected ApiError.InvalidResponse")
        } catch (e: ApiError.InvalidResponse) {
            assertTrue(e.message!!.contains("did not match"))
        }
    }

    @Test
    fun `diagnose probe maps 501 to NotImplemented`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(501)
                .setBody("""{"detail":"not implemented"}"""),
        )
        val temp = File.createTempFile("probe", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val result = client().probeDiagnose(temp)
        temp.delete()
        assertEquals(ApiClient.DiagnoseProbeResult.NotImplemented, result)
    }

    @Test
    fun `health sends request to configured base url`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"status":"ok"}""")
                .addHeader("Content-Type", "application/json"),
        )
        client().health()
        val recorded = server.takeRequest()
        assertEquals("/health", recorded.path)
        assertEquals("GET", recorded.method)
    }
}
