package com.infinitytechcare.screentime

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

private val MIN_OPTIONS = listOf(0, 15, 30, 45, 60, 90, 120, 180)
private val BREAK_FOR_OPTIONS = listOf(5, 10, 15, 20, 30)

private fun cycleNext(cur: Int, opts: List<Int>): Int {
    val i = opts.indexOf(cur)
    return if (i < 0) opts[0] else opts[(i + 1) % opts.size]
}

private fun fmtMin(m: Int): String {
    if (m <= 0) return "Off"
    val h = m / 60
    val mm = m % 60
    return if (h > 0) (if (mm > 0) "${h}h ${mm}m" else "${h}h") else "${mm}m"
}

private fun hhmm(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root() {
    var rev by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) rev++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val titles = listOf("Today", "Limits", "Schedule", "Setup", "Remote")
    Scaffold(
        topBar = { TopAppBar(title = { Text("Family Screen Time · ${titles[tab]}") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.List, null) }, label = { Text("Today") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Lock, null) }, label = { Text("Limits") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Schedule") }
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Setup") }
                )
                NavigationBarItem(
                    selected = tab == 4, onClick = { tab = 4 },
                    icon = { Icon(Icons.Default.AccountCircle, null) }, label = { Text("Remote") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(rev)
                1 -> LimitsScreen(rev) { rev++ }
                2 -> ScheduleScreen(rev) { rev++ }
                3 -> SetupScreen(rev) { rev++ }
                else -> RemoteScreen(rev) { rev++ }
            }
        }
    }
}

/* ----------------------------- Today ----------------------------- */

@Composable
fun TodayScreen(rev: Int) {
    val ctx = LocalContext.current
    val hasAccess = remember(rev) { UsageRepository.hasUsageAccess(ctx) }
    if (!hasAccess) {
        PermissionCard { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        return
    }
    val usage = remember(rev) { UsageRepository.usageForDay(ctx, 0) }
    val total = usage.sumOf { it.millis }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Total screen time today", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatDuration(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (usage.isEmpty()) {
            Text("No usage recorded yet today.", Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(usage) { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(a.label, Modifier.weight(1f))
                        Text(formatDuration(a.millis), fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun PermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Usage access needed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Turn on \"Usage access\" for this app so it can show screen time. " +
                "Android requires you to do this by hand — it cannot be enabled silently.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Open Usage Access settings") }
    }
}

/* ----------------------------- Limits ----------------------------- */

@Composable
fun LimitsScreen(rev: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { AppListRepository.launchableApps(ctx) }
    val wlOn = remember(rev) { Prefs.isWhitelistEnabled(ctx) }
    val cap = remember(rev) { Prefs.totalCapMinutes(ctx) }
    val allowed = remember(rev) { Prefs.allowed(ctx) }
    val limits = remember(rev) { Prefs.limits(ctx) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allowed-apps only (whitelist)", fontWeight = FontWeight.Bold)
                        Text("Block everything except the apps you allow below.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = wlOn, onCheckedChange = { Prefs.setWhitelistEnabled(ctx, it); bump() })
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total daily screen-time cap", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { Prefs.setTotalCapMinutes(ctx, cycleNext(cap, MIN_OPTIONS)); bump() }) {
                        Text(fmtMin(cap))
                    }
                }
                HorizontalDivider()
                Text("Per-app daily limit · tap the time to change", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        items(apps) { app ->
            val lim = limits[app.pkg] ?: 0
            val isAllowed = allowed.contains(app.pkg)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(app.label, Modifier.weight(1f))
                TextButton(onClick = { Prefs.setLimit(ctx, app.pkg, cycleNext(lim, MIN_OPTIONS)); bump() }) {
                    Text(fmtMin(lim))
                }
                if (wlOn) {
                    Switch(checked = isAllowed, onCheckedChange = { Prefs.setAppAllowed(ctx, app.pkg, it); bump() })
                }
            }
            HorizontalDivider()
        }
    }
}

/* ----------------------------- Schedule ----------------------------- */

