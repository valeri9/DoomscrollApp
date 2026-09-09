# Doomscroll

A private, local-only Android app that interrupts doomscrolling — and only doomscrolling.

Unlike blanket app blockers, it classifies *what you're doing inside* an app. Scrolling Instagram Reels
triggers an intervention; reading your DMs does not. Nothing leaves the phone: no cloud, no accounts,
no analytics. All data lives in a local Room database.

Built for a Samsung S24 Ultra (One UI), Android 8.0+.

## How it works

An `AccessibilityService` watches only the apps you monitor, classifies the current screen as
**doomscroll** or **legit** from its view hierarchy, and shows a breathing overlay when you scroll a
doomscroll feed. Screens it doesn't recognise are left alone — it fails closed, so it will never
interrupt a conversation it doesn't understand.

A separate low-frequency `UsageStatsManager` sync provides whole-phone screen time history, combined
with the intervention log in one dashboard.

## Setup

See `docs/PERMISSIONS.md` for the on-device permission checklist.
