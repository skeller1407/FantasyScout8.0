# FantasyScout v24

- Official Bundesliga.com matchday pages added as fixture source.
- Players are enriched with their next known opponent, home/away flag and matchday.
- Fixture source is exposed as Bundesliga.com.
- No opponent is invented when fixture retrieval fails.
- Opponent strength remains a conservative heuristic until standings/form data is connected.


## v25 – dynamische Gegnerbewertung
- Gegnerstärke wird nicht mehr statisch hinterlegt.
- Neue öffentliche Bundesliga.com-Tabellenquelle liefert die aktuelle Tabellenposition des Gegners.
- Gegner-Rang und daraus abgeleitete Stärke 1–5 werden an die Android-App übertragen.
- Bei nicht verfügbarer Tabelle bleibt die Gegnerstärke leer statt erfunden zu werden.
