# FixLens workshop notebook design

The Android interface uses a fixed light palette: warm paper (#F5F1E7), cream (#FCF9F2), charcoal (#343A36), terracotta (#995238), sage (#E3E9DF), and dusty lavender (#E2E3EE). Serif headings and readable native sans-serif text work offline and respect Android font scaling. The system bars use dark icons on paper.

Home prioritizes photo diagnosis, with separate live-camera and assembly entry points. Repair history and Pro remain accessible. The home page scrolls on smaller displays. Diagnosis, repair, assembly, verification, purchase, loading, and error screens share the same colors and notebook surfaces.

`ui/theme/Workshop.kt` provides cached paper grain and the drawn framing guide. `ui/WorkshopCamera.kt` provides shared capture controls for photo, live camera, assembly, verification, and scripted demos. Camera controls sit on opaque paper, with a cream outline backed by charcoal to maintain contrast against the preview. These guides are approximate framing aids, not tracked or detected component positions. The existing diagnosis, safety, billing, capture, and verification logic remains in place.

## Illustration provenance

Created using the built-in image generation tool. App asset: `android/app/src/main/res/drawable-nodpi/workshop_illustration.png`.

Final prompt:

> Use case: illustration-story. Asset type: wide editorial illustration for the home screen of FixLens, a mobile repair field guide. Create a lovingly hand-drawn workshop still life: a small adjustable wrench, a terracotta-handled screwdriver, a few screws and washers, and a simple wooden hinge joint, arranged loosely on pale warm cream paper (#F5F1E7). A lightly sketched curved arrow points to the hinge screw and an imperfect pencil circle surrounds it. Fine charcoal ink outlines with varied pressure, sketchy internal lines, desaturated watercolor washes in terracotta, muted sage, dusty lavender-blue and warm brown, subtle paper grain and painted shadows. Landscape composition, approximately 3:2, generous empty cream margin, isolated still life rather than room. Calm premium illustrated repair manual, tactile and human, simple readable silhouettes. No text, no lettering, no UI, no neon, no glossy effects, no digital gradient, no border.

## Verification

- Debug APK builds successfully.
- All 72 JVM tests pass.
- Android lint completes with zero errors; dependency, obsolete API/check, and unused-resource warnings remain.
- Emulator screenshots are saved alongside this document for visual review.

No live backend diagnosis or purchase is needed for the scripted visual checks.
