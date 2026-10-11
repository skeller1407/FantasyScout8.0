# FantasyScout v14

## Marktwert & Ranking
- Backend führt pro Spieler bis zu 14 historische Marktwert-Messpunkte mit.
- Comunio- und Kickbase-Änderungen werden als `comunioDelta` / `kickbaseDelta` ausgeliefert.
- Fantasy-Score berücksichtigt Marktwertbewegungen zusätzlich zu Status und News.
- Android zeigt aktuellen Marktwert, Veränderung und einen kompakten Verlauf.
- Detailansicht zeigt bis zu sieben letzte Messpunkte als Verlauf.

## Datenqualität
- Keine Demo-Spieler im produktiven Datenpfad.
- Letzter erfolgreicher Snapshot bleibt als Offline-Cache verfügbar.
- Kicker-News werden weiterhin dedupliziert und Spielerzuordnungen über das Matching vorgenommen.
- LigaInsider bleibt als autorisierter Status-Adapter vorgesehen; geschützte Seiten werden nicht automatisiert gescrapt.

## Prüfung
- Python-Backend und Provider mit `py_compile` geprüft.
- Backend-Sync mit isoliertem Smoke-Test ausgeführt.
- Android-Querverweise auf neue Datenfelder geprüft.
- Ein vollständiger Gradle/Android-APK-Build war in der aktuellen Umgebung nicht möglich, da kein Gradle/Android-SDK verfügbar ist.
