package de.fantasyscout.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fantasyscout.app.data.*
import de.fantasyscout.app.sync.SyncScheduler
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private enum class Manager { COMUNIO, KICKBASE }
private enum class Position(val label: String) { GK("TW"), DEF("Abwehr"), MID("Mittelfeld"), FWD("Stürmer"), UNKNOWN("Position unbekannt") }
private data class NewsArticle(val source:String,val title:String,val summary:String,val url:String,val age:String,val relevance:String,val injury:Boolean)
private data class Player(val id:String,val name:String,val club:String,val position:Position,val comunioValue:Long?,val kickbaseValue:Long?,val status:String,val fantasyScore:Int,val fantasyLabel:String,val fantasyReasons:List<String>,val news:List<NewsArticle>, val comunioDelta:Long?, val kickbaseDelta:Long?, val marketHistory:List<MarketPoint>, val opponent:String?, val homeMatch:Boolean?, val opponentRank:Int?, val opponentStrength:Int?, val startingProbability:Int?)

private fun position(value:String) = when(value.uppercase(Locale.GERMANY)) { "TW","GK"->Position.GK; "ABW","DEF"->Position.DEF; "MF","MID"->Position.MID; "ST","FWD","FORWARD","STRIKER"->Position.FWD; "UNKNOWN","UNBEKANNT","UNK"->Position.UNKNOWN; else->Position.UNKNOWN }
private fun relevance(value:String) = when(value.lowercase(Locale.GERMANY)) { "positive"->"🟢 positiv"; "watch"->"🟡 beobachten"; "high_negative","negative"->"🔴 Risiko"; else->"🔵 Information" }
private fun deltaText(value:Long?):String = when { value == null -> ""; value > 0 -> "↗ +${euro(value)}"; value < 0 -> "↘ ${euro(value)}"; else -> "→ 0" }
private fun euro(value:Long?):String { val v=value ?: return "–"; return when { v>=1_000_000->String.format(Locale.GERMANY,"%.1f Mio.",v/1_000_000.0); v>=1_000->String.format(Locale.GERMANY,"%.0f Tsd.",v/1_000.0); else->v.toString() } }
private fun scoreIcon(score:Int)=when { score>=70->"🟢"; score>=45->"🟡"; else->"🔴" }
private fun managerAccent(manager:Manager)=if(manager==Manager.COMUNIO) Color(0xFF087A42) else Color(0xFFC6FF00)
private fun managerSurface(manager:Manager)=if(manager==Manager.COMUNIO) Color(0xFFEAF7EE) else Color(0xFF101010)
private fun managerColors(manager:Manager)=if(manager==Manager.COMUNIO) lightColorScheme(
    primary=Color(0xFF087A42), onPrimary=Color.White,
    background=Color(0xFFEAF7EE), onBackground=Color(0xFF123322),
    surface=Color(0xFFF8FCF9), onSurface=Color(0xFF123322),
    surfaceVariant=Color(0xFFD8EDE0), onSurfaceVariant=Color(0xFF244B35)
) else darkColorScheme(
    primary=Color(0xFFC6FF00), onPrimary=Color(0xFF101010),
    background=Color(0xFF101010), onBackground=Color(0xFFF4F4F4),
    surface=Color(0xFF1B1B1B), onSurface=Color(0xFFF4F4F4),
    surfaceVariant=Color(0xFF292929), onSurfaceVariant=Color(0xFFE0E0E0)
)

private fun toPlayers(snapshot:LiveSnapshot):List<Player> {
    val newsByPlayer=snapshot.news.groupBy { it.playerId }
    return snapshot.players.map { p ->
        val articles=newsByPlayer[p.id].orEmpty().map { n->NewsArticle(n.source,n.title,n.summary,n.url,n.publishedAt,relevance(n.relevance),n.injury) }
        Player(p.id,p.name,p.club,position(p.position),p.comunioValue,p.kickbaseValue,p.status,p.fantasyScore,p.fantasyLabel,p.fantasyReasons,articles,p.comunioDelta,p.kickbaseDelta,p.marketHistory,p.opponent,p.homeMatch,p.opponentRank,p.opponentStrength,p.startingProbability)
    }
}

private class AppSettings(context:Context) {
    private val prefs=context.getSharedPreferences("fantasyscout_settings",Context.MODE_PRIVATE)
    fun manager()=if(prefs.getString("manager","COMUNIO")=="KICKBASE") Manager.KICKBASE else Manager.COMUNIO
    fun setManager(v:Manager)=prefs.edit().putString("manager",v.name).apply()
    fun pushInjuries()=prefs.getBoolean("push_injuries",true)
    fun pushNews()=prefs.getBoolean("push_news",true)
    fun pushMarket()=prefs.getBoolean("push_market",false)
    fun setPushInjuries(v:Boolean)=prefs.edit().putBoolean("push_injuries",v).apply()
    fun setPushNews(v:Boolean)=prefs.edit().putBoolean("push_news",v).apply()
    fun setPushMarket(v:Boolean)=prefs.edit().putBoolean("push_market",v).apply()
}
private class FavoritesStore(context:Context) {
    private val prefs=context.getSharedPreferences("fantasyscout",Context.MODE_PRIVATE)
    fun load()=prefs.getStringSet("favorites",emptySet()) ?: emptySet()
    fun save(v:Set<String>)=prefs.edit().putStringSet("favorites",v).apply()
}

