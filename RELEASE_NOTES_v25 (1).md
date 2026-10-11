# FantasyScout v25

## Dynamische Gegnerbewertung
- Gegnerstärke wird nicht mehr statisch anhand des Vereinsnamens gesetzt.
- FantasyScout liest die aktuelle Bundesliga-Tabelle aus der öffentlichen Bundesliga.com-Tabelle.
- Die Tabellenposition des kommenden Gegners wird als `opponentRank` an die App übertragen.
- Daraus wird eine konservative Gegnerstärke von 1 bis 5 abgeleitet:
  - Rang 1–4: 5/5
  - Rang 5–8: 4/5
  - Rang 9–12: 3/5
  - Rang 13–15: 2/5
  - Rang 16–18: 1/5
- Die Aufstellungsbewertung nutzt diese reale Gegnerstärke weiterhin zusammen mit Heim/Auswärts und Fantasy-Score.
- Wenn die Tabelle nicht erreichbar ist, bleibt `opponentRank`/`opponentStrength` leer. Es werden keine Werte erfunden.

## Android
- Gegner-Rang wird in der Spieltagsanzeige dargestellt, wenn vorhanden.
- Cache und API-Verarbeitung unterstützen `opponentRank`.

## Datenquelle
- Öffentliche Bundesliga-Tabelle: Bundesliga.com, Saison 2026/27.
- Öffentlicher Spielplan: Bundesliga.com, Saison 2026/27.

## Build-Vorbereitung
- Gradle Wrapper-Konfiguration für Gradle 9.4 vorbereitet.
- Android-Build-Dokumentation ergänzt.
- GitHub Actions Workflow für reproduzierbaren Debug-APK-Build ergänzt.
- Debug-Build erlaubt lokale HTTP-Backends; Release erwartet HTTPS.
- Kein Release-Keystore im Projekt enthalten.
