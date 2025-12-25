package com.kiranaflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.ui.components.KiranaBottomNav
import com.kiranaflow.app.ui.components.SettingsDrawer
import com.kiranaflow.app.ui.screens.billing.BillingScreen
import com.kiranaflow.app.ui.screens.billing.BillingViewModel
import com.kiranaflow.app.ui.screens.home.HomeScreen
import com.kiranaflow.app.ui.screens.gst.GstReportsScreen
import com.kiranaflow.app.ui.screens.gst.Gstr1ReviewScreen
import com.kiranaflow.app.ui.screens.gst.GstReportsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.kiranaflow.app.util.gst.Gstr1ExcelGenerator
import com.kiranaflow.app.util.gst.Gstr1JsonGenerator
import com.kiranaflow.app.util.gst.Gstr1PdfGenerator
import com.kiranaflow.app.util.gst.GstFileExporter
import com.kiranaflow.app.ui.screens.inventory.InventoryScreen
import com.kiranaflow.app.ui.screens.parties.PartiesScreen
import com.kiranaflow.app.ui.screens.scanner.ScannerScreen
import com.kiranaflow.app.ui.screens.transactions.TransactionsScreen
import com.kiranaflow.app.ui.screens.transactions.TransactionDetailScreen
import com.kiranaflow.app.ui.screens.vendors.ItemsToReorderScreen
import com.kiranaflow.app.ui.screens.vendors.TotalPayablesScreen
import com.kiranaflow.app.ui.screens.vendors.VendorDetailScreen
import com.kiranaflow.app.ui.screens.vendors.VendorsScreen // Imported correctly
import com.kiranaflow.app.ui.theme.KiranaTheme
import com.kiranaflow.app.util.DebugLogger
import com.kiranaflow.app.util.ConnectivityMonitor
import com.kiranaflow.app.util.ImmediateSyncManager
import com.kiranaflow.app.util.StubSyncEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // #region agent log
        DebugLogger.log("MainActivity.kt:37", "onCreate started", mapOf(), "H1")
        // #endregion
        
        // Initialize immediate sync manager for always-online data sync
        ImmediateSyncManager.init(this)
        
        try {
            lifecycleScope.launch {
                try {
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:53", "Before seedInitialData", mapOf(), "H2")
                    // #endregion
                    val prefsStore = AppPrefsStore(this@MainActivity)
                    val prefs = prefsStore.prefs.first()

                    // Apply pending reset (requested via Settings).
                    if (prefs.demoResetRequested) {
                        runCatching {
                            // This will delete Room's on-disk DB; next getDatabase() recreates it.
                            deleteDatabase("kirana_database")
                        }
                        prefsStore.requestDemoReset(false)
                    }

                    // #region agent log
                    DebugLogger.log("MainActivity.kt:41", "Before getDatabase", mapOf(), "H1")
                    // #endregion
                    val db = KiranaDatabase.getDatabase(this@MainActivity)
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:44", "After getDatabase", mapOf(), "H1")
                    // #endregion

                    val repo = KiranaRepository(db)
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:48", "Repository created", mapOf(), "H2")
                    // #endregion

                    repo.seedInitialData(seedSyntheticData = prefs.demoModeEnabled)
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:56", "After seedInitialData", mapOf(), "H2")
                    // #endregion
                } catch (e: Exception) {
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:59", "seedInitialData error", mapOf("error" to (e.message ?: "unknown"), "stackTrace" to e.stackTraceToString()), "H2")
                    // #endregion
                }
            }
        } catch (e: Exception) {
            // #region agent log
            DebugLogger.log("MainActivity.kt:65", "onCreate error", mapOf("error" to (e.message ?: "unknown"), "stackTrace" to e.stackTraceToString()), "H1")
            // #endregion
        }

        setContent {
            val context = this
            val appPrefsStore = remember { AppPrefsStore(context) }

            // Privacy overlay: re-lock (mask numbers) when app goes to background.
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        scope.launch { appPrefsStore.setPrivacyUnlockedUntil(0L) }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            
            KiranaTheme {
                KiranaApp()
            }
        }
    }
}

