package com.fixlens.app.demo

/**
 * Phase 7: pre-authored content for the three deterministic demo scenarios
 * (spec §13, trimmed to the Shipaton demo trio per the phase prompt:
 * stuck chair, furniture assembly, unsafe wiring).
 *
 * Everything here is PROVIDER-AGNOSTIC data. Demo Mode maps it into the very
 * same DTOs/session shapes the live pipeline produces, so the entire UX,
 * result screen, guidance, verification, safety stop, renders through the
 * unmodified production screens. No backend, no AI, no billing, no clock:
 * the content is constant, which is the point of a demo.
 *
 * Honesty: demo results are never presented as live AI. Every demo screen
 * carries the DEMO MODE banner, and the diagnosis result names the scenario.
 */
object DemoScenarios {

    /** The journey types, in menu order. */
    enum class Kind { CHAIR_GUIDED_REPAIR, ASSEMBLY, WIRING_SAFETY_STOP }

    data class Scenario(
        val kind: Kind,
        val title: String,
        val blurb: String,
    )

    val all: List<Scenario> = listOf(
        Scenario(
            Kind.CHAIR_GUIDED_REPAIR,
            "Stuck office chair",
            "Diagnosis → guided steps → camera verification",
        ),
        Scenario(
            Kind.ASSEMBLY,
            "Furniture assembly",
            "Parts recognition → build order → step guidance",
        ),
        Scenario(
            Kind.WIRING_SAFETY_STOP,
            "Unsafe electrical wiring",
            "Live hazard detection → safety stop → professional referral",
        ),
    )

    // ------------------------------------------------------------------
    // Journey 1, stuck office chair: full guided repair with verification
    // ------------------------------------------------------------------

    val chairDiagnosis: DemoDiagnosis = DemoDiagnosis(
        objectName = "office chair",
        objectCategory = "furniture",
        issueSummary = "The seat is stuck and will not rotate; the gas lift mechanism is seized.",
        components = listOf(
            DemoComponent("seat", observed = true, status = "stuck"),
            DemoComponent("gas lift cylinder", observed = true, status = "seized"),
            DemoComponent("central mechanism", observed = true, status = "corroded"),
            DemoComponent("casters", observed = true, status = "intact"),
            DemoComponent("mounting plate screws", observed = true, status = "tight"),
        ),
        likelyCauses = listOf(
            DemoCause("Corrosion in the gas lift mechanism", 0.7),
            DemoCause("Debris or hardened grease in the swivel bearing", 0.2),
        ),
        confidence = 0.91,
        observations = listOf(
            DemoObservation("Seat does not rotate relative to the base", observed = true),
        ),
        safetyLevel = "LOW",
        safetyMessage = "No significant hazards detected in this image.",
        decision = "GUIDE",
        verificationState = "PASS",
        verificationExplanation =
            "The seat assembly now turns freely relative to the base; the seized mechanism is no longer binding.",
    )

    val chairSteps: List<DemoStep> = listOf(
        DemoStep(
            title = "Clear around the chair base",
            action = "Move the chair to open floor space.",
            instruction =
                "Roll the chair away from the desk so you can reach the mechanism under the seat. " +
                    "Lock the casters if yours have locks.",
            targetComponent = "chair base",
            warning = null,
            expectedState = "The chair stands with clear access to the mechanism underneath.",
        ),
        DemoStep(
            title = "Work the seat back and forth",
            action = "Rotate the seat firmly in both directions.",
            instruction =
                "Grip the seat edges and rock the seat clockwise and counterclockwise with steady " +
                    "pressure to break the corrosion loose in the gas lift mechanism.",
            targetComponent = "gas lift cylinder",
            warning = "Keep your fingers clear of the mechanism gap under the seat.",
            expectedState = "The seat begins to turn with less resistance.",
        ),
        DemoStep(
            title = "Free the mechanism fully",
            action = "Apply penetrating oil and rotate again.",
            instruction =
                "Spray a short burst of penetrating oil where the gas lift meets the mechanism, " +
                    "wait two minutes, then rotate the seat several full turns to spread it.",
            targetComponent = "central mechanism",
            warning = "Do not spray oil onto the floor or casters, it becomes slippery.",
            expectedState = "The seat rotates freely through full turns.",
        ),
    )

    // ------------------------------------------------------------------
    // Journey 2, furniture assembly: parts, order, steps, verification
    // ------------------------------------------------------------------

