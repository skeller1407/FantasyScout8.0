# FantasyScout v13

## Beta-UI / Live-Daten
- Android UI nutzt ausschließlich das normalisierte Backend-Datenmodell.
- Demo-Spielerkatalog aus dem produktiven UI-Pfad entfernt.
- Spieler, News, Marktwerte, Status und Fantasy-Score werden aus `/v1/data` gelesen.
- Lokaler Snapshot-Cache für Offline-Fallback ergänzt.
- Live-/Offline-Status im Dashboard sichtbar.
- Manuelle Aktualisierung im Dashboard.
- Fantasy-Score 0–100 mit verständlichen Gründen auf Spielerkarte und Detailseite.
- Marktwertanzeige abhängig von Comunio/Kickbase.
- News der Favoriten werden dedupliziert und mit Original-Link dargestellt.

## Push / Background Sync
- Backend-URL und Worker verwenden denselben Settings-Speicher.
- Alerts werden lokal als bereits zugestellt gemerkt.
- Push nur für Favoriten.
- Verletzungs-/Statusmeldungen und sonstige relevante News können getrennt aktiviert werden.
- Keine wiederholten Benachrichtigungen bei unverändertem Alert.

## Backend
- Fantasy-Score wird bei jedem Sync aus Status und relevanten News berechnet.
- Alert-Payload enthält jetzt zusätzlich den Spielernamen.
- Python-Backend und Provider wurden per `py_compile` geprüft.

## Hinweis
Ein kompilierter APK-Build ist weiterhin erst mit einer vollständigen Android/Gradle-Buildumgebung möglich. Das Projekt ist als Android-Studio-Projekt vorbereitet.
