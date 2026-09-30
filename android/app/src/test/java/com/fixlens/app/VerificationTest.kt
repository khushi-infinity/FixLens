package com.fixlens.app

import com.fixlens.app.data.BackendConfig
import com.fixlens.app.network.ApiClient
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.VerificationStates
import com.fixlens.app.repair.EnginePhase
import com.fixlens.app.repair.RepairEngine
import com.fixlens.app.repair.RepairIntent
import com.fixlens.app.repair.RepairSessionPlan
import com.fixlens.app.repair.RepairSessionStatus
import com.fixlens.app.repair.RepairStepSession
import com.fixlens.app.repair.VerificationResult
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 5 tests: the verify wire contract + the engine verification seam.
 * The engine tests prove the structural rule: a step CANNOT complete without
 * an explicit Advance, and the UI dispatches Advance only on PASS.
 */
class VerificationTest {

    // -----------------------------------------------------------------------
    // Wire layer: POST /api/v1/verify
    // -----------------------------------------------------------------------

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

    private fun verifyJson(
        state: String = "PASS",
        explanation: String = "The screw head sits flush against the bracket.",
        needsView: Boolean = false,
        viewInstruction: String? = null,
    ) = """
        {
          "step_number": 1,
          "state": "$state",
          "confidence": 0.87,
          "explanation": "$explanation",
          "needs_better_view": $needsView,
          "better_view_instruction": ${viewInstruction?.let { "\"$it\"" } ?: "null"},
          "provider_used": "gemini",
          "duration_ms": 4210
        }
    """.trimIndent()

    @Test
    fun `verify posts multipart to verify endpoint and parses PASS`() = runTest {
        server.enqueue(
            MockResponse().setBody(verifyJson()).addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("verify", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val response = client().verify(
            image = temp,
            stepNumber = 1,
            expectedState = "The screw is visibly tight.",
            stepAction = "Turn the screw clockwise.",
            targetComponent = "seat mounting screw",
        )
        temp.delete()

        val recorded = server.takeRequest()
        assertEquals("/api/v1/verify", recorded.path)
        assertEquals("POST", recorded.method)
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"step_number\""))
        assertTrue(body.contains("name=\"expected_state\""))
        assertTrue(body.contains("The screw is visibly tight."))
        assertTrue(body.contains("name=\"step_action\""))
        assertTrue(body.contains("name=\"target_component\""))
        assertTrue(body.contains("name=\"user_confirms_done\""))

        assertEquals(1, response.stepNumber)
        assertEquals(VerificationStates.PASS, response.state)
        assertEquals("The screw head sits flush against the bracket.", response.explanation)
        assertEquals(false, response.needsBetterView)
    }