class MainActivity:ComponentActivity(){
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);SyncScheduler.schedule(this);setContent{FantasyScoutApp()}}
}

@Composable fun FantasyScoutApp(){
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val settings=remember{AppSettings(context)}; val favoritesStore=remember{FavoritesStore(context)}
    var manager by remember{mutableStateOf(settings.manager())}; var tab by remember{mutableIntStateOf(0)}; var selected by remember{mutableStateOf<Player?>(null)}
    var favorites by remember{mutableStateOf(favoritesStore.load())}; var snapshot by remember{mutableStateOf<LiveSnapshot?>(HttpBackendRepository.loadCache(context))}; var loading by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}
    suspend fun refresh(){ loading=true; try { val fresh=withContext(Dispatchers.IO){ComstatsRepository().fetchSnapshot()}; snapshot=fresh; error=null; HttpBackendRepository.saveCache(context,fresh) } catch(e:Exception){error=e.message ?: "Comstats-Verbindung fehlgeschlagen"} finally{loading=false} }
    LaunchedEffect(Unit){ refresh() }
    val players=toPlayers(snapshot ?: LiveSnapshot(emptyList(),emptyList(),emptyList(),null,emptyList(),true)); val accent=managerAccent(manager); val surface=managerSurface(manager)
    MaterialTheme(colorScheme=managerColors(manager)){
        if(selected!=null) PlayerDetail(selected!!,manager,accent,favorites.contains(selected!!.id)||favorites.contains(selected!!.name),{val key=selected!!.id;if(favorites.contains(key))favorites=favorites-key else favorites=favorites+key;favoritesStore.save(favorites)},{selected=null})
        else Scaffold(topBar={TopAppBar(title={Text("FantasyScout",fontWeight=FontWeight.Bold)},actions={Text(if(manager==Manager.COMUNIO)"COMUNIO" else "KICKBASE",Modifier.padding(end=16.dp),fontWeight=FontWeight.Bold,color=accent)})},bottomBar={NavigationBar{listOf(Icons.Default.Home to "Home",Icons.Default.Star to "Meine Spieler",Icons.Default.SportsSoccer to "Spieler",Icons.Default.ShoppingCart to "Transfer",Icons.Default.Assessment to "Kader",Icons.Default.Event to "Aufstellung",Icons.Default.Article to "News",Icons.Default.Settings to "Einstellungen").forEachIndexed{i,item->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(item.first,null)},label={Text(item.second)})}}}){pad->
            Column(Modifier.padding(pad)){ if(loading)LinearProgressIndicator(Modifier.fillMaxWidth()); if(error!=null)ErrorBanner(error!!,accent){error=null};
                when(tab){0->Dashboard(Modifier.weight(1f),manager,accent,favorites,players,snapshot,onRefresh={refresh()},{selected=it});1->PlayerList(Modifier.weight(1f),"Meine Spieler",manager,accent,players.filter{favorites.contains(it.id)||favorites.contains(it.name)},favorites,{p->favorites=favoriteToggle(favorites,p,favoritesStore)},{selected=it});2->PlayerDatabase(Modifier.weight(1f),manager,accent,favorites,players,{p->favorites=favoriteToggle(favorites,p,favoritesStore)},{scope.launch { refresh() }},{selected=it});
3->TransferAdvisor(Modifier.weight(1f),manager,accent,players,{selected=it});
4->SquadPlanner(Modifier.weight(1f),manager,accent,players,favorites,{selected=it});
5->LineupPlanner(Modifier.weight(1f),manager,accent,players,favorites,{selected=it});
6->NewsScreen(Modifier.weight(1f),accent,favorites,players){selected=it};else->SettingsScreen(Modifier.weight(1f),manager,accent,settings){manager=it;settings.setManager(it)}}
            }
        }
    }
}
private fun favoriteToggle(current:Set<String>,p:Player,store:FavoritesStore):Set<String>{val key=p.id;if(current.contains(key)){val n=current-key;store.save(n);return n};val n=current+key;store.save(n);return n}

@Composable private fun ErrorBanner(text:String,accent:Color,onDismiss:()->Unit){Card(Modifier.fillMaxWidth().padding(8.dp),shape=RoundedCornerShape(12.dp)){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text("Offline: $text",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick=onDismiss){Text("OK",color=accent)}}}}

