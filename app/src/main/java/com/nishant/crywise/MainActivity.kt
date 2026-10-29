package com.nishant.crywise

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var monitor: CryMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        monitor = CryMonitor(applicationContext)
        val store = Store(applicationContext)
        setContent {
            CryWiseTheme {
                App(store, monitor, keepScreenOn = { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                })
            }
        }
    }

    override fun onDestroy() {
        monitor.close()
        super.onDestroy()
    }
}

// ---------- Theme ----------

private val Teal = Color(0xFF2C7A73)
private val Warn = Color(0xFFB4231F)

@Composable
fun CryWiseTheme(content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF5CC2B6), onPrimary = Color(0xFF0B1A19),
        primaryContainer = Color(0xFF1B3A3A), onPrimaryContainer = Color(0xFFCDEFEA),
        background = Color(0xFF0F1522), surface = Color(0xFF182133),
        onBackground = Color(0xFFE7ECF4), onSurface = Color(0xFFE7ECF4),
        surfaceVariant = Color(0xFF222D44), onSurfaceVariant = Color(0xFF9AA6BA),
        error = Color(0xFFFF8A80), errorContainer = Color(0xFF3A1B1D), onErrorContainer = Color(0xFFFFDAD6),
    ) else lightColorScheme(
        primary = Teal, onPrimary = Color.White,
        primaryContainer = Color(0xFFDDF0EC), onPrimaryContainer = Color(0xFF0E3B37),
        background = Color(0xFFF2F5F9), surface = Color.White,
        onBackground = Color(0xFF1B2433), onSurface = Color(0xFF1B2433),
        surfaceVariant = Color(0xFFE6EBF2), onSurfaceVariant = Color(0xFF5B6678),
        error = Warn, errorContainer = Color(0xFFFBE4E2), onErrorContainer = Color(0xFF5C0F0C),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

// ---------- App shell ----------

@Composable
fun App(store: Store, monitor: CryMonitor, keepScreenOn: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }   // bump to re-read storage
    var checking by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Episode?>(null) }

    fun newDraft(): Episode {
        val ctx = Advisor.contextFromLog(store, emptySet(), null)
        return Episode(
            start = System.currentTimeMillis(), durationSec = 0, intensity = "", worked = null,
            feedBucket = Advisor.feedBucket(ctx.hoursSinceFeed),
            awakeBucket = Advisor.awakeBucket(ctx.awakeHours),
            hour = ctx.hour,
        )
    }

    DisposableEffect(Unit) {
        monitor.onCryStart = {
            draft = newDraft()
            tab = 0
            checking = true
        }
        monitor.onCryEnd = { _, secs, intensity ->
            draft?.let {
                val e = it.copy(durationSec = secs, intensity = intensity ?: "")
                store.saveEpisode(e)
                draft = e
                version++
            }
        }
        onDispose { monitor.onCryStart = null; monitor.onCryEnd = null }
    }
    LaunchedEffect(monitor.listening) { keepScreenOn(monitor.listening) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) monitor.start(scope)
    }
    fun startListening() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) monitor.start(scope) else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val items = listOf(
                    Triple("Listen", Icons.Filled.Home, 0),
                    Triple("Log", Icons.Filled.List, 1),
                    Triple("History", Icons.Filled.DateRange, 2),
                    Triple("Help", Icons.Filled.Info, 3),
                )
                items.forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> if (checking) CheckScreen(
                    store = store, monitor = monitor, draft = draft, version = version,
                    onDone = { worked ->
                        val d = draft ?: newDraft()
                        store.saveEpisode(d.copy(worked = worked, intensity = d.intensity.ifEmpty { monitor.intensity ?: "" }))
                        draft = null; checking = false; version++
                    },
                    onCancel = { draft = null; checking = false },
                    onLog = { type -> store.addEvent(LogEvent(type, System.currentTimeMillis())); version++ },
                ) else ListenScreen(
                    store = store, monitor = monitor, version = version,
                    onStart = { startListening() },
                    onStop = { monitor.stop() },
                    onCheckNow = { draft = newDraft(); checking = true },
                    onSetup = { tab = 3 },
                )
                1 -> LogScreen(store, version) { version++ }
                2 -> HistoryScreen(store, version) { version++ }
                3 -> HelpScreen(store, version) { version++ }
            }
        }
    }
}

