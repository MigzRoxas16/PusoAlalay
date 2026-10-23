# Verification status — 23 October 2026 (v1.1)

## v1.1 results (GitHub Actions, ubuntu-24.04, JDK 17, Gradle 8.11.1)

- Full Gradle build: `assembleDebug` and `assembleDebugAndroidTest` passed.
- 33 JVM unit tests passed (HealthRecordTest 15, StatisticsTest 8, AppointmentTest 4, AccountRulesTest 3, PasswordHasherTest 3). The same tests were also run locally with Kotlin 2.0.21 and JUnit 4.13.2.
- `lintDebug` passed as a blocking check after fixing one NewApi error (theme attribute).
- `aapt2 dump badging` and `apksigner verify` ran on the APK.
- Room v2 schema exported by the build was compared with `MIGRATION_1_2`: same tables, columns, primary keys and indexes. The migration SQL was also applied to a v1 SQLite database locally.
- Not run: the 5 instrumented tests and every manual device check below.

## Additional manual checks for v1.1 (pending)

21. Install v1.1 over v1.0 that has records; create the first account and confirm the old records appear in it and in no later account.
22. Create two accounts; confirm each sees only its own records and appointments, and that exports contain only the signed-in account's data.
23. Wrong password five times: confirm the 30-second wait. Sign out, sign in again, restart the app and confirm the session is kept.
24. Delete account with a wrong password (nothing deleted) and with the right password (account, records and appointments removed; reminders cancelled).
25. Symptom entry with only checklist items, with only a note, and with neither (rejected); severity can be cleared.
26. Statistics card and unusual-reading tags with fewer than 5, exactly 5, and many readings; blood pressure where only the diastolic value changes.
27. Appointment reminders 1 h / 2 h / 1 day before; reboot before the reminder; confirm the notification hides the title.
28. PDF: graphs, coverage notes, symptom counts and "no data" lines for missing types, in English and Filipino.

## Original v1.0 notes

## Actually performed

- Read the supplied design brief and mini-research paper.
- Cross-checked relevant Android, Kotlin, and Gradle documentation in two research passes.
- Ran `python tools/source_audit.py`: source-level XML, resource-reference, translation-placeholder, manifest, backup exclusion, lexical delimiter, wrapper-integrity, and selected color-contrast checks.
- Retrieved the Gradle wrapper from the official Gradle repository and compared its SHA-256 with Gradle's published checksum.
- Attempted `gradlew.bat --version`. It failed before compilation because Java/JAVA_HOME was absent.
- Checked for an Android SDK/emulator; none was available.
- Attempted access to the official toolchain download endpoints from the execution host; network access was blocked.

The source audit is **not** an Android build, an accessibility certification, a security audit, or a runtime test. It cannot detect all Kotlin type errors, dependency conflicts, device behavior, or UI layout problems.

## Included tests — not executed

| Suite | Cases | Intended verification |
|---|---:|---|
| HealthRecordTest (JVM) | 13 | Missing/nonfinite/invalid values; BP ordering; symptom note; date and length guards; decimal weight; Manila scheduling; CSV escaping and source/timestamp labels |
| StorageTest (Android) | 4 | Encrypted persistence/update/delete; randomized ciphertext; metadata tamper rejection; missing-key failure without data deletion |
| RecordFormTest (Android Compose) | 1 | Invalid data cannot submit; correction retains notes and sends the expected record |

Use a disposable emulator/test profile. The storage tests use an in-memory database and unique test key aliases. The Compose test mounts the form without the production journal.

Build and JVM checks:

```text
gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Device tests:

```text
gradlew.bat :app:connectedDebugAndroidTest
```

Review generated reports under `app/build/reports/`. Do not mark a check passed simply because a test file exists.

## Required manual device checks

All items below are pending.

1. First launch is empty and onboarding supports both languages.
2. Enter pulse, BP, decimal weight, and a multiline symptom note; close/reopen the app and verify persistence.
3. Edit an existing record. Verify one row changes, no duplicate appears, and its original identity is retained.
4. Rotate while editing. Switch record types and verify the per-type number fields retain their draft inputs.
5. Cancel a dirty form: verify the discard confirmation and the keep-editing path.
6. Reject missing values, reversed BP, future dates, nonfinite pasted numbers, and notes over 2,000 characters without losing the draft.
7. Backdate entries across Manila midnight; verify sorting, 7/30-day filters, and UTC/Manila export fields.
8. Use identical timestamps and constant values in a trend; confirm the graph stays within bounds and the list remains readable.
9. Test deletion cancellation, confirmed deletion, and delete-all with reminder cancellation.
10. Export CSV with quotes, commas, newlines, Filipino text, and notes beginning with =/+/-/@. Inspect the CSV in a text editor and spreadsheet application.
11. Export PDFs with zero prevented, one, many, and very long records. Visually inspect every page for clipping, missing text, and page numbering.
12. Rotate while the Android document picker is open. Cancel the picker and retry. Simulate an unavailable destination; check the error and remove any partial file.
13. Deny notifications, then allow them from Android settings; verify the switch reflects intent and the permission notice reflects actual delivery availability.
14. Change reminder time, disable/re-enable, reboot, change device timezone, and force-stop/reopen. Verify no duplicate reminder schedule and Manila interpretation.
15. Revoke notification permission after scheduling; confirm no crash and the off notice appears on resume.
16. Run offline, including airplane mode. Add/edit/delete/export to local storage.
17. Test at 320dp width, landscape, 200% font size, large display scaling, and TalkBack. Verify focus, labels, visible controls, dialog buttons, and scrolling.
18. Verify screenshots and recent-app thumbnails do not expose the journal.
19. Inspect the database on a disposable debug device: values/notes must be ciphertext, with only declared metadata visible. Test key loss without real records.
20. Evaluate loading/scrolling/export with a large synthetic journal explicitly marked as test data.

Do not demonstrate this as a clinically validated product or move it into patient use on the strength of source checks.