@Composable private fun Dashboard(modifier:Modifier,manager:Manager,accent:Color,favorites:Set<String>,players:List<Player>,snapshot:LiveSnapshot?,onRefresh:suspend()->Unit,onOpen:(Player)->Unit){
    val scope=rememberCoroutineScope()
    val favPlayers=players.filter{favorites.contains(it.id)||favorites.contains(it.name)}
    val ranked=players.sortedByDescending{it.fantasyScore}
    val buy=ranked.filter{it.fantasyScore>=70}.take(5)
    val hold=ranked.filter{it.fantasyScore in 45..69}.take(5)
    val winners=players.sortedByDescending{if(manager==Manager.COMUNIO) it.comunioDelta ?: Long.MIN_VALUE else it.kickbaseDelta ?: Long.MIN_VALUE}.filter{(if(manager==Manager.COMUNIO) it.comunioDelta else it.kickbaseDelta)!=null}.take(5)
    val losers=players.sortedBy{if(manager==Manager.COMUNIO) it.comunioDelta ?: Long.MAX_VALUE else it.kickbaseDelta ?: Long.MAX_VALUE}.filter{(if(manager==Manager.COMUNIO) it.comunioDelta else it.kickbaseDelta)!=null}.take(5)
    val riskiest=players.sortedBy{it.fantasyScore}.take(5)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Dein Fantasy-Dashboard",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text(if(snapshot?.fromCache==true)"Zuletzt synchronisiert · Offline-Cache" else "Live-Daten · ${snapshot?.syncedAt ?: "noch nicht synchronisiert"}",color=accent,style=MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick={scope.launch{onRefresh()}}){Icon(Icons.Default.Refresh,"Aktualisieren")}
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            StatCard("⭐",favPlayers.size.toString(),"Meine Spieler",accent)
            StatCard("👥",players.size.toString(),"Spieler",accent)
            StatCard("🔥",buy.size.toString(),"Top-Käufe",accent)
        }
        Spacer(Modifier.height(18.dp))
        RankingSection("🔥 Kaufempfehlungen","Score 70+ · aktuell besonders interessant",buy,manager,accent,onOpen)
        Spacer(Modifier.height(14.dp))
        RankingSection("🟡 Halten / beobachten","solide Werte, aber genauer hinschauen",hold,manager,accent,onOpen)
        Spacer(Modifier.height(14.dp))
        RankingSection("📈 Größte Gewinner","positive Marktwertentwicklung der letzten Syncs",winners,manager,accent,onOpen,showDelta=true)
        Spacer(Modifier.height(14.dp))
        RankingSection("📉 Größte Verlierer","negative Marktwertentwicklung der letzten Syncs",losers,manager,accent,onOpen,showDelta=true)
        Spacer(Modifier.height(14.dp))
        RankingSection("⚠️ Größtes Risiko","niedrigster Fantasy-Score",riskiest,manager,accent,onOpen)
        Spacer(Modifier.height(14.dp))
        Text("Deine Favoriten",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if(favPlayers.isEmpty()) Text("Noch keine Favoriten. Markiere Spieler mit dem ⭐.",style=MaterialTheme.typography.bodySmall)
        favPlayers.take(5).forEach{PlayerCard(it,manager,accent,true,onOpen,null)}
        if(snapshot?.warnings?.isNotEmpty()==true){Spacer(Modifier.height(12.dp));Text("Datenhinweise",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);snapshot.warnings.take(3).forEach{Text("• $it",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp))}}
    }
}

@Composable private fun RankingSection(title:String,subtitle:String,list:List<Player>,manager:Manager,accent:Color,onOpen:(Player)->Unit,showDelta:Boolean=false){
    Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text(subtitle,style=MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(5.dp))
    if(list.isEmpty()) Text("Aktuell keine ausreichenden Daten.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp))
    list.forEachIndexed{index,p->
        val delta=if(manager==Manager.COMUNIO)p.comunioDelta else p.kickbaseDelta
        Card(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable{onOpen(p)},shape=RoundedCornerShape(14.dp)){
            Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
                Text("${index+1}",fontWeight=FontWeight.Bold,modifier=Modifier.width(28.dp))
                Column(Modifier.weight(1f)){
                    Text(p.name,fontWeight=FontWeight.Bold)
                    Text("${p.club} · ${p.position.label}",style=MaterialTheme.typography.labelSmall)
                    Text("${scoreIcon(p.fantasyScore)} ${p.fantasyScore}/100 · ${p.fantasyLabel}",color=accent,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
                }
                Column(horizontalAlignment=Alignment.End){Text(euro(if(manager==Manager.COMUNIO)p.comunioValue else p.kickbaseValue),fontWeight=FontWeight.Bold);if(showDelta&&delta!=null)Text(deltaText(delta),style=MaterialTheme.typography.labelSmall)}
            }
        }
    }
}

@Composable private fun StatCard(icon:String,number:String,label:String,accent:Color){Card(Modifier.weight(1f),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(10.dp)){Text(icon);Text(number,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=accent);Text(label,style=MaterialTheme.typography.labelMedium)}}}

