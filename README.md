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

## What it does

**Detection.** An `AccessibilityService` watches only the apps you monitor, classifies the
current screen from its view hierarchy, and shows a breathing overlay when you scroll a
doomscroll feed. Screens it doesn't recognise are left alone — it fails closed, so it will
never interrupt a conversation it doesn't understand.

**Intervention.** Daytime is a 10-second breathing circle followed by a one-tap reason.
Inside the night window (00:00–08:00 by default) it gets harder: longer breathing, a typed
sentence instead of a tap, a delay before *Continue anyway* becomes usable, and +10 seconds
for every reopen in the same night.

**Learn Mode.** Instagram's and TikTok's internal view ids are private and change between
releases, so the shipped rules are guesses. Learn Mode captures the real ids off your phone
and turns them into rules with one tap.

**Stats.** A separate, low-frequency `UsageStatsManager` sync provides whole-phone screen
time — including history from before the app was installed — shown next to the intervention
log, so "3h on Instagram" sits beside "caught doomscrolling 6 times, 4 of them after
midnight".

## Building

Requires a JDK 21 and the Android SDK (compileSdk 37). `gradle.properties` points at a local
JDK; change `org.gradle.java.home` if yours lives elsewhere.

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
```

## Setup

See `docs/PERMISSIONS.md` for the on-device permission checklist.
