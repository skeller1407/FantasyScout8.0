# FantasyScout-Backend auf Render bereitstellen

Diese Projektversion enthält eine Render-Blueprint-Datei (`render.yaml`). Das Backend verwendet ausschließlich die Python-Standardbibliothek; es sind keine zusätzlichen Python-Pakete erforderlich.

## Bereitstellung

1. Lade den Inhalt dieses Projektordners in ein GitHub-Repository hoch (oder aktualisiere das bestehende Repository). `render.yaml` muss im Repository-Stamm liegen und der Ordner `backend/` direkt daneben.
2. Melde dich bei https://render.com/ an und verbinde dein GitHub-Konto.
3. Wähle **New + → Blueprint** und das Repository mit `render.yaml`.
4. Prüfe den Service `fantasyscout-backend` und klicke auf **Apply**. Render baut und startet das Backend.
5. Nach erfolgreichem Deployment zeigt Render eine URL in der Form `https://fantasyscout-backend.onrender.com` an. Die tatsächliche Subdomain kann abweichen.
6. Öffne `https://DEINE-URL/health`. Erwartete Antwort: `{"ok": true}`. Öffne danach `https://DEINE-URL/v1/sync`, um den ersten Datensync auszulösen. Anschließend liefert `https://DEINE-URL/v1/data` den gespeicherten Datensatz.
7. Trage die tatsächliche Basis-URL (ohne `/v1/data`) in FantasyScout unter **Einstellungen → Backend → API-URL** ein und speichere sie.

## Wichtige Hinweise

- Die URL oben ist ein Beispiel, noch kein bereits laufender Server. Ein tatsächlicher Render-Service muss in deinem Render-Konto angelegt werden. Ich kann keine Bereitstellung in deinem Konto auslösen, solange ich keinen autorisierten Zugang dazu habe.
- Der kostenlose Render-Webservice kann bei Inaktivität einschlafen. Der erste Aufruf kann dann verzögert sein. Der lokale Dateispeicher unter `/tmp` ist nicht dauerhaft und kann bei Neustarts/Deployments verloren gehen; `/v1/sync` lädt die Daten erneut. Für dauerhafte Historie wäre ein persistenter Datenspeicher nötig.
- `GET /v1/sync` fragt mehrere Datenquellen ab und kann bei langsamen Quellen länger dauern. Wenn Android einen Timeout meldet, sollte der Sync serverseitig in einen Hintergrundjob verschoben werden.
- Die Spielerliste und Marktwerte hängen davon ab, ob Comstats und die übrigen konfigurierten Quellen erreichbar sind und sich deren Seitenformat nicht geändert hat. Prüfe `warnings` in `/v1/data`, falls Daten fehlen.