@Composable private fun PlayerDatabase(modifier:Modifier,manager:Manager,accent:Color,favorites:Set<String>,players:List<Player>,toggle:(Player)->Unit,refresh:()->Unit,open:(Player)->Unit){
    var query by remember{mutableStateOf("")}; var pos by remember{mutableStateOf<Position?>(null)}; var selectedClub by remember{mutableStateOf<String?>(null)}
    val clubs=players.map{it.club}.filter{it.isNotBlank()&&it!="Unbekannter Verein"&&it!="Bundesliga"}.distinct().sortedBy{it.lowercase(java.util.Locale.GERMANY)}
    val filtered=players.filter{(selectedClub==null||it.club==selectedClub)&&(pos==null||it.position==pos)&&(query.isBlank()||it.name.contains(query,true)||it.club.contains(query,true))}
    Column(modifier.fillMaxSize()) {
        Text("Bundesliga-Spieler · ${players.size}",Modifier.padding(16.dp),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=12.dp),singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("Spieler oder Verein suchen")})
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            FilterChip(selectedClub==null,{selectedClub=null},label={Text("Alle Vereine")})
            clubs.forEach{club->FilterChip(selectedClub==club,{selectedClub=if(selectedClub==club)null else club},label={Text(club)})}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=6.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){FilterChip(pos==null,{pos=null},label={Text("Alle Positionen")});Position.values().forEach{p->FilterChip(pos==p,{pos=if(pos==p)null else p},label={Text(p.label)})}}
        if(filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(Icons.Default.SportsSoccer,null,tint=accent,modifier=Modifier.size(42.dp))
                Spacer(Modifier.height(12.dp))
                Text(if(players.isEmpty()) "Noch keine Spielerdaten geladen" else "Keine passenden Spieler gefunden",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(if(players.isEmpty()) "Spielerdaten konnten nicht geladen werden. Prüfe deine Internetverbindung und aktualisiere erneut." else "Ändere deine Suche oder setze die Positionsfilter zurück.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                if(players.isEmpty()) Button(onClick=refresh,modifier=Modifier.padding(top=12.dp)){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp));Text("Spieler erneut laden")}
                else OutlinedButton(onClick={query="";pos=null},modifier=Modifier.padding(top=12.dp)){Text("Filter zurücksetzen")}
            }
        } else LazyColumn(Modifier.weight(1f)){items(filtered,key={it.id}){PlayerCard(it,manager,accent,favorites.contains(it.id)||favorites.contains(it.name),open,toggle)}}
    }
}
private fun actionFor(p:Player, manager:Manager):Pair<String,String>{
    val delta=if(manager==Manager.COMUNIO) p.comunioDelta else p.kickbaseDelta
    return when {
        p.fantasyScore < 35 -> "🔴 Verkaufen" to "Score und/oder Einsatzstatus kritisch"
        p.status.contains("verlet",true) || p.status.contains("ausfall",true) -> "⚠️ Beobachten" to "Verletzungs-/Ausfallrisiko"
        p.fantasyScore >= 75 && (delta ?: 0L) >= 0L -> "🟢 Kaufen / halten" to "Starker Score und positiver Trend"
        p.fantasyScore >= 60 && (delta ?: 0L) < 0L -> "🟡 Halten" to "Guter Score, aber Marktwert fällt"
        p.fantasyScore >= 45 -> "🟡 Beobachten" to "Solider Wert mit gemischten Signalen"
        else -> "🔵 Beobachten" to "Weitere News abwarten"
    }
}

@Composable private fun PlayerList(modifier:Modifier,title:String,manager:Manager,accent:Color,list:List<Player>,favorites:Set<String>,toggle:(Player)->Unit,open:(Player)->Unit){
    Column(modifier.fillMaxSize()){
        Text(title,Modifier.padding(start=16.dp,top=16.dp),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text(if(list.isEmpty()) "Noch keine Favoriten gespeichert." else "Deine persönliche Kaderübersicht · ${list.size} Spieler",Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=accent)
        LazyColumn{items(list.sortedWith(compareByDescending<Player>{it.fantasyScore}.thenByDescending{if(manager==Manager.COMUNIO) it.comunioDelta ?: Long.MIN_VALUE else it.kickbaseDelta ?: Long.MIN_VALUE}),key={it.id}){PersonalPlayerCard(it,manager,accent,open,toggle)}}
    }
}

@Composable private fun PersonalPlayerCard(p:Player,manager:Manager,accent:Color,open:(Player)->Unit,toggle:(Player)->Unit){
    val value=if(manager==Manager.COMUNIO)p.comunioValue else p.kickbaseValue
    val delta=if(manager==Manager.COMUNIO)p.comunioDelta else p.kickbaseDelta
    val action=actionFor(p,manager)
    Card(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp).clickable{open(p)},shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(14.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.club} · ${p.position.label}",style=MaterialTheme.typography.bodySmall)};IconButton(onClick={toggle(p)}){Icon(Icons.Default.Star,null,tint=accent)}}
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("${scoreIcon(p.fantasyScore)} ${p.fantasyScore}/100",fontWeight=FontWeight.Bold,color=accent);Text("${if(manager==Manager.COMUNIO)"Comunio" else "Kickbase"}: ${euro(value)}",fontWeight=FontWeight.SemiBold);if(delta!=null)Text(deltaText(delta),style=MaterialTheme.typography.bodySmall)}
            Text("${action.first} · ${action.second}",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=7.dp))
            Text("Status: ${p.status}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp))
            Text("News: ${p.news.size} · Gründe: ${p.fantasyReasons.size}",style=MaterialTheme.typography.labelSmall,color=Color.Gray,modifier=Modifier.padding(top=4.dp))
        }
    }
}

