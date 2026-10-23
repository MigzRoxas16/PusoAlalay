# Technical references checked

Reviewed on 23 October 2026. Initial research was followed by a second pass across official Android, Kotlin, and Gradle sources. These establish API/build guidance; they do not prove this uncompiled app works.

- Android Gradle Plugin 8.9 compatibility: https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Compose dependency/compiler setup: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose BOM: https://developer.android.com/develop/ui/compose/bom
- Kotlin Compose compiler migration: https://kotlinlang.org/docs/compose-compiler-migration-guide.html
- Room persistence: https://developer.android.com/training/data-storage/room
- Room release history, including the pinned 2.7.0: https://developer.android.com/jetpack/androidx/releases/room
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android backup exclusions: https://developer.android.com/identity/data/autobackup
- Alarm scheduling and approximate delivery: https://developer.android.com/develop/background-work/services/alarms
- Notification permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Notification channels: https://developer.android.com/develop/ui/compose/notifications/channels
- Gradle compatibility: https://docs.gradle.org/current/userguide/compatibility.html
- Gradle distribution/wrapper checksums: https://gradle.org/release-checksums/

The project uses pinned historical versions to keep its configuration explicit. Some current documentation describes newer library generations (for example Room 3.x or AGP 9.x); those APIs were not copied into the pinned Room 2.7 / AGP 8.9 project.

The user's design brief and research PDF supplied the product direction and research boundary. No claim of independent clinical validation is made.
