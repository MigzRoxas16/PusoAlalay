# Third-party components

The Gradle wrapper scripts and JAR were obtained from the official Gradle repository at tag `v8.11.1`:
- https://github.com/gradle/gradle/blob/v8.11.1/gradlew
- https://github.com/gradle/gradle/blob/v8.11.1/gradlew.bat
- https://github.com/gradle/gradle/blob/v8.11.1/gradle/wrapper/gradle-wrapper.jar

Gradle is distributed under Apache License 2.0. The original script headers are retained, and the wrapper JAR includes its license under META-INF/LICENSE.

Wrapper SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
Gradle 8.11.1 binary distribution SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.
Both were checked against https://gradle.org/release-checksums/ on 23 October 2026.

Build dependencies are declared in the Gradle files: Android Gradle Plugin, AndroidX/Compose/Room, Kotlin/Kotlinx coroutines, KSP, JUnit, and AndroidX Test. No copied patient data, photos, research waveforms, commercial assets, or generated model weights are shipped. Material icons come through the declared AndroidX dependency. Review and retain dependency license notices when preparing a release.