@Composable private fun PlayerCard(p:Player,manager:Manager,accent:Color,favorite:Boolean,open:(Player)->Unit,toggle:((Player)->Unit)?){val value=if(manager==Manager.COMUNIO)p.comunioValue else p.kickbaseValue;Card(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=5.dp).clickable{open(p)},shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.club} · ${p.position.label}",style=MaterialTheme.typography.bodySmall);Text("${scoreIcon(p.fantasyScore)} ${p.fantasyScore}/100 · ${p.fantasyLabel}",color=accent,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=4.dp));Text("${if(manager==Manager.COMUNIO)"Comunio" else "Kickbase"}: ${euro(value)} ${deltaText(if(manager==Manager.COMUNIO)p.comunioDelta else p.kickbaseDelta)} · ${p.status}",style=MaterialTheme.typography.bodySmall)};IconButton(onClick={toggle?.invoke(p)}){Icon(if(favorite)Icons.Default.Star else Icons.Default.StarBorder,null,tint=if(favorite)accent else Color.Gray)}}}}

@Composable private fun MarketTrendCard(p:Player,manager:Manager,accent:Color){
    val points=p.marketHistory.mapNotNull { point ->
        val value=if(manager==Manager.COMUNIO) point.comunioValue else point.kickbaseValue
        value?.let { it to point.at }
    }.takeLast(7)
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(14.dp)){
            Text("Marktwertverlauf",fontWeight=FontWeight.Bold)
            if(points.size<2){ Text("Noch nicht genug Messpunkte für einen Verlauf.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp)) }
            else {
                val first=points.first().first; val last=points.last().first; val diff=last-first
                Text("${euro(first)} → ${euro(last)}   ${if(diff>=0)"↗" else "↘"} ${if(diff>=0)"+" else ""}${euro(diff)}",color=accent,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=6.dp))
                Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom){
                    val max=points.maxOf{it.first}.coerceAtLeast(1); val min=points.minOf{it.first}; val range=(max-min).coerceAtLeast(1)
                    points.forEach { (v,_) -> Box(Modifier.weight(1f).height((20+(70*(v-min).toFloat()/range)).dp).padding(horizontal=2.dp)){ Surface(Modifier.fillMaxSize(),shape=RoundedCornerShape(4.dp),color=accent.copy(alpha=.75f)){} } }
                }
            }
        }
    }
}


@Composable private fun TransferAdvisor(modifier:Modifier,manager:Manager,accent:Color,players:List<Player>,open:(Player)->Unit){
    var budgetText by remember{mutableStateOf("8.0")}
    var position by remember{mutableStateOf<Position?>(null)}
    var maxPrice by remember{mutableStateOf(8.0)}
    val budget=(budgetText.replace(",",".").toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    LaunchedEffect(budgetText){maxPrice=budget}
    val candidates=players.filter{
        val value=if(manager==Manager.COMUNIO) it.comunioValue else it.kickbaseValue
        value!=null && value <= (budget*1_000_000).toLong() && (position==null || it.position==position)
    }.sortedByDescending{
        val value=if(manager==Manager.COMUNIO) it.comunioValue ?: Long.MAX_VALUE else it.kickbaseValue ?: Long.MAX_VALUE
        val delta=if(manager==Manager.COMUNIO) it.comunioDelta ?: 0L else it.kickbaseDelta ?: 0L
        val pricePenalty=(value.toDouble()/(budget*1_000_000.0).coerceAtLeast(1.0))*12.0
        it.fantasyScore + (if(delta>0) 6 else if(delta<0) -3 else 0) - pricePenalty
    }.take(15)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text("Transferberater",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Empfehlungen für ${if(manager==Manager.COMUNIO) "Comunio" else "Kickbase"}",color=accent)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value=budgetText,onValueChange={budgetText=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Budget in Mio. €")},leadingIcon={Icon(Icons.Default.Euro,null)})
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(selected=position==null,onClick={position=null},label={Text("Alle")})
            Position.values().forEach{pos->FilterChip(selected=position==pos,onClick={position=if(position==pos)null else pos},label={Text(pos.label)})}
        }
        Spacer(Modifier.height(10.dp))
        Card(shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(12.dp)){
            Text("Kaufstrategie",fontWeight=FontWeight.Bold)
            Text("Score + Einsatzsicherheit + Marktwerttrend; teure Spieler werden relativ zum Budget etwas niedriger gewichtet.",style=MaterialTheme.typography.bodySmall)
        }}
        Spacer(Modifier.height(12.dp))
        if(candidates.isEmpty()) Text("Keine passenden Spieler im gewählten Budget.",modifier=Modifier.padding(vertical=18.dp))
        candidates.forEachIndexed{index,p->
            val value=if(manager==Manager.COMUNIO) p.comunioValue else p.kickbaseValue
            val delta=if(manager==Manager.COMUNIO) p.comunioDelta else p.kickbaseDelta
            Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{open(p)},shape=RoundedCornerShape(18.dp)){
                Column(Modifier.padding(14.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text("#${index+1}",color=accent,fontWeight=FontWeight.Bold,modifier=Modifier.width(34.dp))
                        Column(Modifier.weight(1f)){
                            Text(p.name,fontWeight=FontWeight.Bold)
                            Text("${p.club} · ${p.position.label}",style=MaterialTheme.typography.bodySmall)
                        }
                        Text("${scoreIcon(p.fantasyScore)} ${p.fantasyScore}",fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("${if(manager==Manager.COMUNIO) "Comunio" else "Kickbase"}: ${euro(value)}  ${deltaText(delta)}",style=MaterialTheme.typography.bodySmall)
                    Text("Status: ${p.status} · ${p.fantasyLabel}",style=MaterialTheme.typography.bodySmall)
                    val reason=p.fantasyReasons.firstOrNull() ?: "ausgewogene Bewertung"
                    Text("Empfehlung: ${if(p.fantasyScore>=75) "Kaufen" else if(p.fantasyScore>=60) "Interessant" else "Nur bei Bedarf"} · $reason",color=accent,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp))
                }
            }
        }
    }
}

