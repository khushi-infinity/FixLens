# FixLens, Workshop Design Notes

The visual identity: an **illustrated workshop journal** rendered in Compose.
An AI technician that looks hand-made, not machine-made.

## Palette

Defined once in `android/app/src/main/java/com/fixlens/app/ui/theme/Theme.kt`
(`FixLensColors`). No screen hardcodes colors outside this object.

| Name | Hex | Role |
|---|---|---|
| Paper | `#F5F1E7` | App background; `paperSurface()` adds deterministic grain flecks |
| Cream | `#FCF9F2` | Cards, raised surfaces, on-primary text |
| Ink | `#343A36` | Text and outlines, deep charcoal, never pure black |
| MutedInk | `#636960` | Secondary text |
| Terracotta | `#995238` | Primary actions, progress, accents |
| ClayWash | `#EBD7C8` | Warm tint containers |
| Sage | `#E3E9DF` | Secondary washes, demo banner, success |
| SageInk | `#4E6657` | Positive / low-risk text |
| Lavender | `#E2E3EE` | Tertiary washes |
| BlueGray | `#586677` | Journal accents |
| Rule | `#CCCBBE` | Hairline borders and separators |
| Danger | `#A23E32` | Safety stop, HIGH risk |
| CameraInk | `#282E2A` | Sketch overlays drawn over the camera |

## Typography (`Type.kt`)

- **Serif display** (`FontFamily.Serif`) for `display*`, `headline*`,
  `titleLarge`, editorial "repair manual" voice.
- **Humanist sans** for everything instructional and functional, friendly,
  legible, respects accessibility font scaling. System families only: no font
  downloads, fully offline.

## Hand-drawn details (`ui/theme/Workshop.kt`)

- `Modifier.paperSurface()`, deterministic paper-grain flecks (seeded
  `Random(41)`), cached with `drawWithCache` so it never re-animates.
- `SketchTarget`, the camera annotation: an irregular ink contour and a
  pencilled arrow drawn with `Canvas`. Framing aid only; never claims to be a
  detected component.
- `CameraPaperBands`, opaque paper strips behind camera controls so
  charcoal-on-cream stays legible on any scene.
- `CameraFieldNote`, cream note card with uneven corner radii.
- Cards across the app use slightly irregular `RoundedCornerShape(r1, r2,
  r3, r4)` values, subtle imperfection, on purpose.

## Icon

`assets/icon_1024.png`, watercolor paper, sage/lavender/clay washes, a
sketchy double-ring charcoal lens, terracotta scan arc, dial ticks. Generated
programmatically; regenerate with the PIL script used for the submission.

## What we deliberately avoid

Neon gradients · glassmorphism · dark-futuristic dashboards · chat bubbles ·
purple-blue "AI app" color schemes · pure black text · pure white backgrounds.
