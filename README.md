# PusoAlalay

A Filipino and English heart-health journal for Android, built with Kotlin and Jetpack Compose. It is an academic thesis prototype: a personal record-keeping tool, not a diagnostic or medical device.

**Status (23 October 2026): version 1.1 builds and passes its automated checks on GitHub Actions** (33 unit tests, lint, APK verification). It has not yet been tested on a phone or emulator. See docs/BUILD_REPORT.md.

## Install on your phone

1. On your phone, open the latest release: https://github.com/MigzRoxas16/PusoAlalay/releases/latest
2. Tap **PusoAlalay-v1.1-debug.apk** to download it.
3. Open the file and allow "Install unknown apps" when Android asks.
4. If Play Protect warns about an unknown developer, choose "Install anyway". This is expected for a personal debug build.

Requires Android 8.0 or later. Version 1.1 installs over 1.0 and keeps existing records (they move into the first account you create).

## Features

- **On-device accounts:** sign up, sign in, sign out and delete account. Each person on the phone gets a separate journal. Passwords are stored only as salted PBKDF2 hashes. There is no online account and no password recovery.
- **Health dashboard:** latest heart rate, blood pressure and weight, plus recent entries.
- **Heart journal:** add, edit and delete readings copied from a measuring device, with date and time (Asia/Manila), filters by type, period and note text.
- **Symptom tracking:** checklist (fatigue, dizziness, palpitations, shortness of breath, swelling, chest discomfort), severity (mild, moderate, severe) and notes, with an emergency-care notice.
- **Personal trend statistics:** average, typical variation (SD), median, range and days-with-data coverage. Readings far from your own earlier readings are marked "unusual compared with your earlier readings" (modified z-score above 3.5 after at least 5 earlier readings). This is a prompt to review, not a diagnosis.
- **Reminders:** one daily logging reminder, plus appointment reminders 1 hour, 2 hours or 1 day before. Notifications never show health details or appointment titles.
- **Reports:** PDF with summary statistics, graphs, missing-data notes, symptom counts and all entries; CSV for spreadsheets. Saved only where you choose.
- **Privacy:** works fully offline with no internet permission. Values, notes and appointment details are encrypted with an Android Keystore key. Backups are disabled and screenshots are blocked.
- **Languages and theme:** Filipino and English; dark theme.

Not included: smartwatch / Health Connect import, PPG signal-quality analysis (research only), cloud sync, medication tracking, clinical alerts.

## How the APK is built

Every push to this repository runs .github/workflows/build-apk.yml on GitHub Actions. It installs Android SDK 35, runs the unit tests, builds the debug APK, checks it with aapt2 and apksigner, runs lint, and publishes the APK as a GitHub release. Open the Actions tab to see each run.

## Build on your own computer (optional)

1. Install Android Studio with Android SDK Platform 35 and Build Tools 35.0.0.
2. Open this folder (the one containing settings.gradle.kts) and let Gradle sync. Use JDK 17.
3. Run the app with the Run button, or from a terminal:

        gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

   The APK appears at app/build/outputs/apk/debug/app-debug.apk.

For a signed release APK, see docs/RELEASE_SIGNING.md. No signing keys are stored in this repository.

## Project layout

| Folder | Contents |
|---|---|
| app/src/main/java/ph/pusoalalay/app/ui | Compose screens: sign-in, onboarding, home, records, form, reminders, settings |
| .../domain | Records, validation, statistics, appointments, account rules (pure Kotlin, unit-tested) |
| .../data | Room database and migration, Keystore encryption, password hashing, settings |
| .../reminders | Daily and appointment notifications |
| .../export | PDF and CSV reports |
| app/src/test | JVM unit tests |
| app/src/androidTest | Instrumented tests (need a device or emulator) |
| docs | Build report, testing checklist, security notes, research scope, sources |
| tools | Source audit and Windows check script |

Pinned versions: Android Gradle Plugin 8.9.2, Gradle 8.11.1, Kotlin 2.1.10, Compose BOM 2025.03.00, Room 2.7.0, compile/target SDK 35, minimum SDK 26, Java 17.

## Important limits

- This app records information that the user enters. It does not measure anything, diagnose conditions, or detect emergencies. For sudden or severe symptoms, seek emergency care regardless of what the app shows.
- Uninstalling the app or clearing its data deletes all records and accounts. Exported reports cannot be imported back.
- Accounts on the same phone share one encryption key; separation between them is enforced by the app.
- Reminders can be delayed by Android battery management.
- No clinical validation, medical endorsement or institutional affiliation is claimed. Read docs/TESTING.md and docs/SECURITY.md before any thesis demonstration.