@Composable private fun NewsScreen(modifier:Modifier,accent:Color,favorites:Set<String>,players:List<Player>,open:(Player)->Unit){val articles=players.filter{favorites.contains(it.id)||favorites.contains(it.name)}.flatMap{p->p.news.map{p to it}}.distinctBy{it.second.url.ifBlank{it.second.title.lowercase()}};Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){Text("Aktuelle News",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Für deine Favoriten",color=accent);Spacer(Modifier.height(8.dp));if(articles.isEmpty())Text("Noch keine News zu deinen Favoriten.",Modifier.padding(vertical=16.dp));articles.forEach{(p,n)->Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{open(p)},shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(n.source,color=accent,fontWeight=FontWeight.Bold);Text(n.age,style=MaterialTheme.typography.labelSmall)};Text(p.name,style=MaterialTheme.typography.labelMedium);Text(n.title,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp));Text(n.summary,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=4.dp));Text("Fantasy-Relevanz: ${n.relevance}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))}}}}}

@Composable private fun PlayerDetail(p:Player,manager:Manager,accent:Color,favorite:Boolean,toggle:()->Unit,back:()->Unit){val context=LocalContext.current;val value=if(manager==Manager.COMUNIO)p.comunioValue else p.kickbaseValue;Scaffold(topBar={TopAppBar(title={Text(p.name)},navigationIcon={IconButton(onClick=back){Icon(Icons.Default.ArrowBack,null)}},actions={IconButton(onClick=toggle){Icon(if(favorite)Icons.Default.Star else Icons.Default.StarBorder,null,tint=accent)}})}){pad->Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())){Text("${p.club} · ${p.position.label}",color=accent);Spacer(Modifier.height(10.dp));Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text("Fantasy-Score",style=MaterialTheme.typography.labelLarge);Text("${scoreIcon(p.fantasyScore)} ${p.fantasyScore}/100",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=accent);Text(p.fantasyLabel,fontWeight=FontWeight.Bold);p.fantasyReasons.forEach{Text("• $it",style=MaterialTheme.typography.bodySmall)};Spacer(Modifier.height(12.dp));Text("${if(manager==Manager.COMUNIO)"Comunio" else "Kickbase"}-Marktwert",style=MaterialTheme.typography.labelLarge);Text(euro(value),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Status: ${p.status}",fontWeight=FontWeight.Bold)}};
        Spacer(Modifier.height(12.dp));
        MarketTrendCard(p,manager,accent);
        Spacer(Modifier.height(18.dp));Text("Aktuelle Nachrichten",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);if(p.news.isEmpty())Text("Keine zugeordneten aktuellen Meldungen.",Modifier.padding(vertical=12.dp));p.news.forEach{n->Card(Modifier.fillMaxWidth().padding(vertical=5.dp),shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(12.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(n.source,color=accent,fontWeight=FontWeight.Bold);Text(n.age,style=MaterialTheme.typography.labelSmall)};Text(n.title,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp));Text(n.summary,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp));Text(n.relevance,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp));if(n.url.isNotBlank())OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(n.url)))},modifier=Modifier.padding(top=5.dp)){Icon(Icons.Default.OpenInBrowser,null);Spacer(Modifier.width(6.dp));Text("Original öffnen")}}}}}}}

