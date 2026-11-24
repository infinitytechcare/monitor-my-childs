package com.infinitytechcare.screentime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(UsageRepository.hasUsageAccess(context)) }
    var dayOffset by remember { mutableStateOf(0) }
    var usage by remember { mutableStateOf<List<AppUsage>>(emptyList()) }

    // Re-check the permission whenever the screen comes back to the foreground
    // (e.g. after the user returns from the Settings screen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = UsageRepository.hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasAccess, dayOffset) {
        usage = if (hasAccess) UsageRepository.usageForDay(context, dayOffset) else emptyList()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Family Screen Time") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!hasAccess) {
                PermissionCard(
                    onGrant = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            } else {
                UsageContent(
                    dayOffset = dayOffset,
                    usage = usage,
                    onPrev = { dayOffset -= 1 },
                    onNext = { if (dayOffset < 0) dayOffset += 1 },
                    onShare = {
                        val report = buildReport(dayOffset, usage)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Share report"))
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Usage access needed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This app shows how long each app was used on this phone. Android " +
                "requires you to turn on \"Usage access\" for it by hand — it cannot " +
                "be enabled silently. Tap below, find \"Family Screen Time\" in the " +
                "list, and switch it on.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Open Usage Access settings") }
    }
}

@Composable
fun UsageContent(
    dayOffset: Int,
    usage: List<AppUsage>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShare: () -> Unit
) {
    val total = usage.sumOf { it.millis }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPrev) { Text("< Prev") }
            Text(dateLabel(dayOffset), fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext, enabled = dayOffset < 0) { Text("Next >") }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Total screen time", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatDuration(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (usage.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No usage recorded for this day.")
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(usage) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(app.label, modifier = Modifier.weight(1f))
                        Text(formatDuration(app.millis), fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                }
            }
        }

        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) { Text("Share this day's report") }
    }
}

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

fun buildReport(dayOffset: Int, usage: List<AppUsage>): String {
    val sb = StringBuilder()
    sb.append("Family Screen Time — ").append(dateLabel(dayOffset)).append("\n")
    sb.append("Total: ").append(formatDuration(usage.sumOf { it.millis })).append("\n\n")
    for (a in usage) {
        sb.append(a.label).append(": ").append(formatDuration(a.millis)).append("\n")
    }
    return sb.toString()
}