// ---------- Shared bits ----------

@Composable
fun Title(text: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        if (sub != null) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Section(title: String, danger: Boolean = false, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().then(
            if (danger) Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp)) else Modifier
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(options: List<Choice>, selected: Set<String>, danger: Boolean = false, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val on = o.key in selected
            FilterChip(
                selected = on,
                onClick = { onToggle(o.key) },
                label = { Text(o.label, modifier = Modifier.padding(vertical = 6.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    selectedLabelColor = if (danger) Color.White else MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

fun ago(t: Long?): String {
    if (t == null) return "not logged"
    val mins = ((System.currentTimeMillis() - t) / 60000).toInt()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        else -> "${mins / 60} hr ${mins % 60} min ago"
    }
}

fun ageText(weeks: Int?): String = when {
    weeks == null -> ""
    weeks < 9 -> "$weeks weeks old"
    else -> "${weeks * 7 / 30} months old"
}

// ---------- Listen ----------

@Composable
fun ListenScreen(
    store: Store, monitor: CryMonitor, version: Int,
    onStart: () -> Unit, onStop: () -> Unit, onCheckNow: () -> Unit, onSetup: () -> Unit,
) {
    // Tick every 30 s so "x min ago" stays fresh
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val name = store.babyName
    val weeks = store.ageWeeks()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Title(
                if (name.isBlank()) "CryWise" else "Listening for $name",
                if (weeks == null) "Add your baby's birth date so advice fits their age." else ageText(weeks),
            )
            if (name.isBlank() || weeks == null) {
                TextButton(onClick = onSetup) { Text("Set up baby details") }
            }
        }
        item {
            val scale by animateFloatAsState(if (monitor.listening) 1f + monitor.level * 0.25f else 1f, label = "pulse")
            val color = when {
                monitor.crying -> MaterialTheme.colorScheme.error
                monitor.listening -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size((170 * scale).dp).background(color.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(140.dp).background(color, CircleShape)
                            .clickable { if (monitor.listening) onStop() else onStart() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (monitor.listening) "Stop" else "Start\nlistening",
                            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                            color = if (monitor.listening) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    when {
                        monitor.crying -> "Crying detected · ${monitor.crySeconds}s"
                        monitor.listening -> "Listening… all quiet"
                        else -> "Keep the phone within a couple of metres of your baby"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = if (monitor.crying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (monitor.listening) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cry match ${(monitor.cryScore * 100).toInt()}%", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { monitor.cryScore }, modifier = Modifier.fillMaxWidth())
                    }
                }
                monitor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Button(onClick = onCheckNow, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text("Baby is crying — help me now", fontWeight = FontWeight.Bold)
            }
        }
        item {
            key(version, tick) {
                Section("Right now") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("Last feed", ago(store.lastOf(EventType.FEED)?.time), Modifier.weight(1f))
                        Stat("Last nappy", ago(store.lastOf(EventType.NAPPY)?.time), Modifier.weight(1f))
                    }
                    val w = store.lastOf(EventType.WAKE)?.time
                    val s = store.lastOf(EventType.SLEEP)?.time
                    Stat("Sleep", when {
                        s != null && (w == null || s > w) -> "Asleep since ${timeFmt.format(Date(s))}"
                        w != null -> "Awake since ${timeFmt.format(Date(w))}"
                        else -> "not logged"
                    }, Modifier.fillMaxWidth())
                    Text("Tap Log after feeds, sleeps and nappy changes. Advice gets sharper with it.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Check (why is baby crying) ----------

@Composable
fun CheckScreen(
    store: Store, monitor: CryMonitor, draft: Episode?, version: Int,
    onDone: (String?) -> Unit, onCancel: () -> Unit, onLog: (String) -> Unit,
) {
    var signs by remember { mutableStateOf(setOf<String>()) }
    var flags by remember { mutableStateOf(setOf<String>()) }
    val intensity = monitor.intensity ?: draft?.intensity?.ifEmpty { null }
    val ctx = remember(signs, intensity, version) { Advisor.contextFromLog(store, signs, intensity) }
    val ranked = remember(ctx) { Advisor.rank(ctx, store.episodes(), store.babyName).take(3) }
    val name = store.babyName.ifBlank { "baby" }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Title("Why is $name crying?", if (monitor.crying) "Crying now · ${monitor.crySeconds}s" +
                (intensity?.let { " · $it" } ?: "") else "Tap what you notice. The reasons update as you go.")
        }
        item {
            Section("Any warning signs?", danger = true) {
                ChipGroup(Advisor.RED_FLAGS, flags, danger = true) { k -> flags = if (k in flags) flags - k else flags + k }
            }
        }
        if (flags.isNotEmpty()) item {
            val emergency = flags.any { it in Advisor.EMERGENCY }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (emergency) "Get medical help now" else "Call your doctor today",
                        fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.error)
                    Text(
                        if (emergency) "Go to the nearest emergency department or call 112. Don't wait to see if it passes."
                        else "These signs can mean illness that this app can't rule out. If it gets worse, go to emergency.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        item {
            Section("From your log") {
                Text("Last feed: ${ctx.hoursSinceFeed?.let { fmtHours(it) + " ago" } ?: "not logged"}")
                Text("Awake: ${when (val a = ctx.awakeHours) { null -> "not logged"; 0.0 -> "was asleep"; else -> fmtHours(a ?: 0.0) }}")
                Text("Last nappy: ${ctx.hoursSinceNappy?.let { fmtHours(it) + " ago" } ?: "not logged"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onLog(EventType.FEED) }) { Text("Just fed") }
                    OutlinedButton(onClick = { onLog(EventType.NAPPY) }) { Text("Just changed") }
                }
            }
        }
        item {
            Section("What do you notice?") {
                ChipGroup(Advisor.SIGNS, signs) { k -> signs = if (k in signs) signs - k else signs + k }
            }
        }
        item { Text("Most likely reasons", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        items(ranked.size) { i -> ReasonCard(ranked[i], top = i == 0, best = ranked[0].score) }
        item {
            Section("What settled $name?") {
                Text("Tap what worked. CryWise learns $name's patterns from this.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val choices = (ranked.map { it.cause } + Advisor.CAUSES.values).distinctBy { it.key }
                    .map { Choice(it.key, it.title) }
                ChipGroup(choices, emptySet()) { k -> onDone(k) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDone(null) }) { Text("Settled, not sure why") }
                    TextButton(onClick = onCancel) { Text("Close") }
                }
            }
        }
        item {
            Text("Tried the top reasons and still crying after 20–30 minutes? Check temperature. Crying for over 2–3 hours, or a cry that sounds different from usual, is a reason to call your doctor.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReasonCard(r: Ranked, top: Boolean, best: Int) {
    val (label, bg, fg) = when {
        r.score >= best && r.score >= 4 -> Triple("Most likely", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        r.score >= 3 -> Triple("Possible", Color(0x33E0A100), MaterialTheme.colorScheme.onSurface)
        else -> Triple("Worth checking", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().then(
            if (top) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.cause.title, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f))
                Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, letterSpacing = 0.6.sp,
                    modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
            if (r.reasons.isNotEmpty()) Text(r.reasons.joinToString(". ") + ".", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            r.cause.steps.forEachIndexed { i, s -> Text("${i + 1}. $s") }
            Text("Worked if: ${r.cause.workedIf}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Log ----------

@Composable
fun LogScreen(store: Store, version: Int, changed: () -> Unit) {
    val events = remember(version) { store.events() }
    var minutesAgo by remember { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("Log", "One tap after each feed, sleep and nappy.") }
        item {
            Section("When?") {
                ChipGroup(
                    listOf(Choice("0", "Now"), Choice("15", "15 min ago"), Choice("30", "30 min ago"), Choice("60", "1 hr ago")),
                    setOf(minutesAgo.toString()),
                ) { k -> minutesAgo = k.toInt() }
                val types = listOf(EventType.FEED, EventType.SLEEP, EventType.WAKE, EventType.NAPPY)
                types.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t ->
                            Button(
                                onClick = {
                                    store.addEvent(LogEvent(t, System.currentTimeMillis() - minutesAgo * 60_000L))
                                    minutesAgo = 0
                                    changed()
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(EventType.label(t), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
        item { Text("Recent", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (events.isEmpty()) item {
            Text("Nothing logged yet. Start with the last feed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(events.take(60)) { e ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(EventType.label(e.type), fontWeight = FontWeight.SemiBold)
                    Text(dayFmt.format(Date(e.time)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { store.removeEvent(e); changed() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- History ----------

@Composable
fun HistoryScreen(store: Store, version: Int, changed: () -> Unit) {
    val eps = remember(version) { store.episodes() }
    val name = store.babyName.ifBlank { "your baby" }
    val counts = eps.mapNotNull { it.worked }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("History", "Every crying spell and what settled it.") }
        item {
            Section("What usually works for $name") {
                if (counts.isEmpty()) Text("After a few cries, the things that settle $name show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                counts.take(5).forEach { (k, n) ->
                    Row { Text(Advisor.CAUSES[k]?.title ?: k, modifier = Modifier.weight(1f)); Text("$n×", fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (eps.isEmpty()) item {
            Text("No crying spells recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(eps.take(100)) { e ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(dayFmt.format(Date(e.start)), fontWeight = FontWeight.SemiBold)
                        val parts = listOfNotNull(
                            if (e.durationSec > 0) "${e.durationSec / 60} min ${e.durationSec % 60}s" else null,
                            e.intensity.ifEmpty { null },
                            if (e.feedBucket != "?") "fed ${e.feedBucket} hr before" else null,
                        )
                        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Settled by: " + (e.worked?.let { Advisor.CAUSES[it]?.title } ?: "not recorded"),
                            color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                    IconButton(onClick = { store.deleteEpisode(e.start); changed() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------- Help & setup ----------

@Composable
fun HelpScreen(store: Store, version: Int, changed: () -> Unit) {
    val context = LocalContext.current
    var name by remember(version) { mutableStateOf(store.babyName) }
    val birth = store.birthMillis
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Title("Help") }
        item {
            Section("Baby details") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; store.babyName = it },
                    label = { Text("Baby's name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = {
                    val cal = Calendar.getInstance().apply { if (birth > 0) timeInMillis = birth }
                    DatePickerDialog(context, { _, y, m, d ->
                        store.birthMillis = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis
                        changed()
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (birth > 0) "Born ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(birth))} · ${ageText(store.ageWeeks())}"
                        else "Set birth date")
                }
            }
        }
        item {
            Section("Get medical help now if baby has", danger = true) {
                listOf(
                    "Blue, grey or very pale lips or skin",
                    "Struggling, fast or noisy breathing",
                    "Is floppy, very sleepy or hard to wake",
                    "Under 3 months with a temperature of 38°C or more",
                    "A rash that doesn't fade when you press a glass on it",
                    "A bulging soft spot on the head",
                ).forEach { Text("• $it") }
                Text("Call 112 or go to the nearest emergency department.", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Section("If you're at the end of your rope") {
                Text("• It's okay to lay baby on their back in the cot and step out for 5–10 minutes. Crying won't hurt them.")
                Text("• Never shake a baby, even for a second. Hand over to someone else if you can.")
                Text("• Healthy babies can cry for hours in the first 3 months. It peaks around 6–8 weeks, then eases.")
            }
        }
        item {
            Section("How listening works") {
                Text("CryWise uses an on-phone sound model (Google's YAMNet) that recognises a baby crying. Audio never leaves your phone and nothing is recorded.")
                Text("The sound tells it that baby is crying and how intense it is. The likely reason comes from your log, what you notice, and what has worked for your baby before.")
                Text("Keep the app open with the screen on while listening. It's a guide, not a diagnosis. If something feels wrong, call your doctor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
