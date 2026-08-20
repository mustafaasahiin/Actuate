# Apple-Inspired Mobile App — Style Guide

## Philosophy & Elevation
The design language is built on restraint: a near-white canvas, generous breathing room, and a single vivid blue accent for intentional primary actions. Hierarchy is created through clear surface shifts and hairline borders—never drop shadows on buttons, cards, or containers.

---

## Tokens — Colors

| Name | Hex Value | Role |
|------|-----------|------|
| Apple Blue | `#0071e3` | **Primary Action / Active State:** Filled buttons, toggle switches, active selection states. Used sparingly. |
| Link Blue | `#0066cc` | **Secondary Action:** Outlined button borders, text buttons, inline interactive text. |
| Signal Blue | `#2997ff` | **Accent:** Status indicators, subtle decorative accents, active icon strokes. |
| Carbon | `#1d1d1f` | **Primary Text:** Main body copy, primary titles, high-contrast text. |
| Frost | `#f5f5f7` | **App Canvas / Background:** Default background surface across app screens. |
| Ice | `#f4f8fb` | **Elevated Surface Wash:** Subtle background tint for featured list items or highlight cards. |
| Smoke | `#333333` | **Secondary Text / Borders:** Secondary headers, active neutral icons, medium-contrast borders. |
| Graphite | `#474747` | **Tertiary Text:** Subtitles, tertiary labels, list item descriptions. |
| Ash | `#707070` | **Muted Text:** Captions, disabled text states, subtle metadata. |
| Mist | `#858585` | **Hairline Dividers:** Light functional lines, item dividers, input borders on light surfaces. |
| Onyx | `#000000` | **Maximum Contrast:** True black for dark mode surfaces or high-emphasis headings. |
| Pebble | `#e2e2e5` | **Control Surfaces:** Inactive button fills, disabled surface states, chip backgrounds. |

---

## Tokens — Typography

### Font Families
- **Display / Headers:** `SF Pro Display` (Fallback: `Inter`, `system-ui`)
- **Body / Controls:** `SF Pro Text` (Fallback: `Inter`, `system-ui`)

### Type Scale

| Role | Size (sp/dp) | Line Height Ratio | Letter Spacing | Font Weight |
|------|--------------|-------------------|----------------|-------------|
| caption | 12dp | 1.33 | -0.022em | 400 |
| body-sm | 14dp | 1.29 | -0.016em | 400 |
| body | 17dp | 1.47 | -0.016em | 400 |
| subheading | 21dp | 1.24 | -0.005em | 300 / 600 |
| heading-sm | 28dp | 1.18 | 0.007em | 600 |
| heading | 40dp | 1.14 | 0.011em | 600 |
| display | 56dp | 1.07 | 0.011em | 600 |

*Note: Tighten tracking proportionally as text size increases to retain clarity.*

---

## Tokens — Spacing & Radii

### Spacing Scale (4dp Grid)
- `4dp`, `8dp`, `12dp`, `16dp` (standard padding/margin), `20dp`, `24dp`, `40dp`, `48dp`

### Corner Radii
- **Fully Rounded / Capsule:** `980dp` (Pill Buttons, Tags, Chips, Search Bars)
- **Rounded Containers:** `8dp` (Cards, Inputs, Dialogs, Bottom Sheets)

---

## Core Component Principles

### Primary Action Button (Filled Pill)
- **Background:** `#0071e3` (Apple Blue)
- **Text:** `#F4F8FB` (Ice), 17dp, Medium/Regular weight
- **Shape:** Fully rounded capsule (`980dp`)
- **Elevation:** Flat (0dp)

### Secondary Action Button (Outlined Pill)
- **Border:** 1dp solid `#0066cc` (Link Blue)
- **Text:** `#0066cc` (Link Blue), 17dp
- **Background:** Transparent
- **Shape:** Fully rounded capsule (`980dp`)

### Text Input Fields
- **Background:** `#F5F5F7` (Frost)
- **Border:** 1dp solid `#858585` (Mist) or `#333333` (Smoke)
- **Focus State Ring:** `#0071e3` (Apple Blue)
- **Shape:** `8dp` rounded corners

### Content Cards & List Items
- **Surface:** `#F5F5F7` or pure White depending on contrast
- **Border:** Hairline divider (`1dp` in `#858585`)
- **Corner Radius:** `8dp`
- **Elevation:** No drop shadows; structural separation via background color or hairline rules.

---

## Design Guidelines & Guardrails

### Do
- Use `#0071e3` **strictly** for active/filled touch targets—one color, one job.
- Set main body text to `17dp` with slight negative letter-spacing for high legibility.
- Use full capsule shapes (`980dp`) for interactive buttons and chips, and strictly `8dp` for containers/inputs.
- Reserve weight `300` for subtitles to give the screen a clean, minimal voice.

### Don't
- Do not apply drop shadows to buttons, cards, or bottom sheets.
- Do not use `#0071e3` for general body text or icons—use `#0066cc` for clickable text links and `#2997ff` for status/accents.
- Do not mix corner radii beyond `8dp` for containers and `980dp` for pills.