@Composable private fun SettingsScreen(modifier:Modifier,manager:Manager,accent:Color,settings:AppSettings,select:(Manager)->Unit){var injuries by remember{mutableStateOf(settings.pushInjuries())};var news by remember{mutableStateOf(settings.pushNews())};var market by remember{mutableStateOf(settings.pushMarket())};Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){Text("Einstellungen",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(18.dp));Text("Mein Manager",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);ManagerRow("Comunio",manager==Manager.COMUNIO){select(Manager.COMUNIO)};ManagerRow("Kickbase",manager==Manager.KICKBASE){select(Manager.KICKBASE)};Spacer(Modifier.height(18.dp));Text("Benachrichtigungen",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);SettingSwitch("Verletzungen & Einsatzänderungen",injuries){injuries=it;settings.setPushInjuries(it)};SettingSwitch("Relevante Spieler-News",news){news=it;settings.setPushNews(it)};SettingSwitch("Marktwertänderungen",market){market=it;settings.setPushMarket(it)};Spacer(Modifier.height(18.dp));Text("Direkte Datenquelle",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("Spieler und Comunio-Marktwerte werden direkt von stats.comunio.de (Comstats) geladen. Es ist kein API-Schlüssel und kein eigenes Backend erforderlich. Für Kickbase-Marktwerte liefert Comstats keine Daten.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))}}
@Composable private fun SettingSwitch(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked,onChange)}}
@Composable private fun ManagerRow(label:String,selected:Boolean,click:()->Unit){Row(Modifier.fillMaxWidth().clickable{click()}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected,click);Text(label)}}


/** v20: komplette Transferkette mit Verkauf -> Ersatz -> Restbudget. */
@Composable private fun SquadPlanner(modifier:Modifier,manager:Manager,accent:Color,players:List<Player>,favorites:Set<String>,open:(Player)->Unit){
    var budgetText by remember{mutableStateOf("8.0")}
    val budget=(budgetText.replace(",",".").toDoubleOrNull()?:0.0).coerceAtLeast(0.0)
    val fav=players.filter{favorites.contains(it.id)||favorites.contains(it.name)}
    val problems=fav.sortedBy{riskPriority(it)}.take(5)
    val candidates=players.filter{p->!fav.any{it.id==p.id} && valueOf(p,manager)!=null}
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text("Kader- & Transferplaner",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Verkauf + Ersatz + verbleibendes Budget",color=accent)
        Spacer(Modifier.height(10.dp))
        SquadAnalysisCard(manager,accent,players,favorites)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(budgetText,{budgetText=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Freies Budget in Mio. €")})
        Spacer(Modifier.height(14.dp))
        Text("Priorisierte Problemspieler",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        if(problems.isEmpty()) Text("Markiere Spieler als Favoriten, um deinen Kader zu planen.",Modifier.padding(vertical=8.dp))
        problems.forEach{p->
            val available=budget*1_000_000+(valueOf(p,manager)?:0L)
            val replacement=candidates.filter{q->q.position==p.position && (valueOf(q,manager)?:Long.MAX_VALUE)<=available.toLong()}
                .maxByOrNull{transferScore(it,manager)}
            Card(Modifier.fillMaxWidth().padding(vertical=5.dp),shape=RoundedCornerShape(16.dp)){
                Column(Modifier.padding(14.dp)){
                    Text("${riskIcon(p)} ${p.name}",fontWeight=FontWeight.Bold)
                    Text("${p.position.label} · ${p.status}",style=MaterialTheme.typography.bodySmall)
                    if(replacement!=null){
                        val rest=available-(valueOf(replacement,manager)?:0L)
                        Text("Vorschlag: ${replacement.name} · ${euro(valueOf(replacement,manager))}",color=accent,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=5.dp))
                        Text("Nach Verkauf + Kauf verbleiben: ${euro(rest)}",style=MaterialTheme.typography.bodySmall)
                        Text("Verbesserung: ${replacement.fantasyScore} vs. ${p.fantasyScore} Score",style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={open(replacement)}){Text("Ersatz ansehen")}
                    } else Text("Kein passender Ersatz im verfügbaren Budget.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
                }
            }
        }
    }
}

private fun valueOf(p:Player,m:Manager)=if(m==Manager.COMUNIO)p.comunioValue else p.kickbaseValue
private fun riskPriority(p:Player)=when{
    p.status.contains("verlet",true)||p.status.contains("ausfall",true)->p.fantasyScore-30
    p.fantasyScore<45->p.fantasyScore
    else->p.fantasyScore+30
}
private fun riskIcon(p:Player)=when{p.status.contains("verlet",true)||p.status.contains("ausfall",true)->"🩹";p.fantasyScore<45->"🔴";else->"🟡"}
private fun transferScore(p:Player,m:Manager):Double{
    val v=valueOf(p,m)?:return -999.0
    val d=if(m==Manager.COMUNIO)p.comunioDelta?:0L else p.kickbaseDelta?:0L
    return p.fantasyScore+d/1_000_000.0*5.0-(v/1_000_000.0)*1.5
}

/** v21: strukturelle Kaderanalyse mit Problemzonen und Prioritäten. */
@Composable private fun SquadAnalysisCard(manager:Manager,accent:Color,players:List<Player>,favorites:Set<String>){
    val fav=players.filter{favorites.contains(it.id)||favorites.contains(it.name)}
    val counts=Position.values().associateWith{pos->fav.count{it.position==pos}}
    val weak=Position.values().sortedBy{counts[it]?:0}.firstOrNull()
    val avg=if(fav.isEmpty())0 else fav.map{it.fantasyScore}.average().toInt()
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(14.dp)){
            Text("Kaderanalyse",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Gesamtscore: $avg/100 · ${if(manager==Manager.COMUNIO)"Comunio" else "Kickbase"}",color=accent,fontWeight=FontWeight.SemiBold)
            Text("Besetzung: ${Position.values().joinToString(" · "){p->"${p.label} ${counts[p]?:0}"} }",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=5.dp))
            if(weak!=null && (counts[weak]?:0)<2) Text("🚨 Priorität: ${weak.label} ist dünn besetzt.",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp))
            val risks=fav.count{it.fantasyScore<45||it.status.contains("verlet",true)||it.status.contains("ausfall",true)}
            if(risks>0) Text("⚠️ $risks Spieler mit erhöhtem Risiko.",modifier=Modifier.padding(top=3.dp))
        }
    }
}

