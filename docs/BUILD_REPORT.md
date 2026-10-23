# PusoAlalay v1.1 — Build Report (23 Oct 2026)

Repository: https://github.com/MigzRoxas16/PusoAlalay

## Final compilation status
Built on GitHub Actions (ubuntu-24.04, JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.10). Final green run: 39949485468 (commit fc0d049). Unit tests, debug build, androidTest build, APK verification and lint (now a blocking check) all passed.
Artifact `PusoAlalay-v1.1-debug` contains `PusoAlalay-v1.1-debug.apk` (debug-signed, versionCode 2, versionName 1.1, minSdk 26 / Android 8.0, targetSdk 35) and its SHA-256 file.
SHA-256: `5d12b0a78992308e03829a3fd6c64bc063768d2544ae35fcd64bc4e2de15e15f`. `apksigner`: signed with the standard Android Debug certificate.

## What changed in v1.1
| Area | Change |
|---|---|
| Accounts | On-device sign-up / sign-in / sign-out / delete account. PBKDF2-HMAC-SHA256 (120,000 iterations, random 16-byte salt); 5 failed sign-ins cause a 30 s wait. Each account sees only its own records and appointments. Records saved in v1.0 are moved into the first account created. No internet, no password recovery. |
| Symptoms | Checklist (fatigue, dizziness, palpitations, shortness of breath, swelling, chest discomfort), severity (mild/moderate/severe), optional note, emergency-care notice. |
| Trend statistics | Per type and period: count, mean, sample SD, median, range, days-with-data coverage. "Unusual compared with your earlier readings" flag: modified z-score (median/MAD, Iglewicz & Hoaglin) above 3.5 against up to 30 previous readings, after at least 5. Labelled as a review prompt, not a diagnosis. |
| Appointments | Add/delete appointments with reminders 1 h / 2 h / 1 day before. Title and note encrypted; notifications never show the title; reminders restored after reboot. |
| PDF report | Owner name, per-type summary statistics, coverage (missing-data) notes, line graphs, symptom frequencies, unusual-reading count, then all entries. CSV adds symptoms and severity columns. |
| Database | Room v1 to v2 migration (ownerId column, accounts and appointments tables). Generated schema checked in CI against the migration SQL: identical tables, columns and indexes. |
| Lint | Fixed `windowLightNavigationBar` NewApi error (API 27 attribute with minSdk 26) and made lint a blocking CI check. |

## Tests actually performed
| Test | Result |
|---|---|
| 33 JVM unit tests (validation, CSV, reminders, statistics, unusual-reading detection, appointments, account rules, password hashing) | Passed locally (Kotlin 2.0.21 + JUnit 4.13.2) and on GitHub Actions |
| Full Gradle compile (assembleDebug, assembleDebugAndroidTest) | Passed on GitHub Actions |
| Migration SQL applied to a v1 SQLite database | Passed locally (sqlite3); schema matches Room's exported v2 schema |
| `aapt2 dump badging`, `apksigner verify` | Run in CI on every build |
| Lint | v1.0 and the first v1.1 build had 1 lint error (NewApi, theme attribute) hidden by a non-blocking step; fixed afterwards |

## Not performed
No emulator or phone testing has been done: launch, onboarding, sign-up/sign-in, upgrade from v1.0 on a real phone, record entry, symptom checklist, statistics display, appointment notifications, language switching, PDF/CSV output and FLAG_SECURE all still need checking on a device. The 5 instrumented tests were not run.

## Known limitations
- Accounts are local only; forgotten passwords cannot be recovered. All accounts share the phone's Keystore key, so separation between accounts is enforced by the app, not by separate encryption keys.
- Unusual-reading flags depend on how much and how regularly a person records; they are not validated for clinical use.
- Not implemented: smartwatch / Health Connect import, PPG measurement-quality analysis (research only), cloud sync, medication tracking.
- The GitHub repository now holds the complete project, including the Gradle wrapper (official 8.11.1 files, checksums verified), docs and tools. Each successful build on main also publishes the APK as the GitHub release v1.1.

See `docs/RELEASE_SIGNING.md` for producing a signed release APK.
