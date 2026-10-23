# Data handling and threat boundaries

This implementation keeps the core application offline. It requests only notification permission and receipt of the system boot broadcast. The manifest declares no internet, location, contacts, broad storage, camera, microphone, or health-data access.

## Accounts (v1.1)

Accounts exist only in the local Room database. A password is never stored: each account keeps a random 16-byte salt and a PBKDF2-HMAC-SHA256 hash (120,000 iterations, 256-bit output), compared in constant time. Usernames are normalised to lowercase. Five failed sign-ins trigger a 30-second wait for that app session, and unknown usernames are hashed too so timing does not reveal which usernames exist.

Each record and appointment row carries the owning account ID, and every query filters by it. All accounts share the same Android Keystore key, so separation between accounts on the same phone is enforced by the app rather than by separate encryption keys. Someone with root access or a debug build could read other accounts' data. There is no password recovery; a forgotten password leaves that account's records unreachable through the app.

Appointment titles and notes are encrypted like records. Appointment and reminder times stay in plain columns so reminders can be restored after a reboot; notifications never show the title.

## Storage

Room stores a random record ID, kind, time, and encrypted payload. The payload includes the measurement values, notes, origin label, and creation time. AES-256-GCM uses a newly generated IV for each encryption. The record ID, kind, and time are authenticated as additional data to detect metadata swapping/tampering.

The secret key is generated in Android Keystore. Hardware-backed protection is not assumed or claimed. The key is not exported. If a record cannot be decrypted, the observer reports a storage error, preserves database rows, and blocks new saves until the journal is readable or the user explicitly deletes all records. The app does not silently drop unreadable entries or replace the database.

Database migrations must be added deliberately for later schema changes; no destructive fallback is enabled.

## Backup and recovery

Cloud backup and Android device transfer are excluded through manifest and backup resources. The app never uploads records itself. OEM behavior still requires device checks.

The user is told that uninstalling/clearing app data can destroy records and encryption keys. There is no encrypted backup/import workflow in this version. A CSV/PDF is a readable report, not a restorable database archive.

## Exports

Only the explicitly selected journal entries are captured for export. Android's system document picker controls the destination. A selected document provider may be cloud-backed; the app explains that exported files leave its encryption boundary.

The app does not email or share the report automatically. CSV cells are quoted, internal quotes doubled, and leading formula characters neutralized with an apostrophe. Partial files can remain if an export fails. No silent export retry or external-file deletion is performed.

## Device and UI

FLAG_SECURE is set on the main activity, and notification text contains no measurement or symptom. Notification visibility is private. All manifest receivers are non-exported. Reminder PendingIntents are immutable.

Since v1.1 the journal requires an on-device account. The session stays signed in until the user signs out, so anyone holding the unlocked phone while signed in can view that journal; there is no biometric lock. Saved form state is managed by Android activity restoration; it is not a separately encrypted persistent-draft store. Device compromise, root access, malicious keyboards, and user-chosen report sharing are not solved by this design.

## Pending validation

No penetration test, legal compliance assessment, Keystore device-matrix test, or independent security audit has been performed. Review the app and test its behavior before entering real health data.
