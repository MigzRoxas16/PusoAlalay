# Creating a signed release APK

No release keystore or password is included in this project, and no release APK has been signed.

## 1. Create your own upload key (once)

In Android Studio: **Build → Generate Signed App Bundle / APK → APK → Create new...**
or on the command line (JDK 17):

```
keytool -genkeypair -v -keystore pusoalalay-release.jks -alias pusoalalay -keyalg RSA -keysize 4096 -validity 10000
```

Keep `pusoalalay-release.jks` and its passwords outside the repository and back them up.
Losing the key means future updates cannot be installed over the old app.

## 2. Point Gradle at the key without committing secrets

Create `keystore.properties` next to `settings.gradle.kts` (it is listed in `.gitignore`):

```
storeFile=../keys/pusoalalay-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=pusoalalay
keyPassword=YOUR_KEY_PASSWORD
```

`storeFile` is relative to the project root. Alternatively set the environment variables
`PUSOALALAY_KEYSTORE`, `PUSOALALAY_KEYSTORE_PASSWORD`, `PUSOALALAY_KEY_ALIAS`, `PUSOALALAY_KEY_PASSWORD`
(for CI, store them as encrypted repository secrets, never in the workflow file).

## 3. Build and verify

```
.\gradlew.bat :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (R8 minification is enabled).
If no signing values are present, Gradle produces `app-release-unsigned.apk` instead, which phones will not install.

Verify the signature before sharing:

```
%ANDROID_HOME%\build-tools\35.0.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\app-release.apk
```

Test the release build on a device before a demo: minification can expose problems that the debug build does not.
Raise `versionCode` for every build you install over a previous one.
