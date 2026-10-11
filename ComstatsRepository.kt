package de.fantasyscout.app.data

import org.jsoup.Jsoup
import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Loads public Comunio market-value lists directly from Comstats. No hosted backend or API key. */
class ComstatsRepository {
    private val season = java.time.LocalDate.now().year

    fun fetchSnapshot(): LiveSnapshot {
        val players = linkedMapOf<String, LivePlayer>()
        val warnings = mutableListOf<String>()
        val base = "https://stats.comunio.de/search?direc=ASC&endGd=34&orderBy=clubid&season=$season&startGd=1"
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        queue.add(base)
        // Follow Comstats' own pagination links instead of relying on limited Top-N lists.
        // The guard prevents unexpected pagination loops or runaway requests.
        while (queue.isNotEmpty() && visited.size < 40) {
            val pageUrl = queue.removeFirst()
            if (!visited.add(pageUrl)) continue
            try {
                val document = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                    .timeout(20000)
                    .get()
                var parsedOnPage = 0
                document.select("table tr").forEach rowLoop@ { row ->
                    val cells = row.select("td")
                    if (cells.isEmpty()) return@rowLoop
                    val name = row.select("a").map { it.text().trim() }
                        .firstOrNull { it.length in 2..55 && !it.matches(Regex("\\d+[.]?")) && !it.contains("Top ", true) }
                        ?: return@rowLoop
                    val images = row.select("img[alt], img[title]").map {
                        it.attr("alt").ifBlank { it.attr("title") }.trim()
                    }.filter { it.isNotBlank() }
                    val position = when {
                        images.any { it.equals("Keeper", true) || it.equals("Goalkeeper", true) } -> "GK"
                        images.any { it.equals("Defender", true) } -> "DEF"
                        images.any { it.equals("Striker", true) || it.equals("Forward", true) } -> "FWD"
                        images.any { it.equals("Midfielder", true) } -> "MID"
                        else -> null
                    }
                    val marketValue = cells.flatMap { cell ->
                        Regex("\\d{1,3}(?:\\.\\d{3})+").findAll(cell.text().replace('\u00a0', ' ')).mapNotNull { match ->
                            match.value.replace(".", "").toLongOrNull()
                        }.toList()
                    }.filter { it >= 10000L }.maxOrNull() ?: return@rowLoop
                    val club = images.firstOrNull {
                        !it.equals("Keeper", true) && !it.equals("Goalkeeper", true) &&
                            !it.equals("Defender", true) && !it.equals("Midfielder", true) &&
                            !it.equals("Striker", true) && !it.equals("Forward", true) &&
                            !it.equals("Trend", true)
                    } ?: row.select("a[title], a[aria-label]").map {
                        it.attr("title").ifBlank { it.attr("aria-label") }.trim()
                    }.firstOrNull { it.isNotBlank() } ?: "Unbekannter Verein"
                    val id = Normalizer.normalize(name.lowercase(Locale.GERMANY), Normalizer.Form.NFD)
                        .replace(Regex("\\p{Mn}+"), "")
                        .replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    if (id.isBlank()) return@rowLoop
                    val delta = Regex("[+−-]\\s*\\d{1,3}(?:\\.\\d{3})+")
                        .findAll(row.text()).mapNotNull {
                            it.value.replace(" ", "").replace("−", "-").replace("+", "").replace(".", "").toLongOrNull()
                        }.firstOrNull()
                    val previous = players[id]
                    players[id] = LivePlayer(
                        id = id, name = name, club = club, position = position ?: previous?.position ?: "UNK",
                        status = "Statusdaten nicht in Comstats enthalten",
                        comunioValue = marketValue, kickbaseValue = null,
                        fantasyScore = previous?.fantasyScore ?: 50,
                        fantasyLabel = "Comstats-Marktwert",
                        fantasyReasons = listOf("Spielerdaten direkt von Comstats geladen"),
                        comunioDelta = delta ?: previous?.comunioDelta,
                        kickbaseDelta = null,
                        marketHistory = previous?.marketHistory ?: emptyList()
                    )
                    parsedOnPage++
                }
                if (parsedOnPage == 0 && visited.size == 1) {
                    warnings += "Die Comstats-Spielersuche lieferte keine lesbaren Spielerzeilen."
                }
                // Comstats generates pagination URLs for the complete search result. Keep the
                // search parameters and follow only links within the same player-search page.
                document.select("a[href]").forEach { a ->
                    val href = a.absUrl("href")
                    if (href.startsWith("https://stats.comunio.de/search") &&
                        (href.contains("startLimit=") || href.contains("page=")) && href !in visited && !queue.contains(href)) {
                        queue.add(href)
                    }
                }
            } catch (e: Exception) {
                warnings += "Comstats-Seite konnte nicht geladen werden: ".plus(e.message ?: "Netzwerkfehler")
            }
        }
        if (players.isEmpty()) throw IllegalStateException(
            warnings.firstOrNull() ?: "Comstats hat keine Spielerdaten geliefert. Bitte Internetverbindung prüfen."
        )
        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY))
        warnings += "Quelle: stats.comunio.de (Comstats-Spielersuche); geladene eindeutige Spieler: ${players.size}."
        warnings += "Comstats liefert Comunio-Marktwerte, keine Kickbase-Marktwerte."
        return LiveSnapshot(
            players = players.values.sortedWith(compareBy<LivePlayer> { it.club.lowercase(Locale.GERMANY) }.thenBy { it.name.lowercase(Locale.GERMANY) }),
            news = emptyList(), alerts = emptyList(), syncedAt = timestamp,
            warnings = warnings, fromCache = false
        )
    }
}