    @Test
    fun `verify parses UNCERTAIN with better view instruction`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    verifyJson(
                        state = "UNCERTAIN",
                        explanation = "The area worked on is out of frame.",
                        needsView = true,
                        viewInstruction = "Move the camera closer to the hinge.",
                    ),
                )
                .addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("verify", ".jpg").apply { writeBytes(byteArrayOf(4)) }
        val response = client().verify(temp, 2, "Gap is closed.")
        temp.delete()

        assertEquals(VerificationStates.UNCERTAIN, response.state)
        assertEquals(true, response.needsBetterView)
        assertEquals("Move the camera closer to the hinge.", response.betterViewInstruction)
    }

    @Test
    fun `verify parses FAIL state`() = runTest {
        server.enqueue(
            MockResponse().setBody(verifyJson(state = "FAIL", explanation = "Screw still protrudes."))
                .addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("verify", ".jpg").apply { writeBytes(byteArrayOf(5)) }
        val response = client().verify(temp, 1, "The screw is visibly tight.")
        temp.delete()

        assertEquals(VerificationStates.FAIL, response.state)
        assertEquals("Screw still protrudes.", response.explanation)
    }

    @Test
    fun `verify 503 maps backend detail for retry UI`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"detail":"The AI service is temporarily unavailable or rate-limited. Please try again in a moment."}"""),
        )
        val temp = File.createTempFile("verify", ".jpg").apply { writeBytes(byteArrayOf(6)) }
        try {
            client().verify(temp, 1, "tight")
            fail("Expected ApiError.Http")
        } catch (e: ApiError.Http) {
            assertEquals(503, e.code)
            assertTrue(e.message!!.contains("temporarily unavailable"))
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `verify 400 quality-gate rejection surfaces friendly detail`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"detail":"The image is too dark. Try better lighting."}"""),
        )
        val temp = File.createTempFile("verify", ".jpg").apply { writeBytes(byteArrayOf(7)) }
        try {
            client().verify(temp, 1, "tight")
            fail("Expected ApiError.Http")
        } catch (e: ApiError.Http) {
            assertEquals(400, e.code)
            assertTrue(e.message!!.contains("too dark"))
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `verify image bytes are transmitted unmodified`() = runTest {
        server.enqueue(
            MockResponse().setBody(verifyJson()).addHeader("Content-Type", "application/json"),
        )
        val payload = ByteArray(512) { (it % 251).toByte() }
        val temp = File.createTempFile("verify", ".jpg").apply { writeBytes(payload) }
        client().verify(temp, 1, "tight")
        temp.delete()

        val recorded = server.takeRequest()
        val sent: Buffer = recorded.body
        val all = sent.readByteArray()
        // The multipart body must contain the exact file bytes.
        val found = all.indices.any { i -> i + payload.size <= all.size && all.copyOfRange(i, i + payload.size).contentEquals(payload) }
        assertTrue(found)
    }

    // -----------------------------------------------------------------------
    // Engine seam: verification gates step completion
    // -----------------------------------------------------------------------

    private fun engineWith(steps: Int = 2): RepairEngine {
        val engine = RepairEngine()
        val plan = RepairSessionPlan(
            objectName = "office chair",
            issueSummary = "Loose seat",
            steps = (1..steps).map { n ->
                RepairStepSession(
                    number = n,
                    title = "Step $n",
                    action = "Do thing $n",
                    instruction = "Detailed instruction $n",
                    targetComponent = "target $n",
                    toolKnown = false,
                    tool = null,
                    toolNote = null,
                    warning = null,
                    expectedState = "Visible result $n",
                    confirmationRequired = true,
                )
            },
            safetyLevel = "LOW",
            safetyMessage = "",
            requiresAcknowledgement = false,
        )
        assertTrue(engine.loadPlan(plan))
        return engine
    }

    @Test
    fun `confirm does not complete a step - advance after pass does`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.ConfirmStep)
        // READY_FOR_VERIFICATION alone must NOT mark the step complete...
        assertEquals(EnginePhase.READY_FOR_VERIFICATION, engine.state.value.phase)
        assertEquals(0, engine.state.value.completedCount)

        // ...only the UI's Advance after a PASS does.
        engine.dispatch(RepairIntent.Advance) // UI dispatched onVerified → PASS
        assertEquals(RepairSessionStatus.IN_PROGRESS, engine.state.value.status)
        assertEquals(1, engine.state.value.completedCount)
        assertEquals(com.fixlens.app.repair.RepairStepState.COMPLETE, engine.state.value.stepStates[1])
        assertEquals(2, engine.state.value.currentStepNumber)
    }

    @Test
    fun `fail outcome keeps the step open and returns to working state`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.ConfirmStep)
        // UI dispatched onIncomplete → FAIL: no Advance is legal here.
        engine.dispatch(RepairIntent.MarkAttempted)
        assertEquals(EnginePhase.WAITING_FOR_USER, engine.state.value.phase)
        assertEquals(0, engine.state.value.completedCount)
        assertEquals(1, engine.state.value.currentStepNumber)

        // The user can retry: confirm again → verify again → pass this time.
        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.Advance)
        assertEquals(1, engine.state.value.completedCount)
    }

    @Test
    fun `uncertain outcome never completes the step`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.MarkAttempted) // onUncertain path
        assertEquals(EnginePhase.WAITING_FOR_USER, engine.state.value.phase)
        assertEquals(0, engine.state.value.completedCount)
    }

    @Test
    fun `no verification path exists that skips advance`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.ConfirmStep)
        // Any attempt to complete without Advance must be a no-op.
        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.MarkAttempted)
        assertEquals(0, engine.state.value.completedCount)
        assertEquals(1, engine.state.value.currentStepNumber)
        // Advance is ONLY legal from READY_FOR_VERIFICATION: after a FAIL the
        // user is back in WAITING_FOR_USER, so Advance is a no-op...
        engine.dispatch(RepairIntent.Advance)
        assertEquals(0, engine.state.value.completedCount)
        // ...and completion requires confirm → (PASS) → advance again.
        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.Advance)
        assertEquals(1, engine.state.value.completedCount)
    }

    @Test
    fun `last step advance completes the session`() {
        val engine = engineWith(steps = 1)
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.Advance)
        assertEquals(EnginePhase.REPAIR_COMPLETE, engine.state.value.phase)
        assertEquals(RepairSessionStatus.COMPLETED, engine.state.value.status)
        assertEquals("All guided steps completed.", engine.completionMessage())
        assertTrue(!engine.completionMessage().contains("verified"))
    }

    @Test
    fun `verification result types carry evidence separately from claims`() {
        val pass = VerificationResult.Verified("Head flush with bracket.")
        val fail = VerificationResult.FailedMismatch("Screw still protrudes.", null)
        val uncertain = VerificationResult.Inconclusive(
            explanation = "Cannot tell from this angle.",
            betterViewInstruction = "Move the camera closer to the screw.",
        )
        assertEquals("Head flush with bracket.", pass.explanation)
        assertEquals("Screw still protrudes.", fail.explanation)
        assertEquals(null, fail.betterViewInstruction)
        assertEquals("Move the camera closer to the screw.", uncertain.betterViewInstruction)
    }
}
