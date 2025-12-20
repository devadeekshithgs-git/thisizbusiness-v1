package com.kiranaflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.local.AppPrefsStore
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.ui.components.KiranaBottomNav
import com.kiranaflow.app.ui.components.OfflineBanner
import com.kiranaflow.app.ui.components.SettingsDrawer
import com.kiranaflow.app.ui.components.SyncStatusChip
import com.kiranaflow.app.ui.screens.billing.BillingScreen
import com.kiranaflow.app.ui.screens.billing.BillingViewModel
import com.kiranaflow.app.ui.screens.home.HomeScreen
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
import com.kiranaflow.app.util.StubSyncEngine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // #region agent log
        DebugLogger.log("MainActivity.kt:37", "onCreate started", mapOf(), "H1")
        // #endregion
        
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

    // Milestone D groundwork: global connectivity + outbox state (shared across UI).
    val ctx = LocalContext.current
    val monitor = remember(ctx) { ConnectivityMonitor(ctx) }
    val db = remember(ctx) { KiranaDatabase.getDatabase(ctx) }
    val repo = remember(ctx) { KiranaRepository(db) }
    val appPrefsStore = remember(ctx) { AppPrefsStore(ctx) }
    val syncEngine = remember(ctx) { StubSyncEngine(db, appPrefsStore) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            KiranaBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    // #region agent log
                    Log.d("Nav", "Tab selected=$tab currentTab=$currentTab currentRoute=$currentRoute")
                    // #endregion
                    if (currentTab != tab) {
                        navController.navigate(tab) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onTabLongPress = { tab ->
                    if (tab == "home") return@KiranaBottomNav
                    quickAction = tab
                    if (currentTab != tab) {
                        navController.navigate(tab) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        onOpenTransaction = { txId -> navController.navigate("transaction/$txId") }
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
                        onOpenSettings = { showSettingsDrawer = true }
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
                        onOpenSettings = { showSettingsDrawer = true }
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfflineBanner(isOnlineFlow = monitor.isOnline)
                    SyncStatusChip(isOnlineFlow = monitor.isOnline, pendingCountFlow = repo.pendingOutboxCount)
                }
            }
        }
    }
}
