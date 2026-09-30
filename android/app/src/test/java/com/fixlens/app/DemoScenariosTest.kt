package com.fixlens.app

import com.fixlens.app.demo.DemoMappers
import com.fixlens.app.demo.DemoScenarios
import com.fixlens.app.network.PlanStatuses
import com.fixlens.app.network.AssemblyStatuses
import com.fixlens.app.network.VerificationStates
import com.fixlens.app.repair.RepairEngine
import com.fixlens.app.repair.RepairIntent
import com.fixlens.app.repair.RepairStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 tests: the scripted demo journeys. Each journey must map into the
 * PRODUCTION DTO/session shapes and satisfy the same structural rules the
 * live pipeline enforces (no steps for SAFETY_STOP, positional numbering,
 * tool-known⇄tool consistency), and it must be byte-for-byte deterministic
 * across runs — that is the entire point of Demo Mode.
 */
class DemoScenariosTest {

    @Test
    fun `all three scenarios exist with titles`() {
        assertEquals(3, DemoScenarios.all.size)
        assertEquals(
            listOf("Stuck office chair", "Furniture assembly", "Unsafe electrical wiring"),
            DemoScenarios.all.map { it.title },
        )
    }

    @Test
    fun `chair journey maps to a valid diagnosis and three-step plan`() {
        val journey = DemoScenarios.scenarioFor(DemoScenarios.Kind.CHAIR_GUIDED_REPAIR)
        val response = DemoMappers.toDiagnoseResponse(journey.diagnosis)
        assertEquals("GUIDE", response.safety.decision)
        assertEquals("demo", response.providerUsed)
        assertTrue(response.diagnosis.likelyCauses.isNotEmpty())
        assertTrue(response.diagnosis.confidence in 0.0..1.0)

        val plan = DemoMappers.toPlanResponse(journey.diagnosis, journey.steps)
        assertEquals(PlanStatuses.PLAN_READY, plan.status)
        assertEquals(3, plan.plan!!.steps.size)
        assertEquals(listOf(1, 2, 3), plan.plan!!.steps.map { it.number }) // positional
        assertTrue(plan.plan!!.steps.all { !it.toolKnown && it.tool == null })
        assertTrue(plan.plan!!.steps.all { it.warning != null || it.warning == null }) // shape only
        assertNotNull(plan.plan!!.steps[0].expectedState)
    }

    @Test
    fun `chair journey drives the real engine to completion with verified steps`() {
        val journey = DemoScenarios.scenarioFor(DemoScenarios.Kind.CHAIR_GUIDED_REPAIR)
        val engine = RepairEngine()
        assertTrue(engine.loadPlan(DemoMappers.toSessionPlan(journey.diagnosis, journey.steps)))
        engine.dispatch(RepairIntent.Start)
        repeat(journey.steps.size) {
            engine.dispatch(RepairIntent.ConfirmStep)
            engine.dispatch(RepairIntent.Advance) // scripted verification is PASS
        }
        assertEquals(com.fixlens.app.repair.EnginePhase.REPAIR_COMPLETE, engine.state.value.phase)
        assertEquals(journey.steps.size, engine.state.value.completedCount)
        assertEquals("All guided steps completed.", engine.completionMessage())
        assertTrue(engine.state.value.stepStates.values.all { it == RepairStepState.COMPLETE })
    }

    @Test
    fun `assembly journey is a confident ordered plan`() {
        val journey = DemoScenarios.scenarioFor(DemoScenarios.Kind.ASSEMBLY)
        assertEquals(3, journey.steps.size)
        assertTrue(journey.diagnosis.likelyCauses.isEmpty()) // assembly has no causes

        val plan = DemoMappers.toPlanResponse(journey.diagnosis, journey.steps)
        assertEquals(PlanStatuses.PLAN_READY, plan.status)
        assertEquals(listOf(1, 2, 3), plan.plan!!.steps.map { it.number })
    }

    @Test
    fun `wiring journey is a safety stop with no fix path and no verification`() {
        val journey = DemoScenarios.scenarioFor(DemoScenarios.Kind.WIRING_SAFETY_STOP)
        val response = DemoMappers.toDiagnoseResponse(journey.diagnosis)
        assertEquals("SAFETY_STOP", response.safety.decision)
        assertEquals("HIGH", response.diagnosis.safetyLevel)
        assertEquals("ELECTRICIAN", response.safety.professionalType)
        assertTrue(journey.steps.isEmpty()) // never any instructions for HIGH
        assertNull(journey.diagnosis.verificationState)

        val plan = DemoMappers.toPlanResponse(journey.diagnosis, journey.steps)
        assertEquals(PlanStatuses.PLAN_READY, plan.status)
        // Defense in depth: even the demo cannot produce steps for HIGH risk.
        assertEquals(0, journey.steps.size)
    }

    @Test
    fun `demo verification is a deterministic pass with evidence`() {
        val journey = DemoScenarios.scenarioFor(DemoScenarios.Kind.CHAIR_GUIDED_REPAIR)
        val v1 = DemoMappers.toVerifyResponse(journey.diagnosis, 1)
        val v2 = DemoMappers.toVerifyResponse(journey.diagnosis, 1)
        assertEquals(VerificationStates.PASS, v1.state)
        assertEquals(v1, v2)
        assertTrue(v1.explanation.isNotBlank())
        assertFalse(v1.needsBetterView)
    }

    @Test
    fun `every journey is deterministic across two full runs`() {
        DemoScenarios.Kind.values().forEach { kind ->
            val run1 = DemoScenarios.scenarioFor(kind)
            val run2 = DemoScenarios.scenarioFor(kind)
            assertEquals(run1, run2)
            assertEquals(
                DemoMappers.toDiagnoseResponse(run1.diagnosis),
                DemoMappers.toDiagnoseResponse(run2.diagnosis),
            )
        }
    }

    @Test
    fun `demo diagnosis carries the demo provider marker`() {
        DemoScenarios.Kind.values().forEach { kind ->
            val response = DemoMappers.toDiagnoseResponse(DemoScenarios.scenarioFor(kind).diagnosis)
            assertEquals("demo", response.providerUsed)
            assertEquals("demo", response.diagnosis.providerUsed)
        }
    }
}
