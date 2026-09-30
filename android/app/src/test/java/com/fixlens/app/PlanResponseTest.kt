package com.fixlens.app

import com.fixlens.app.data.BackendConfig
import com.fixlens.app.network.ApiClient
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.DiagnosisDto
import com.fixlens.app.network.PlanStatuses
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PlanResponseTest {

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

    private fun minimalDiagnosisDto() = DiagnosisDto(
        objectName = "office chair",
        objectCategory = "furniture",
        components = emptyList(),
        issueSummary = "The seat is loose.",
        likelyCauses = emptyList(),
        confidence = 0.8,
        safetyLevel = "LOW",
        safetyReason = "No hazards.",
        observations = emptyList(),
        needsBetterView = false,
        betterViewInstruction = null,
    )

    private fun planResponseJson(
        status: String = "PLAN_READY",
        withPlan: Boolean = true,
        requiresAck: Boolean = false,
    ) = """
        {
          "status": "$status",
          "plan": ${if (withPlan) planJson() else "null"},
          "safety": {
            "decision": "GUIDE",
            "user_message": "No significant hazards detected in this image.",
            "professional_type": "NONE"
          },
          "requires_acknowledgement": $requiresAck,
          "provider_used": "gemini",
          "duration_ms": 3120
        }
    """.trimIndent()

    private fun planJson() = """
        {
          "object_name": "office chair",
          "issue_summary": "The seat is loose on its mounting plate.",
          "steps": [
            {
              "number": 1,
              "title": "Tighten the mounting screws",
              "action": "Turn each screw clockwise.",
              "instruction": "Work in a star pattern, half a turn at a time.",
              "target_component": "seat mounting screws",
              "tool_known": true,
              "tool": "Phillips screwdriver",
              "tool_note": null,
              "warning": null,
              "expected_state": "Seat no longer rocks.",
              "confirmation_required": true
            },
            {
              "number": 2,
              "title": "Check the seat",
              "action": "Push the seat side to side.",
              "instruction": "Try to rock the seat; re-tighten if it moves.",
              "target_component": "seat plate",
              "tool_known": false,
              "tool": null,
              "tool_note": "Use the appropriate screwdriver for this screw.",
              "warning": null,
              "expected_state": "Seat stays fixed.",
              "confirmation_required": true
            }
          ],
          "notes": null
        }
    """.trimIndent()

    @Test
    fun `plan posts diagnosis json to plan endpoint and parses steps`() = runTest {
        server.enqueue(
            MockResponse().setBody(planResponseJson()).addHeader("Content-Type", "application/json"),
        )
        val response = client().plan(minimalDiagnosisDto())
        val recorded = server.takeRequest()
        assertEquals("/api/v1/plan", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"diagnosis\""))
        assertTrue(body.contains("office chair"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))

        assertEquals(PlanStatuses.PLAN_READY, response.status)
        assertEquals(2, response.plan!!.steps.size)
        assertEquals("Tighten the mounting screws", response.plan!!.steps[0].title)
        assertEquals(true, response.plan!!.steps[0].toolKnown)
        assertEquals("Phillips screwdriver", response.plan!!.steps[0].tool)
        assertEquals(false, response.plan!!.steps[1].toolKnown)
        assertNull(response.plan!!.steps[1].tool)
        assertEquals("Use the appropriate screwdriver for this screw.", response.plan!!.steps[1].toolNote)
        assertEquals(false, response.requiresAcknowledgement)
    }

    @Test
    fun `plan blocked high risk has no plan object`() = runTest {
        server.enqueue(
            MockResponse().setBody(planResponseJson(status = "BLOCKED_HIGH_RISK", withPlan = false))
                .addHeader("Content-Type", "application/json"),
        )
        val response = client().plan(minimalDiagnosisDto())
        assertEquals(PlanStatuses.BLOCKED_HIGH_RISK, response.status)
        assertNull(response.plan)
    }

    @Test
    fun `plan medium risk surfaces acknowledgement flag`() = runTest {
        server.enqueue(
            MockResponse().setBody(planResponseJson(requiresAck = true))
                .addHeader("Content-Type", "application/json"),
        )
        val response = client().plan(minimalDiagnosisDto())
        assertEquals(PlanStatuses.PLAN_READY, response.status)
        assertEquals(true, response.requiresAcknowledgement)
    }

    @Test
    fun `plan error maps backend detail`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(503)
                .setBody("""{"detail":"The AI service is temporarily unavailable or rate-limited. Please try again in a moment."}"""),
        )
        try {
            client().plan(minimalDiagnosisDto())
            fail("Expected ApiError.Http")
        } catch (e: ApiError.Http) {
            assertEquals(503, e.code)
            assertTrue(e.message!!.contains("temporarily unavailable"))
        }
    }

    @Test
    fun `assembly parses ready plan with parts and steps`() = runTest {
        server.enqueue(
            MockResponse().setBody(assemblyJson(status = "READY")).addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("parts", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val response = client().planAssembly(temp)
        temp.delete()
        val recorded = server.takeRequest()
        assertEquals("/api/v1/assembly", recorded.path)

        assertEquals("READY", response.status)
        val plan = response.assemblyPlan!!
        assertEquals("metal shelving unit", plan.objectName)
        assertEquals(2, plan.parts.size)
        assertEquals("vertical frame post", plan.parts[0].name)
        assertEquals(true, plan.orderConfident)
        assertEquals(1, plan.steps.size)
        assertEquals("Stand the two frame posts upright", plan.steps[0].title)
    }

    @Test
    fun `assembly needs better view carries view request and no steps`() = runTest {
        server.enqueue(
            MockResponse().setBody(assemblyJson(status = "NEEDS_BETTER_VIEW", orderConfident = false))
                .addHeader("Content-Type", "application/json"),
        )
        val temp = File.createTempFile("parts", ".jpg").apply { writeBytes(byteArrayOf(9)) }
        val response = client().planAssembly(temp)
        temp.delete()

        assertEquals("NEEDS_BETTER_VIEW", response.status)
        val plan = response.assemblyPlan!!
        assertEquals(false, plan.orderConfident)
        assertEquals(0, plan.steps.size)
        assertTrue(plan.requestedView!!.contains("bolt holes"))
    }

    private fun assemblyJson(status: String, orderConfident: Boolean = true) = """
        {
          "status": "$status",
          "assembly_plan": {
            "object_name": "metal shelving unit",
            "parts": [
              {"name": "vertical frame post", "kind": "OBSERVED", "status": "four visible"},
              {"name": "M6 hex bolts", "kind": "OBSERVED", "status": "eight visible"}
            ],
            "order_confident": $orderConfident,
            "steps": ${if (orderConfident) """
            [
              {
                "number": 1,
                "title": "Stand the two frame posts upright",
                "action": "Place the posts parallel, 60 cm apart.",
                "instruction": "Set the posts on the floor with the bolt holes facing inward.",
                "target_component": "vertical frame posts",
                "tool_known": false,
                "tool": null,
                "tool_note": null,
                "warning": null,
                "expected_state": "Two posts standing parallel.",
                "confirmation_required": true
              }
            ]
            """ else "[]"},
            "requested_view": ${if (orderConfident) "null" else "\"Show the flat side of the largest frame piece with its bolt holes.\""},
            "safety_level": "LOW",
            "safety_reason": "Loose parts only."
          },
          "safety": {
            "decision": "GUIDE",
            "user_message": "No significant hazards detected in this image.",
            "professional_type": "NONE"
          },
          "provider_used": "gemini",
          "duration_ms": 4010
        }
    """.trimIndent()
}
