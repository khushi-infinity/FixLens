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

class DiagnoseResponseTest {

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

    private fun validResponseJson() = """
        {
          "diagnosis": {
            "object_name": "office chair",
            "object_category": "furniture",
            "components": [
              {"name": "seat", "kind": "OBSERVED", "status": "intact"},
              {"name": "tilt mechanism", "kind": "INFERRED", "status": "suspected seized"}
            ],
            "issue_summary": "The seat does not rotate smoothly.",
            "likely_causes": [
              {"text": "Debris in the swivel mechanism", "confidence": 0.6},
              {"text": "Worn bearings", "confidence": 0.3}
            ],
            "confidence": 0.82,
            "confidence_band": "HIGH",
            "safety_level": "LOW",
            "safety_reason": "No sharp edges or electrical hazards visible.",
            "observations": [
              {"text": "Five-star base with grime around the spindle", "kind": "OBSERVED"},
              {"text": "Internal wear", "kind": "INFERRED"}
            ],
            "needs_better_view": false,
            "better_view_instruction": null,
            "professional_type": "NONE",
            "mode": "PHOTO",
            "provider_used": "gemini"
          },
          "safety": {
            "decision": "GUIDE",
            "user_message": "No significant hazards detected in this image.",
            "professional_type": "NONE"
          },
          "provider_used": "gemini",
          "duration_ms": 4820
        }
    """.trimIndent()

    @Test
    fun `diagnose parses full response`() = runTest {
        server.enqueue(
            MockResponse().setBody(validResponseJson())
                .addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("diag", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val result = client().diagnose(temp, mode = "PHOTO")
        temp.delete()

        assertEquals("office chair", result.diagnosis.objectName)
        assertEquals(0.82, result.diagnosis.confidence, 0.0001)
        assertEquals("HIGH", result.diagnosis.confidenceBand)
        assertEquals("LOW", result.diagnosis.safetyLevel)
        assertEquals(2, result.diagnosis.likelyCauses.size)
        assertEquals(2, result.diagnosis.components.size)
        assertEquals("seat", result.diagnosis.components[0].name)
        assertEquals("intact", result.diagnosis.components[0].status)
        assertEquals("INFERRED", result.diagnosis.components[1].kind)
        assertEquals("OBSERVED", result.diagnosis.observations[0].kind)
        assertEquals("INFERRED", result.diagnosis.observations[1].kind)
        assertEquals(false, result.diagnosis.needsBetterView)
        assertEquals("gemini", result.providerUsed)
        assertEquals(4820, result.durationMs)
        assertEquals("GUIDE", result.safety.decision)
    }

    @Test
    fun `diagnose records multipart path and mode`() = runTest {
        server.enqueue(
            MockResponse().setBody(validResponseJson())
                .addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("diag", ".jpg").apply { writeBytes(byteArrayOf(9)) }
        client().diagnose(temp, mode = "LIVE")
        temp.delete()
        val recorded = server.takeRequest()
        assertEquals("/api/v1/diagnose", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"mode\""))
        assertTrue(body.contains("LIVE"))
    }

    @Test
    fun `diagnose surfaces backend controlled detail on 503`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"detail":"The AI service is temporarily unavailable or rate-limited. Please try again in a moment."}"""),
        )
        val temp = File.createTempFile("diag", ".jpg").apply { writeBytes(byteArrayOf(9)) }
        try {
            client().diagnose(temp)
            fail("Expected ApiError.Http")
        } catch (e: ApiError.Http) {
            assertEquals(503, e.code)
            assertTrue(e.message!!.contains("temporarily unavailable"))
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `diagnose surfaces backend controlled detail on 502`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(502)
                .setBody("""{"detail":"We couldn't analyze this image reliably right now."}"""),
        )
        val temp = File.createTempFile("diag", ".jpg").apply { writeBytes(byteArrayOf(9)) }
        try {
            client().diagnose(temp)
            fail("Expected ApiError.Http")
        } catch (e: ApiError.Http) {
            assertEquals(502, e.code)
            assertTrue(e.message!!.contains("couldn't analyze"))
        } finally {
            temp.delete()
        }
    }
}
