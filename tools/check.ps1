$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
    if (-not $javaPath -or -not (Test-Path -LiteralPath $javaPath)) {
        throw 'JDK 17 is required. Set JAVA_HOME to a JDK installation, then rerun this script. See README.md.'
    }
    & $javaPath -version
    & .\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Gradle validation failed. Read the first Gradle error above.' }
    Write-Output 'Build and JVM checks passed. Connect a disposable test device and run .\gradlew.bat :app:connectedDebugAndroidTest next.'
} finally {
    Pop-Location
}
