# FantasyScout v29 – Direct Comstats

FantasyScout lädt die Spielersuche von Comstats direkt aus der Android-App. Es wird kein eigener Backend-Server und kein API-Schlüssel benötigt.

## Änderungen in v29
- Statt Top-25/Top-50-Listen wird die vollständige paginierte Comstats-Spielersuche abgefragt.
- Ergebnisse werden anhand einer normalisierten Spieler-ID dedupliziert.
- Der Tab „Spieler“ bietet Filterchips für erkannte Bundesliga-Vereine, Positionsfilter und die Suche.
- Daten werden lokal gespeichert, damit zuvor geladene Daten bei späteren Verbindungsproblemen verfügbar bleiben.

## Marktwerte
Comstats ist die Datenquelle für Comunio-Marktwerte. Diese Quelle liefert keine Kickbase-Marktwerte; das entsprechende Feld bleibt leer, statt erfundene Werte zu zeigen.

## Build
GitHub Actions kann mit `.github/workflows/android-build.yml` gestartet werden. Lokal:

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Einschränkung
Die Daten werden aus einer öffentlichen Website gelesen. Wenn Comstats seine Seitenstruktur ändert, die Suche eine andere Pagination verwendet oder Zugriffe blockiert, muss der Parser angepasst werden. Diese v29-Projektdatei wurde hier noch nicht mit einem Android-Build verifiziert.
