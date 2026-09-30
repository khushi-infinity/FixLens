package com.fixlens.app

import com.fixlens.app.repair.EnginePhase
import com.fixlens.app.repair.RepairEngine
import com.fixlens.app.repair.RepairIntent
import com.fixlens.app.repair.RepairSessionPlan
import com.fixlens.app.repair.RepairSessionStatus
import com.fixlens.app.repair.RepairStepSession
import com.fixlens.app.repair.RepairStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairEngineTest {

    private fun plan(steps: Int = 2, requiresAck: Boolean = false) = RepairSessionPlan(
        objectName = "office chair",
        issueSummary = "Loose seat",
        steps = (1..steps).map { n ->
            RepairStepSession(
                number = n,
                title = "Step $n",
                action = "Do thing $n",
                instruction = "Detailed instruction $n",
                targetComponent = "target $n",
                toolKnown = n == 1,
                tool = if (n == 1) "Phillips screwdriver" else null,
                toolNote = if (n == 1) null else "Use the appropriate screwdriver for this screw.",
                warning = if (n == 1) "Support the seat." else null,
                expectedState = "Visible result $n",
                confirmationRequired = true,
            )
        },
        safetyLevel = if (requiresAck) "MEDIUM" else "LOW",
        safetyMessage = if (requiresAck) "Work slowly." else "",
        requiresAcknowledgement = requiresAck,
    )

    private fun engineWith(steps: Int = 2, requiresAck: Boolean = false): RepairEngine {
        val engine = RepairEngine()
        assertTrue(engine.loadPlan(plan(steps, requiresAck)))
        return engine
    }

    @Test
    fun `loadPlan rejects empty plans`() {
        val engine = RepairEngine()
        assertFalse(
            engine.loadPlan(
                RepairSessionPlan("o", "i", emptyList(), "LOW", "", false),
            ),
        )
    }

    @Test
    fun `full lifecycle advances through every spec phase`() {
        val engine = engineWith(steps = 2)

        assertEquals(EnginePhase.PLAN_READY, engine.state.value.phase)
        assertEquals(RepairSessionStatus.NOT_STARTED, engine.state.value.status)

        engine.dispatch(RepairIntent.Start)
        assertEquals(EnginePhase.STEP_ACTIVE, engine.state.value.phase)
        assertEquals(RepairSessionStatus.IN_PROGRESS, engine.state.value.status)

        engine.dispatch(RepairIntent.ConfirmStep)
        assertEquals(EnginePhase.READY_FOR_VERIFICATION, engine.state.value.phase)

        engine.dispatch(RepairIntent.Advance)
        assertEquals(EnginePhase.NEXT_STEP, engine.state.value.phase)
        assertEquals(2, engine.state.value.currentStepNumber)
        assertEquals(1, engine.state.value.completedCount)
        assertEquals(RepairStepState.COMPLETE, engine.state.value.stepStates[1])

        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.Advance)
        assertEquals(EnginePhase.REPAIR_COMPLETE, engine.state.value.phase)
        assertEquals(RepairSessionStatus.COMPLETED, engine.state.value.status)
        assertEquals("All guided steps completed.", engine.completionMessage())
    }

    @Test
    fun `acknowledgement gate blocks steps until accepted for medium risk`() {
        val engine = engineWith(requiresAck = true)

        // Start is refused while the warning is unacknowledged...
        engine.dispatch(RepairIntent.Start)
        assertEquals(EnginePhase.PLAN_READY, engine.state.value.phase)
        assertEquals(true, engine.state.value.plan.requiresAcknowledgement)

        // ...and Accept both clears the gate and starts the session.
        engine.dispatch(RepairIntent.AcknowledgeSafety)
        assertFalse(engine.state.value.plan.requiresAcknowledgement)
        assertEquals(EnginePhase.STEP_ACTIVE, engine.state.value.phase)
        assertEquals(RepairSessionStatus.IN_PROGRESS, engine.state.value.status)
    }

    @Test
    fun `acknowledge is a no-op without medium risk`() {
        val engine = engineWith(requiresAck = false)
        val before = engine.state.value
        engine.dispatch(RepairIntent.AcknowledgeSafety)
        assertEquals(before, engine.state.value)
    }

    @Test
    fun `skip marks step blocked and moves on`() {
        val engine = engineWith(steps = 2)
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.SkipStep)
        assertEquals(2, engine.state.value.currentStepNumber)
        assertEquals(RepairStepState.BLOCKED_BY_USER, engine.state.value.stepStates[1])
        assertEquals(0, engine.state.value.completedCount)

        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.Advance)
        assertEquals(EnginePhase.REPAIR_COMPLETE, engine.state.value.phase)
        assertEquals("Guided steps finished — 1 step skipped.", engine.completionMessage())
    }

    @Test
    fun `pause and resume round trip`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.Pause)
        assertEquals(RepairSessionStatus.PAUSED, engine.state.value.status)
        engine.dispatch(RepairIntent.Resume)
        assertEquals(RepairSessionStatus.IN_PROGRESS, engine.state.value.status)
    }

    @Test
    fun `cancel aborts and later completion wording never claims confirmation`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.Cancel)
        assertEquals(RepairSessionStatus.ABORTED, engine.state.value.status)
        // No code path may emit "Repair confirmed" (spec §17.6).
        assertFalse(engine.completionMessage().contains("confirmed", ignoreCase = true))
    }

    @Test
    fun `invalid intents are ignored from unreachable states`() {
        val engine = engineWith()
        // Advance/Confirm before Start must do nothing.
        engine.dispatch(RepairIntent.ConfirmStep)
        engine.dispatch(RepairIntent.Advance)
        engine.dispatch(RepairIntent.SkipStep)
        assertEquals(EnginePhase.PLAN_READY, engine.state.value.phase)
        assertEquals(1, engine.state.value.currentStepNumber) // pointer at first step, nothing active
        assertEquals(RepairSessionStatus.NOT_STARTED, engine.state.value.status)
        assertEquals(0, engine.state.value.completedCount)
    }

    @Test
    fun `advance without verification phase is a no-op`() {
        val engine = engineWith()
        engine.dispatch(RepairIntent.Start)
        engine.dispatch(RepairIntent.Advance) // not READY_FOR_VERIFICATION
        assertEquals(EnginePhase.STEP_ACTIVE, engine.state.value.phase)
        assertEquals(1, engine.state.value.currentStepNumber)
    }
}