private fun lineupScore(p:Player):Double {
    var score=p.fantasyScore.toDouble()
    when { p.startingProbability != null && p.startingProbability >= 80 -> score += 12
           p.startingProbability != null && p.startingProbability < 50 -> score -= 18 }
    when (p.opponentStrength) {
        1 -> score += 8; 2 -> score += 4; 4 -> score -= 5; 5 -> score -= 9
    }
    if (p.homeMatch == true) score += 2
    if (p.status.contains("verlet",true) || p.status.contains("ausfall",true)) score -= 35
    return score
}

@Composable private fun LineupPlanner(modifier:Modifier,manager:Manager,accent:Color,players:List<Player>,favorites:Set<String>,open:(Player)->Unit){
    val squad=players.filter{favorites.contains(it.id)||favorites.contains(it.name)}
    val starters=squad.filter{!it.status.contains("verlet",true) && !it.status.contains("ausfall",true)}
    val byPos=Position.values().associateWith{pos->starters.filter{it.position==pos}.sortedByDescending{lineupScore(it)}}
    val formation=mapOf(Position.GK to 1,Position.DEF to 4,Position.MID to 3,Position.FWD to 3)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Text("Spieltags-Aufstellung",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text("Automatische 4-3-3-Empfehlung · Spieltagsfaktoren",color=accent)
        Text("Gegner-/Heimdaten werden nur berücksichtigt, wenn die Datenquelle sie bereitstellt.",style=MaterialTheme.typography.bodySmall,color=Color.Gray)
        Spacer(Modifier.height(10.dp))
        SquadAnalysisCard(manager,accent,players,favorites)
        Spacer(Modifier.height(12.dp))
        Position.values().forEach{pos->
            Text("${pos.label} · ${formation[pos]?:0}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp))
            val selected=byPos[pos].orEmpty().take(formation[pos]?:0)
            if(selected.isEmpty()) Text("Keine sichere Besetzung.",style=MaterialTheme.typography.bodySmall)
            selected.forEach{p->LineupPlayerCard(p,manager,accent,true,open)}
            val bench=byPos[pos].orEmpty().drop(formation[pos]?:0).take(2)
            bench.forEach{p->LineupPlayerCard(p,manager,accent,false,open)}
        }
        val missing=formation.any{(pos,n)->byPos[pos].orEmpty().size<n}
        if(missing) Card(Modifier.fillMaxWidth().padding(top=12.dp),shape=RoundedCornerShape(16.dp)){
            Text("⚠️ Aufstellung nicht vollständig: Es fehlen sichere Spieler auf mindestens einer Position.",Modifier.padding(14.dp),fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable private fun LineupPlayerCard(p:Player,m:Manager,accent:Color,starter:Boolean,open:(Player)->Unit){
    Card(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable{open(p)},shape=RoundedCornerShape(14.dp)){
        Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
            Text(if(starter)"START" else "BANK",color=accent,fontWeight=FontWeight.Bold,modifier=Modifier.width(54.dp))
            Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.club} · ${p.status}",style=MaterialTheme.typography.bodySmall)
            val match = p.opponent?.let { "${if(p.homeMatch==true) "vs." else "bei"} $it" }
            if(match != null) Text(match + (p.opponentStrength?.let { " · Gegnerstärke $it/5" } ?: "") + (p.opponentRank?.let { " · Rang $it" } ?: ""),style=MaterialTheme.typography.labelSmall,color=accent)
            if(p.startingProbability != null) Text("Startelfchance: ${p.startingProbability}%",style=MaterialTheme.typography.labelSmall)}
            Text("${scoreIcon(p.fantasyScore)} ${p.fantasyScore}")
        }
    }
}
