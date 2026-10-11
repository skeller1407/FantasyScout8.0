# FantasyScout v25 – Build

## Voraussetzungen
- Android Studio (aktueller Stable-Kanal)
- JDK 21
- Android SDK 36 + Android SDK Build-Tools
- Internetzugang beim ersten Gradle-Wrapper-/Dependency-Download

## Debug APK (Windows)
Im Projektordner:

```bat
gradlew.bat assembleDebug
```

APK:

`app\build\outputs\apk\debug\app-debug.apk`

## Debug APK (macOS/Linux)

```bash
./gradlew assembleDebug
```

APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Release

```bash
./gradlew assembleRelease
```

Die Release-Variante ist noch nicht mit einem persönlichen Release-Keystore signiert. Für eine veröffentlichungsfähige APK/AAB muss ein eigener Keystore konfiguriert werden.

## Hinweis zum Wrapper
`gradlew` und `gradlew.bat` sind enthalten. Der Wrapper-JAR wird beim ersten Aufruf automatisch aus dem offiziellen Gradle-Repository geladen, falls er lokal noch nicht vorhanden ist.