    val assemblyDiagnosis: DemoDiagnosis = DemoDiagnosis(
        objectName = "metal shelving unit",
        objectCategory = "furniture",
        issueSummary = "Four uprights, cross-members, and hex bolts lie disassembled on the floor.",
        components = listOf(
            DemoComponent("vertical frame posts", observed = true, status = "four visible"),
            DemoComponent("cross-members", observed = true, status = "two visible"),
            DemoComponent("M6 hex bolts", observed = true, status = "eight visible"),
        ),
        likelyCauses = emptyList(), // assembly has no "causes"
        confidence = 0.95,
        observations = listOf(
            DemoObservation("Bolt-hole patterns on the uprights determine the order", observed = true),
        ),
        safetyLevel = "LOW",
        safetyMessage = "Loose parts only; nothing hot, energized, or structural in view.",
        decision = "GUIDE",
        verificationState = "PASS",
        verificationExplanation =
            "Two uprights stand parallel with bolt holes facing inward and the first cross-member seated.",
    )

    val assemblySteps: List<DemoStep> = listOf(
        DemoStep(
            title = "Stand the two frame posts upright",
            action = "Place the posts parallel, 60 cm apart.",
            instruction =
                "Set the posts on the floor with the bolt holes facing inward. Keep the tallest " +
                    "side up so the shelf sits level later.",
            targetComponent = "vertical frame posts",
            warning = null,
            expectedState = "Two posts stand parallel with matching holes facing inward.",
        ),
        DemoStep(
            title = "Seat the first cross-member",
            action = "Hook the cross-member into the lowest holes.",
            instruction =
                "Align the cross-member tabs with the lowest bolt holes and press until both ends " +
                    "seat fully into the uprights.",
            targetComponent = "cross-members",
            warning = null,
            expectedState = "The cross-member sits flush in the lowest holes on both sides.",
        ),
        DemoStep(
            title = "Fasten with hex bolts",
            action = "Thread one hex bolt into each end.",
            instruction =
                "Start each bolt by hand to avoid cross-threading, then snug them a quarter turn " +
                    "with the hex key. Do not fully tighten yet.",
            targetComponent = "M6 hex bolts",
            warning = "Hand-start the bolts, power tools easily strip these threads.",
            expectedState = "Both bolts hold the cross-member; the joint still flexes slightly.",
        ),
    )

    val assemblyParts: List<DemoComponent> = listOf(
        DemoComponent("vertical frame posts", observed = true, status = "four"),
        DemoComponent("cross-members", observed = true, status = "two"),
        DemoComponent("M6 hex bolts", observed = true, status = "eight"),
        DemoComponent("shelves", observed = true, status = "three"),
    )

    // ------------------------------------------------------------------
    // Journey 3, unsafe wiring: hazard → SAFETY_STOP → referral
    // ------------------------------------------------------------------

    val wiringDiagnosis: DemoDiagnosis = DemoDiagnosis(
        objectName = "three-gang light switch electrical box",
        objectCategory = "electrical",
        issueSummary =
            "Switches hang loose from the box with exposed current-carrying wiring and no wall plate.",
        components = listOf(
            DemoComponent("electrical wiring and wire connectors", observed = true, status = "exposed"),
            DemoComponent("switch bank", observed = true, status = "hanging loose"),
            DemoComponent("wall plate", observed = false, status = "missing"),
        ),
        likelyCauses = listOf(
            DemoCause("Removed or broken mounting screws let the switch bank fall out", 0.85),
        ),
        confidence = 0.95,
        observations = listOf(
            DemoObservation("Live conductors are exposed with no barrier", observed = true),
        ),
        safetyLevel = "HIGH",
        safetyMessage =
            "Exposed mains wiring is an electrocution and fire hazard. Do not touch the switches " +
                "or wiring. Keep children and pets away and contact a licensed electrician.",
        decision = "SAFETY_STOP",
        professionalType = "ELECTRICIAN",
        verificationState = null, // safety stop never verifies
        verificationExplanation = null,
    )

    /** One entry per scenario kind, the full pre-authored journey. */
    fun scenarioFor(kind: Kind): DemoJourney = when (kind) {
        Kind.CHAIR_GUIDED_REPAIR -> DemoJourney(diagnosis = chairDiagnosis, steps = chairSteps)
        Kind.ASSEMBLY -> DemoJourney(diagnosis = assemblyDiagnosis, steps = assemblySteps)
        Kind.WIRING_SAFETY_STOP -> DemoJourney(diagnosis = wiringDiagnosis, steps = emptyList())
    }
}
