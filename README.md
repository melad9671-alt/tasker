# NEXORA

Adaptive, offline-first Android planner prototype built with Kotlin and Jetpack Compose.

## Current MVP
- Local-only onboarding profile (no account or network permission).
- Six planner sections: Daily, Weekly, Monthly, Yearly, Catch-up and Extra.
- Dynamic starter-plan generation with shuffled study subjects and configurable task durations.
- Manual task creation and local task persistence.
- Focus countdown; a study task is marked complete when its timer finishes.
- Dark UI with purple accents and rounded cards.

## Build
GitHub Actions builds a debug APK on pushes to `main` or `nexora-app`, and on manual workflow dispatch.
The artifact is named `nexora-debug-apk`.

Requirements: JDK 17, Android SDK 35, Gradle 8.9.
Run: `gradle --no-daemon assembleDebug`.

## Notes
This is an initial functional MVP, not yet the complete long-horizon planner described in the product brief. Academic calendars, birth-date-aware progression, weekly availability, commute/meal/sleep constraints, multi-day catch-up optimization, Room migrations, and full year streaming remain planned iterations.