@Composable
fun ScheduleScreen(rev: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val windows = remember(rev) { Prefs.windows(ctx) }

    var sh by remember { mutableStateOf("") }
    var sm by remember { mutableStateOf("") }
    var eh by remember { mutableStateOf("") }
    var em by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Lock windows", fontWeight = FontWeight.Bold)
        Text("During these times, all apps are locked.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        if (windows.isEmpty()) {
            Text("None yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            windows.forEach { w ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${w.label}  ${hhmm(w.startMin)}–${hhmm(w.endMin)}", Modifier.weight(1f))
                    TextButton(onClick = { Prefs.removeWindow(ctx, w); bump() }) { Text("Remove") }
                }
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Quick add", fontWeight = FontWeight.Bold)
        Row {
            OutlinedButton(onClick = { Prefs.addWindow(ctx, LockWindow(21 * 60, 7 * 60, "Bedtime")); bump() }) { Text("Bedtime") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { Prefs.addWindow(ctx, LockWindow(8 * 60, 14 * 60, "School")); bump() }) { Text("School") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { Prefs.addWindow(ctx, LockWindow(17 * 60, 19 * 60, "Study")); bump() }) { Text("Study") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Custom (24-hour)", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumField(sh, { sh = it }, "SH")
            NumField(sm, { sm = it }, "SM")
            Text(" → ", Modifier.padding(horizontal = 4.dp))
            NumField(eh, { eh = it }, "EH")
            NumField(em, { em = it }, "EM")
        }
        OutlinedTextField(label, { label = it }, label = { Text("Label (e.g. Dinner)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val s = (sh.toIntOrNull() ?: -1) * 60 + (sm.toIntOrNull() ?: 0)
                val en = (eh.toIntOrNull() ?: -1) * 60 + (em.toIntOrNull() ?: 0)
                val lbl = label.ifBlank { "Locked" }
                if (s in 0..1439 && en in 0..1439) {
                    Prefs.addWindow(ctx, LockWindow(s, en, lbl))
                    sh = ""; sm = ""; eh = ""; em = ""; label = ""
                    bump()
                }
            }
        ) { Text("Add window") }
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(64.dp).padding(end = 4.dp)
    )
}

/* ----------------------------- Setup ----------------------------- */

@Composable
fun SetupScreen(rev: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val usageOk = remember(rev) { UsageRepository.hasUsageAccess(ctx) }
    val overlayOk = remember(rev) { Settings.canDrawOverlays(ctx) }
    val adminOk = remember(rev) { isAdminActive(ctx) }
    val svcOn = remember(rev) { Prefs.isServiceEnabled(ctx) }
    val alertsOn = remember(rev) { Prefs.isAlertsEnabled(ctx) }
    val brkAfter = remember(rev) { Prefs.breakAfterMinutes(ctx) }
    val brkFor = remember(rev) { Prefs.breakForMinutes(ctx) }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { bump() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Permissions", fontWeight = FontWeight.Bold)
        PermRow("Usage access", usageOk) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        PermRow("Draw over other apps (for the lock)", overlayOk) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        PermRow("Notifications", true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Protection running", fontWeight = FontWeight.Bold)
                Text("Enforces your limits, whitelist and schedule.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = svcOn,
                onCheckedChange = {
                    Prefs.setServiceEnabled(ctx, it)
                    if (it) MonitorService.start(ctx) else MonitorService.stop(ctx)
                    bump()
                }
            )
        }
        if (svcOn && (!usageOk || !overlayOk)) {
            Text("Grant Usage access and Draw-over permission above, or the lock can't appear.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Alerts on this phone", fontWeight = FontWeight.Bold)
            Switch(checked = alertsOn, onCheckedChange = { Prefs.setAlertsEnabled(ctx, it); bump() })
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Forced break after", fontWeight = FontWeight.Bold)
            TextButton(onClick = { Prefs.setBreakAfterMinutes(ctx, cycleNext(brkAfter, MIN_OPTIONS)); bump() }) { Text(fmtMin(brkAfter)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Break length", fontWeight = FontWeight.Bold)
            TextButton(onClick = { Prefs.setBreakForMinutes(ctx, cycleNext(brkFor, BREAK_FOR_OPTIONS)); bump() }) { Text(fmtMin(brkFor)) }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Stop easy removal", fontWeight = FontWeight.Bold)
        Text("Make the app device-admin so it can't be uninstalled until an adult turns admin off first. It adds no monitoring ability.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        if (adminOk) {
            Text("Device admin: ON", color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(ctx, AdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents a child from uninstalling the screen-time app.")
                }
                ctx.startActivity(intent)
            }) { Text("Turn on uninstall protection") }
        }

        Spacer(Modifier.height(24.dp))
        Text("This app keeps everything on the phone. It does not record the screen, microphone, messages or location, and sends nothing to anyone.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermRow(name: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (ok) "$name ✓" else name, Modifier.weight(1f))
        if (!ok) OutlinedButton(onClick = onClick) { Text("Grant") } else TextButton(onClick = onClick) { Text("Open") }
    }
}

private fun isAdminActive(ctx: Context): Boolean {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
}

/* ----------------------------- Remote ----------------------------- */

@Composable
fun RemoteScreen(rev: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    var enabled by remember(rev) { mutableStateOf(Prefs.isRemoteEnabled(ctx)) }
    var code by remember(rev) { mutableStateOf(Prefs.familyCode(ctx)) }
    var device by remember(rev) { mutableStateOf(Prefs.deviceName(ctx)) }
    var role by remember(rev) { mutableStateOf(Prefs.role(ctx)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remote control", fontWeight = FontWeight.Bold)
                Text("Carries only lock on/off and a short request — no screen, audio, messages or location.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = { Prefs.setRemoteEnabled(ctx, it); enabled = it })
        }

        OutlinedTextField(code, { code = it; Prefs.setFamilyCode(ctx, it) }, label = { Text("Family code (same on both phones)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedTextField(device, { device = it; Prefs.setDeviceName(ctx, it) }, label = { Text("This device's name (unique)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        Spacer(Modifier.height(12.dp))
        Text("This phone's role", fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 4.dp)) {
            if (role == "parent") Button(onClick = {}) { Text("Parent") }
            else OutlinedButton(onClick = { role = "parent"; Prefs.setRole(ctx, "parent") }) { Text("Parent") }
            Spacer(Modifier.width(8.dp))
            if (role == "child") Button(onClick = {}) { Text("Child") }
            else OutlinedButton(onClick = { role = "child"; Prefs.setRole(ctx, "child") }) { Text("Child") }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        if (!enabled || code.isBlank() || device.isBlank()) {
            Text("Turn on remote control and fill in the family code and device name on both phones.", style = MaterialTheme.typography.bodySmall)
        } else if (role == "child") {
            ChildRemote(ctx, code, device)
        } else {
            ParentRemote(ctx, code)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChildRemote(ctx: Context, code: String, device: String) {
    var locked by remember { mutableStateOf(false) }
    DisposableEffect(code, device) {
        var reg: ListenerRegistration? = null
        RemoteRepo.ensureAuth { ok ->
            if (ok) reg = RemoteRepo.listenMyDevice(ctx) { l, _ -> locked = l }
        }
        onDispose { reg?.remove() }
    }
    var msg by remember { mutableStateOf("") }
    Text("This is a CHILD device.", fontWeight = FontWeight.Bold)
    Text(if (locked) "Status: Locked by parent" else "Status: Unlocked", color = if (locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(msg, { msg = it }, label = { Text("Message (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        RemoteRepo.ensureAuth { ok -> if (ok) RemoteRepo.askForTime(ctx, msg.ifBlank { "Can I get more time?" }) }
        msg = ""
    }) { Text("Ask parent for more time") }
}

@Composable
private fun ParentRemote(ctx: Context, code: String) {
    var devices by remember { mutableStateOf<List<RemoteDevice>>(emptyList()) }
    DisposableEffect(code) {
        var reg: ListenerRegistration? = null
        RemoteRepo.ensureAuth { ok ->
            if (ok) reg = RemoteRepo.listenDevices(code) { devices = it }
        }
        onDispose { reg?.remove() }
    }
    Text("Child devices under \"$code\"", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (devices.isEmpty()) {
        Text("No child devices yet. Open the app on the child phone with the same family code, set its role to Child, and turn Protection on.", style = MaterialTheme.typography.bodySmall)
    }
    devices.forEach { d ->
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(d.name, fontWeight = FontWeight.Bold)
                Text(if (d.locked) "Locked" else "Unlocked", color = if (d.locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Row(Modifier.padding(top = 6.dp)) {
                    if (d.locked) Button(onClick = { RemoteRepo.setLocked(code, d.name, false) }) { Text("Unlock") }
                    else Button(onClick = { RemoteRepo.setLocked(code, d.name, true) }) { Text("Lock now") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { RemoteRepo.grantMinutes(code, d.name, 30) }) { Text("+30 min") }
                }
                if (d.requestPending) {
                    Spacer(Modifier.height(8.dp))
                    Text("Request: ${d.requestText}", color = MaterialTheme.colorScheme.error)
                    Row(Modifier.padding(top = 4.dp)) {
                        Button(onClick = { RemoteRepo.grantMinutes(code, d.name, 15) }) { Text("Approve +15m") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { RemoteRepo.clearRequest(code, d.name) }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

/* ----------------------------- shared helpers ----------------------------- */

fun formatDuration(millis: Long): String {
    val totalMin = millis / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun dateLabel(dayOffset: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, dayOffset)
    val fmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    return if (dayOffset == 0) "Today" else fmt.format(cal.time)
}