@Composable
fun KiranaApp() {
    // #region agent log
    DebugLogger.log("MainActivity.kt:85", "KiranaApp composable entered", mapOf(), "H3")
    // #endregion
    
    val navController = rememberNavController()
    // #region agent log
    DebugLogger.log("MainActivity.kt:89", "NavController created", mapOf(), "H3")
    // #endregion
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // Map nested routes (scanner/*) to their parent tab so the bottom-nav always matches screen.
    val currentTab = when {
        currentRoute == null -> "home"
        currentRoute == "scanner/inventory" -> "inventory"
        currentRoute == "scanner/billing" -> "bill"

        // Keep vendors tab selected across vendor sub-routes (detail/payables/reorder).
        currentRoute.startsWith("vendors/") -> "vendors"

        // Transactions are launched from Home, keep Home selected while browsing them.
        currentRoute == "transactions" -> "home"
        currentRoute.startsWith("transaction") -> "home"

        else -> currentRoute
    }

    LaunchedEffect(currentRoute) {
        // #region agent log
        Log.d("Nav", "Route changed: $currentRoute -> tab=$currentTab")
        // #endregion
    }
    
    // #region agent log
    DebugLogger.log("MainActivity.kt:94", "Before billingViewModel creation", mapOf(), "H3")
    // #endregion
    val billingViewModel: BillingViewModel = viewModel()
    // #region agent log
    DebugLogger.log("MainActivity.kt:97", "After billingViewModel creation", mapOf(), "H3")
    // #endregion

    var showSettingsDrawer by rememberSaveable { mutableStateOf(false) }
    var quickAction by rememberSaveable { mutableStateOf<String?>(null) }

    val mainTabs = remember { listOf("home", "customers", "bill", "inventory", "vendors") }

    fun navigateToTab(tab: String) {
        if (currentTab != tab) {
            navController.navigate(tab) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Milestone D groundwork: global connectivity + outbox state (shared across UI).
    val ctx = LocalContext.current
    val monitor = remember(ctx) { ConnectivityMonitor(ctx) }
    val db = remember(ctx) { KiranaDatabase.getDatabase(ctx) }
    val repo = remember(ctx) { KiranaRepository(db) }
    val appPrefsStore = remember(ctx) { AppPrefsStore(ctx) }
    val syncEngine = remember(ctx) { StubSyncEngine(db, appPrefsStore, ctx) }
    
    // Auto-sync when connectivity is restored
    val isOnline by monitor.isOnline.collectAsState(initial = true)
    val pendingCount by repo.pendingOutboxCount.collectAsState(initial = 0)
    
    LaunchedEffect(isOnline, pendingCount) {
        // When we come online and have pending items, sync immediately
        if (isOnline && pendingCount > 0) {
            runCatching { syncEngine.syncAllPending() }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            KiranaBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    // #region agent log
                    Log.d("Nav", "Tab selected=$tab currentTab=$currentTab currentRoute=$currentRoute")
                    // #endregion
                    navigateToTab(tab)
                },
                onTabLongPress = { tab ->
                    if (tab == "home") return@KiranaBottomNav
                    quickAction = tab
                    navigateToTab(tab)
                }
            )
        }
    ) { innerPadding ->
        val density = LocalDensity.current
        val swipeThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
        var rootSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
                // Swipe in the center (WhatsApp/Instagram-style) to switch between bottom tabs.
                // Only enabled on the main tab routes to avoid breaking nested detail flows.
                .pointerInput(currentRoute, currentTab, rootSize) {
                    var armed = false
                    var dx = 0f
                    var dy = 0f

                    detectDragGestures(
                        onDragStart = { start: Offset ->
                            dx = 0f
                            dy = 0f

                            val route = currentRoute
                            if (route == null || route !in mainTabs) {
                                armed = false
                                return@detectDragGestures
                            }

                            val w = rootSize.width.toFloat()
                            val h = rootSize.height.toFloat()
                            if (w <= 0f || h <= 0f) {
                                armed = false
                                return@detectDragGestures
                            }

                            // Require gesture to start in center region:
                            // - avoid status/header area (top 15%)
                            // - avoid bottom nav area (bottom 20%)
                            val inCenter =
                                (start.x in (w * 0.15f)..(w * 0.85f)) &&
                                (start.y in (h * 0.15f)..(h * 0.80f))
                            armed = inCenter
                        },
                        onDragCancel = { armed = false },
                        onDragEnd = { armed = false },
                        onDrag = { change, dragAmount ->
                            if (!armed) return@detectDragGestures

                            dx += dragAmount.x
                            dy += dragAmount.y

                            val adx = abs(dx)
                            val ady = abs(dy)

                            // "Straight line" preference: ensure horizontal intent dominates vertical.
                            if (adx >= swipeThresholdPx && adx > (ady * 1.3f)) {
                                val idx = mainTabs.indexOf(currentTab)
                                if (idx >= 0) {
                                    // WhatsApp-style: swipe left -> next tab, swipe right -> previous tab
                                    val dir = if (dx < 0f) 1 else -1
                                    val next = (idx + dir).coerceIn(0, mainTabs.lastIndex)
                                    if (next != idx) navigateToTab(mainTabs[next])
                                }

                                // Consume after triggering so the underlying screen doesn't also react.
                                change.consumeAllChanges()
                                armed = false
                            }
                        }
                    )
                }
        ) {
            Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = "bill",
                modifier = Modifier, 
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                composable("home") { 
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:134", "Navigating to home screen", mapOf(), "H4")
                    // #endregion
                    HomeScreen(
                        onSettingsClick = { showSettingsDrawer = true },
                        onViewAllTransactions = { navController.navigate("transactions") },
                        onOpenTransaction = { txId -> navController.navigate("transaction/$txId") },
                        onOpenGstReports = { navController.navigate("gst") }
                    )
                }
                composable("gst") {
                    GstReportsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { showSettingsDrawer = true },
                        onOpenGstr1 = { from, toExclusive ->
                            navController.navigate("gst/gstr1?from=$from&to=$toExclusive")
                        }
                    )
                }
                composable(
                    route = "gst/gstr1?from={from}&to={to}",
                    arguments = listOf(
                        navArgument("from") { type = NavType.LongType },
                        navArgument("to") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val from = backStackEntry.arguments?.getLong("from") ?: 0L
                    val to = backStackEntry.arguments?.getLong("to") ?: 0L
                    val vm: GstReportsViewModel = viewModel()
                    val context = LocalContext.current
                    val state by vm.gstr1.collectAsState()

                    LaunchedEffect(from, to) {
                        if (from > 0L && to > 0L) vm.loadGstr1(from, to)
                    }

                    Gstr1ReviewScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { showSettingsDrawer = true },
                        onUpdateInvoiceNumber = { txId, invNo -> vm.updateInvoiceNumber(txId, invNo) },
                        onUpdateRecipientName = { txId, name -> vm.updateRecipientName(txId, name) },
                        onUpdateRecipientGstin = { txId, gstin -> vm.updateRecipientGstin(txId, gstin) },
                        onUpdatePlaceOfSupply = { txId, pos -> vm.updatePlaceOfSupply(txId, pos) },
                        onUpdateLineHsn = { lineId, hsn -> vm.updateLineHsn(lineId, hsn) },
                        onUpdateLineGstRate = { lineId, rate -> vm.updateLineGstRate(lineId, rate) },
                        onUpdateLineTaxableValue = { lineId, txval -> vm.updateLineTaxableValue(lineId, txval) },
                        onExportJson = {
                            val jsonObj = Gstr1JsonGenerator.build(state, vm::formatInvoiceDate)
                            val jsonStr = Gstr1JsonGenerator.toJsonString(jsonObj)
                            val name = "GSTR1_${jsonObj.fp}_${state.businessGstin.ifBlank { "GSTIN" }}.json"
                            val uri = GstFileExporter.saveTextToDownloads(context, name, "application/json", jsonStr)
                            if (uri != null) {
                                GstFileExporter.share(context, uri, "application/json", "Share GSTR-1 JSON")
                            }
                        },
                        onExportExcel = {
                            val bytes = Gstr1ExcelGenerator.generateXlsx(state)
                            val fp = Gstr1JsonGenerator.build(state, vm::formatInvoiceDate).fp
                            val name = "GSTR1_${fp}_${state.businessGstin.ifBlank { "GSTIN" }}.xlsx"
                            val uri = GstFileExporter.saveBytesToDownloads(
                                context = context,
                                displayName = name,
                                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                bytes = bytes
                            )
                            if (uri != null) {
                                GstFileExporter.share(
                                    context,
                                    uri,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "Share GSTR-1 Excel"
                                )
                            }
                        },
                        onExportPdf = {
                            val jsonObj = Gstr1JsonGenerator.build(state, vm::formatInvoiceDate)
                            val bytes = Gstr1PdfGenerator.generatePdfBytes(
                                state = state,
                                filingPeriod = jsonObj.fp,
                                formatInvoiceDate = vm::formatInvoiceDate
                            )
                            val name = "GSTR1_${jsonObj.fp}_${state.businessGstin.ifBlank { "GSTIN" }}.pdf"
                            val uri = GstFileExporter.saveBytesToDownloads(
                                context = context,
                                displayName = name,
                                mimeType = "application/pdf",
                                bytes = bytes
                            )
                            if (uri != null) {
                                GstFileExporter.share(context, uri, "application/pdf", "Share GSTR-1 PDF")
                            }
                        }
                    )
                }
                composable("transactions") {
                    TransactionsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTransaction = { txId -> navController.navigate("transaction/$txId") }
                    )
                }
                composable("transaction/{txId}") { backStackEntry ->
                    val idStr = backStackEntry.arguments?.getString("txId")
                    val id = idStr?.toIntOrNull() ?: 0
                    TransactionDetailScreen(
                        transactionId = id,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("customers") { 
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:139", "Navigating to customers screen", mapOf(), "H4")
                    // #endregion
                    PartiesScreen(
                        type = "CUSTOMER",
                        triggerAdd = quickAction == "customers",
                        onTriggerConsumed = { quickAction = null },
                        onOpenSettings = { showSettingsDrawer = true },
                        onOpenTransaction = { txId -> navController.navigate("transaction/$txId") }
                    )
                }
                composable("bill") { 
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:144", "Navigating to bill screen", mapOf(), "H4")
                    // #endregion
                    BillingScreen(
                        navController = navController,
                        viewModel = billingViewModel,
                        onOpenSettings = { showSettingsDrawer = true },
                        triggerScanner = quickAction == "bill",
                        onTriggerConsumed = { quickAction = null }
                    )
                }
                composable("inventory") { 
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:149", "Navigating to inventory screen", mapOf(), "H4")
                    // #endregion
                    InventoryScreen(
                        navController = navController,
                        triggerAddItem = quickAction == "inventory",
                        onTriggerConsumed = { quickAction = null },
                        onOpenSettings = { showSettingsDrawer = true }
                    )
                }
                composable("vendors") { 
                    // #region agent log
                    DebugLogger.log("MainActivity.kt:154", "Navigating to vendors screen", mapOf(), "H4")
                    // #endregion
                    PartiesScreen(
                        type = "VENDOR",
                        triggerAdd = quickAction == "vendors",
                        onTriggerConsumed = { quickAction = null },
                        onOpenSettings = { showSettingsDrawer = true },
                        onOpenReorder = { navController.navigate("vendors/reorder") },
                        onOpenPayables = { navController.navigate("vendors/payables") },
                        onOpenTransaction = { txId -> navController.navigate("transaction/$txId") }
                    )
                }
                composable("vendors/reorder") {
                    ItemsToReorderScreen(onBack = { navController.popBackStack() })
                }
                composable("vendors/payables") {
                    TotalPayablesScreen(onBack = { navController.popBackStack() })
                }
                composable("vendors/detail/{vendorId}") { backStackEntry ->
                    val idStr = backStackEntry.arguments?.getString("vendorId")
                    val id = idStr?.toIntOrNull() ?: 0
                    VendorDetailScreen(
                        vendorId = id,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                // Scanner for Inventory (Single Scan)
                composable("scanner/inventory") { 
                    ScannerScreen(
                        isContinuous = false,
                        onBarcodeScanned = { barcode ->
                            // Ensure we always return to Inventory and deliver the barcode there.
                            // #region agent log
                            Log.d("Nav", "Inventory scan result barcode=$barcode")
                            // #endregion
                            runCatching {
                                navController.getBackStackEntry("inventory").savedStateHandle.set("barcode", barcode)
                                navController.popBackStack("inventory", false)
                            }.onFailure { 
                                Log.e("Nav", "Failed to deliver inventory barcode; navigating to inventory", it)
                                navController.navigate("inventory") 
                            }
                        },
                        onClose = { navController.popBackStack("inventory", false) }
                    )
                }
                
                // Scanner for Billing (Continuous Scan)
                composable("scanner/billing") { 
                    ScannerScreen(
                        isContinuous = true,
                        onBarcodeScanned = { barcode ->
                            // #region agent log
                            Log.d("Nav", "Billing scan result barcode=$barcode")
                            // #endregion
                            billingViewModel.addItemToCartByBarcode(barcode)
                        },
                        onClose = { navController.popBackStack("bill", false) }
                    )
                }
            }
            }

            SettingsDrawer(isOpen = showSettingsDrawer, onClose = { showSettingsDrawer = false })
        }
    }
}
