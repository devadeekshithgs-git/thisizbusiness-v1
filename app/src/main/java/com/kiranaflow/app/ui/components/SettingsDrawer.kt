package com.kiranaflow.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.data.local.AppPrefs
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.local.ShopSettings
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.util.StubSyncEngine
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsDrawer(
    isOpen: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { ShopSettingsStore(context) }
    val settings by store.settings.collectAsState(initial = ShopSettings("", "", "", ""))
    val appPrefsStore = remember(context) { AppPrefsStore(context) }
    val appPrefs by appPrefsStore.prefs.collectAsState(initial = AppPrefs())
    val db = remember(context) { KiranaDatabase.getDatabase(context) }
    val repo = remember(context) { KiranaRepository(db) }
    val pendingOutbox by repo.pendingOutboxCount.collectAsState(initial = 0)
    val failedOutbox by repo.failedOutboxCount.collectAsState(initial = 0)
    val outboxRecent by repo.recentOutbox.collectAsState(initial = emptyList())
    val syncEngine = remember(context) { StubSyncEngine(db, appPrefsStore) }
    val scope = rememberCoroutineScope()

    var expandedShop by remember { mutableStateOf(true) }
    var expandedDev by remember { mutableStateOf(false) }
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var whatsappReminderMessage by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var legalName by remember { mutableStateOf("") }
    var businessAddress by remember { mutableStateOf("") }
    var businessStateCode by remember { mutableStateOf("") }

    var showDemoConfirm by remember { mutableStateOf(false) }
    var pendingDemoEnabled by remember { mutableStateOf(false) }
    var showOutboxDialog by remember { mutableStateOf(false) }
    var showMarkDoneConfirm by remember { mutableStateOf(false) }
    var outboxFilter by remember { mutableStateOf("UNSYNCED") } // ALL | UNSYNCED | FAILED | DONE
    val lastSyncFmt = remember { SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault()) }

    LaunchedEffect(isOpen, settings) {
        if (isOpen) {
            shopName = settings.shopName
            ownerName = settings.ownerName
            upiId = settings.upiId
            whatsappReminderMessage = settings.whatsappReminderMessage
            gstin = settings.gstin
            legalName = settings.legalName
            businessAddress = settings.address
            businessStateCode = if (settings.stateCode == 0) "" else settings.stateCode.toString().padStart(2, '0')
        }
    }

    BackHandler(enabled = isOpen) {
        onClose()
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black.copy(alpha = 0.45f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClose() }
            )

            // Right drawer panel
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.88f),
                color = BgPrimary,
                shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header (fixed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Settings", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextPrimary)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Divider(color = Gray200)

                    // Scrollable body
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Shop settings
                        item {
                            ListItem(
                                headlineContent = { Text("Shop Settings", fontWeight = FontWeight.Bold, color = TextPrimary) },
                                leadingContent = { Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary) },
                                trailingContent = {
                                    Icon(
                                        if (expandedShop) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                modifier = Modifier.clickable { expandedShop = !expandedShop }
                            )
                        }

                        if (expandedShop) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 18.dp)
                                        .fillMaxWidth()
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    KiranaInput(
                                        value = shopName,
                                        onValueChange = { shopName = it },
                                        placeholder = "e.g. Bhanu Super Mart",
                                        label = "SHOP NAME"
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    KiranaInput(
                                        value = ownerName,
                                        onValueChange = { ownerName = it },
                                        placeholder = "e.g. Owner Ji",
                                        label = "OWNER NAME"
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        "@ UPI ID (FOR QR CODE)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Purple600
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Required to show dynamic QR codes during billing.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextSecondary)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    KiranaInput(
                                        value = upiId,
                                        onValueChange = { upiId = it },
                                        placeholder = "e.g. 9876543210@upi"
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    KiranaInput(
                                        value = whatsappReminderMessage,
                                        onValueChange = { whatsappReminderMessage = it },
                                        placeholder = "e.g. Namaste {name}, your due is ₹{due}. Please pay.",
                                        label = "WHATSAPP REMINDER MESSAGE"
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))
                                    Text(
                                        "GST BUSINESS DETAILS",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Blue600
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    KiranaInput(
                                        value = gstin,
                                        onValueChange = { gstin = it.uppercase() },
                                        placeholder = "e.g. 29AABCT1234A1ZZ",
                                        label = "GSTIN"
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    KiranaInput(
                                        value = legalName,
                                        onValueChange = { legalName = it },
                                        placeholder = "Legal name as per GST registration",
                                        label = "LEGAL NAME"
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    KiranaInput(
                                        value = businessStateCode,
                                        onValueChange = { businessStateCode = it.filter { ch -> ch.isDigit() }.take(2) },
                                        placeholder = "e.g. 29",
                                        label = "STATE CODE (2 DIGITS)"
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    KiranaInput(
                                        value = businessAddress,
                                        onValueChange = { businessAddress = it },
                                        placeholder = "Shop address (for invoices/exports)",
                                        label = "ADDRESS"
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))
                                    KiranaButton(
                                        text = "✓ Save Settings",
                                        onClick = {
                                            scope.launch {
                                                store.save(shopName, ownerName, upiId)
                                                store.saveWhatsAppReminderMessage(whatsappReminderMessage)
                                                store.saveGstBusinessInfo(
                                                    gstin = gstin,
                                                    legalName = legalName,
                                                    address = businessAddress,
                                                    stateCode = businessStateCode.toIntOrNull() ?: 0
                                                )
                                                onClose()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }

                        item { Divider(color = Gray200) }

                        // Appearance settings (Dark Mode)
                        item {
                            ListItem(
                                headlineContent = { Text("Appearance", fontWeight = FontWeight.Bold, color = TextPrimary) },
                                leadingContent = { 
                                    Icon(
                                        if (appPrefs.darkModeEnabled) Icons.Default.DarkMode else Icons.Default.LightMode, 
                                        contentDescription = null, 
                                        tint = if (appPrefs.darkModeEnabled) Purple600 else AlertOrange
                                    ) 
                                },
                                trailingContent = {
                                    Switch(
                                        checked = appPrefs.darkModeEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch { appPrefsStore.setDarkModeEnabled(enabled) }
                                        }
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (appPrefs.darkModeEnabled) "Dark mode enabled" else "Light mode enabled",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }

                        item { Divider(color = Gray200) }

                        // Developer/demo tools
                        item {
                            ListItem(
                                headlineContent = { Text("Demo & Testing", fontWeight = FontWeight.Bold, color = TextPrimary) },
                                leadingContent = { Icon(Icons.Default.Menu, contentDescription = null, tint = TextSecondary) },
                                trailingContent = {
                                    Icon(
                                        if (expandedDev) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                modifier = Modifier.clickable { expandedDev = !expandedDev }
                            )
                        }

                        if (expandedDev) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 18.dp)
                                        .fillMaxWidth()
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Demo mode loads synthetic 1+ year transactions for charts and testing.",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Enable demo data", fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("Requires reset on next restart", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = appPrefs.demoModeEnabled,
                                            onCheckedChange = { enabled ->
                                                pendingDemoEnabled = enabled
                                                showDemoConfirm = true
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = {
                                            // Request reset without changing demo mode (useful if user wants to re-seed).
                                            showDemoConfirm = true
                                            pendingDemoEnabled = appPrefs.demoModeEnabled
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary),
                                        border = null
                                    ) {
                                        Text("Reset on next restart", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        "Cloud Sync",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Pending changes: $pendingOutbox",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Simulate sync success (dev)", fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("Marks validated outbox entries as DONE", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = appPrefs.devSimulateSyncSuccess,
                                            onCheckedChange = { enabled ->
                                                scope.launch { appPrefsStore.setDevSimulateSyncSuccess(enabled) }
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Use real backend (dev)", fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("Sends sync envelopes to BuildConfig backend URL", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = appPrefs.useRealBackend,
                                            onCheckedChange = { enabled ->
                                                scope.launch { appPrefsStore.setUseRealBackend(enabled) }
                                            }
                                        )
                                    }
                                    if (com.kiranaflow.app.BuildConfig.BACKEND_BASE_URL.isBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Backend URL not configured. Set KIRANAFLOW_BACKEND_BASE_URL in your local gradle.properties.",
                                            color = LossRed,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val syncButtonLabel = when {
                                        appPrefs.useRealBackend && com.kiranaflow.app.BuildConfig.BACKEND_BASE_URL.isNotBlank() -> "Sync now"
                                        appPrefs.devSimulateSyncSuccess -> "Sync now (simulate)"
                                        else -> "Sync now"
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val now = System.currentTimeMillis()
                                                val res = syncEngine.syncOnce()
                                                appPrefsStore.setLastSyncAttempt(now, res.message)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary),
                                        border = null
                                    ) {
                                        Text(syncButtonLabel, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        buildString {
                                            append("Last sync: ")
                                            append(
                                                appPrefs.lastSyncAttemptAtMillis?.let { lastSyncFmt.format(Date(it)) } ?: "—"
                                            )
                                        },
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    if (!appPrefs.lastSyncMessage.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(appPrefs.lastSyncMessage.orEmpty(), color = TextSecondary, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { showOutboxDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary),
                                        border = null
                                    ) {
                                        Text("View Outbox ($pendingOutbox pending)", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOutboxDialog) {
        Dialog(onDismissRequest = { showOutboxDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f),
                color = BgPrimary,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Outbox", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                            Text(
                                "${outboxRecent.size} shown • $pendingOutbox pending • $failedOutbox failed",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { showOutboxDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { repo.clearOutboxDone() } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary),
                            border = null
                        ) { Text("Clear done", fontWeight = FontWeight.Bold) }

                        OutlinedButton(
                            onClick = { scope.launch { repo.clearOutboxAll() } },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = LossRed),
                            border = null
                        ) { Text("Clear all", fontWeight = FontWeight.Bold) }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val res = syncEngine.syncOnce()
                                    appPrefsStore.setLastSyncAttempt(now, res.message)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary),
                            border = null
                        ) { Text("Retry unsynced", fontWeight = FontWeight.Bold) }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val res = syncEngine.syncFailedOnly(simulateSuccess = appPrefs.devSimulateSyncSuccess)
                                    appPrefsStore.setLastSyncAttempt(now, res.message)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = LossRed),
                            border = null
                        ) { Text("Retry failed", fontWeight = FontWeight.Bold) }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val res = syncEngine.syncPendingOnly(simulateSuccess = appPrefs.devSimulateSyncSuccess)
                                    appPrefsStore.setLastSyncAttempt(now, res.message)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = AlertOrange),
                            border = null
                        ) { Text("Sync pending", fontWeight = FontWeight.Bold) }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    syncEngine.resetFailedToPending()
                                    appPrefsStore.setLastSyncAttempt(
                                        System.currentTimeMillis(),
                                        "Reset FAILED → PENDING"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = Blue600),
                            border = null
                        ) { Text("Reset failed", fontWeight = FontWeight.Bold) }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showMarkDoneConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = AlertOrange),
                            border = null
                        ) { Text("Dev: mark done", fontWeight = FontWeight.Bold) }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Gray200)
                    Spacer(modifier = Modifier.height(10.dp))

                    val chipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Blue50,
                        selectedLabelColor = Blue600,
                        selectedLeadingIconColor = Blue600
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = outboxFilter == "UNSYNCED",
                            onClick = { outboxFilter = "UNSYNCED" },
                            label = { Text("Unsynced") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = outboxFilter == "FAILED",
                            onClick = { outboxFilter = "FAILED" },
                            label = { Text("Failed") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = outboxFilter == "DONE",
                            onClick = { outboxFilter = "DONE" },
                            label = { Text("Done") },
                            colors = chipColors
                        )
                        FilterChip(
                            selected = outboxFilter == "ALL",
                            onClick = { outboxFilter = "ALL" },
                            label = { Text("All") },
                            colors = chipColors
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val filtered = remember(outboxRecent, outboxFilter) {
                        when (outboxFilter) {
                            "FAILED" -> outboxRecent.filter { it.status == "FAILED" }
                            "DONE" -> outboxRecent.filter { it.status == "DONE" }
                            "UNSYNCED" -> outboxRecent.filter { it.status != "DONE" }
                            else -> outboxRecent
                        }
                    }

                    if (outboxRecent.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Outbox is empty", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered) { e ->
                                Surface(
                                    color = BgPrimary,
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 2.dp
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${e.entityType} • ${e.op}",
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                e.status,
                                                color = when (e.status) {
                                                    "PENDING" -> AlertOrange
                                                    "FAILED" -> LossRed
                                                    else -> ProfitGreen
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "id=${e.entityId ?: "—"} • opId=${e.opId.take(8)}…",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Created: ${lastSyncFmt.format(Date(e.createdAtMillis))}" +
                                                (e.lastAttemptAtMillis?.let { " • Tried: ${lastSyncFmt.format(Date(it))}" } ?: ""),
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        if (!e.error.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(e.error, color = LossRed, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        val now = System.currentTimeMillis()
                                                        val res = syncEngine.retryEntry(e.id, simulateSuccess = appPrefs.devSimulateSyncSuccess)
                                                        appPrefsStore.setLastSyncAttempt(now, res.message)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = TextPrimary),
                                                border = null
                                            ) { Text("Retry this", fontWeight = FontWeight.Bold) }

                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        db.outboxDao().markDone(e.id, System.currentTimeMillis())
                                                        appPrefsStore.setLastSyncAttempt(System.currentTimeMillis(), "Dev: marked outbox#${e.id} DONE")
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = ProfitGreen),
                                                border = null
                                            ) { Text("Mark done", fontWeight = FontWeight.Bold) }
                                        }

                                        if (e.status == "FAILED") {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        syncEngine.resetEntryFailedToPending(e.id)
                                                        appPrefsStore.setLastSyncAttempt(
                                                            System.currentTimeMillis(),
                                                            "Reset outbox#${e.id} FAILED → PENDING"
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = BgPrimary, contentColor = Blue600),
                                                border = null
                                            ) { Text("Reset (failed → pending)", fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMarkDoneConfirm) {
        AlertDialog(
            onDismissRequest = { showMarkDoneConfirm = false },
            title = { Text("Mark unsynced as done?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This is a developer-only helper to simulate a successful cloud sync.\n\nIt will mark ALL unsynced outbox entries as DONE locally. Continue?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repo.devMarkAllUnsyncedOutboxDone()
                            showMarkDoneConfirm = false
                        }
                    }
                ) { Text("Yes, mark done") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkDoneConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDemoConfirm) {
        AlertDialog(
            onDismissRequest = { showDemoConfirm = false },
            title = { Text("Apply demo setting?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will erase local data on the next app restart and then load ${if (pendingDemoEnabled) "demo data" else "normal mode"}.\n\nContinue?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            appPrefsStore.setDemoModeEnabled(pendingDemoEnabled)
                            appPrefsStore.requestDemoReset(true)
                            showDemoConfirm = false
                            onClose()
                        }
                    }
                ) { Text("Yes, apply") }
            },
            dismissButton = { TextButton(onClick = { showDemoConfirm = false }) { Text("Cancel") } }
        )
    }
}


