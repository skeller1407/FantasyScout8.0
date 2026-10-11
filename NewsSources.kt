package de.fantasyscout.app.data

data class NewsSource(val name: String, val homepage: String, val priority: Int)

val defaultNewsSources = listOf(
    NewsSource("LigaInsider", "https://www.ligainsider.de/", 1),
    NewsSource("Kicker", "https://www.kicker.de/", 1),
    NewsSource("Bundesliga.com", "https://www.bundesliga.com/de/bundesliga", 1),
    NewsSource("Sport1", "https://www.sport1.de/", 2),
    NewsSource("Sky Sport", "https://sport.sky.de/", 2)
)
