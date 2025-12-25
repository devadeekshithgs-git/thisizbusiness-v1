package com.kiranaflow.app.ui.screens.billing

import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.util.Log
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.kiranaflow.app.R
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.ShopSettingsStore
import com.kiranaflow.app.data.local.ShopSettings
import com.kiranaflow.app.data.local.KiranaDatabase
import com.kiranaflow.app.data.repository.KiranaRepository
import com.kiranaflow.app.ui.components.CircleButton
import com.kiranaflow.app.ui.components.SearchField
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.components.KiranaCard
import com.kiranaflow.app.ui.components.KiranaInput
import com.kiranaflow.app.ui.components.ValleyTopBar
import com.kiranaflow.app.ui.components.dialogs.CompletePaymentModal
import com.kiranaflow.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kiranaflow.app.ui.screens.scanner.ScannerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import java.io.File
import com.kiranaflow.app.util.InputFilters
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.kiranaflow.app.ui.screens.billing.BillSavedEvent
import com.kiranaflow.app.ui.screens.billing.BillSavedLineItem
import com.kiranaflow.app.util.ReceiptImageRenderer
import com.kiranaflow.app.util.ReceiptRenderData
import com.kiranaflow.app.util.ReceiptRenderItem

// Save/restore scanner overlay state across configuration changes (e.g., landscape rotation).
private val BillingScanOverlayStateSaver = androidx.compose.runtime.saveable.Saver<BillingScanOverlayState, List<Any?>>(
    save = { state ->
        when (state) {
            BillingScanOverlayState.Idle -> listOf("IDLE", null, null)
            is BillingScanOverlayState.Added -> listOf("ADDED", state.itemId, state.name)
            is BillingScanOverlayState.NotFound -> listOf("NOT_FOUND", null, state.barcode)
        }
    },
    restore = { restored ->
        val type = restored.getOrNull(0) as? String ?: return@Saver BillingScanOverlayState.Idle
        when (type) {
            "ADDED" -> {
                val itemId = restored.getOrNull(1) as? Int ?: return@Saver BillingScanOverlayState.Idle
                val name = restored.getOrNull(2) as? String ?: ""
                BillingScanOverlayState.Added(itemId, name)
            }
            "NOT_FOUND" -> {
                val barcode = restored.getOrNull(2) as? String ?: return@Saver BillingScanOverlayState.Idle
                BillingScanOverlayState.NotFound(barcode)
            }
            else -> BillingScanOverlayState.Idle
        }
    }
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BillingScreen(
    navController: NavController,
    viewModel: BillingViewModel = viewModel(),
    onCompletePayment: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    triggerScanner: Boolean = false,
    onTriggerConsumed: () -> Unit = {}
) {
    // #region agent log
    SideEffect { Log.d("BillingScreen", "BillingScreen composed") }
    // #endregion

    val billItems by viewModel.billItems.collectAsState()
    val totalAmount by viewModel.totalAmount.collectAsState()
    // Persisted shop settings (for UPI QR)
    val context = LocalContext.current
    val settingsStore = remember(context) { ShopSettingsStore(context) }
    val shopSettings by settingsStore.settings.collectAsState(initial = ShopSettings())

    // Stable repository for customer list + add-new-customer from payment modal
    val db = remember(context) { KiranaDatabase.getDatabase(context) }
    val repo = remember(db) { KiranaRepository(db) }
    val customers by repo.customers.collectAsState(initial = emptyList())

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var showCheckoutDialog by remember { mutableStateOf(false) }
    // Must survive rotation; otherwise scanner overlay exits on landscape change.
    var showBillingScanner by rememberSaveable { mutableStateOf(false) }
    var scanOverlayState by rememberSaveable(stateSaver = BillingScanOverlayStateSaver) {
        mutableStateOf<BillingScanOverlayState>(BillingScanOverlayState.Idle)
    }
    var priceEditItem by remember { mutableStateOf<BoxCartItem?>(null) }
    var showPriceEditDialog by remember { mutableStateOf(false) }
    var showTxnSavedOverlay by remember { mutableStateOf(false) }
    var pendingDigitalBillEvent by remember { mutableStateOf<BillSavedEvent?>(null) }
    var showDigitalBillPrompt by remember { mutableStateOf(false) }
    var screenMode by rememberSaveable { mutableStateOf("BILL") } // BILL | EXPENSE
    var showQuickAddProduct by remember { mutableStateOf(false) }
    var quickAddName by remember { mutableStateOf("") }
    var quickAddPrice by remember { mutableStateOf("") }
    var quickAddStock by remember { mutableStateOf("") }
    var quickAddNameError by remember { mutableStateOf(false) }
    var quickAddErrorText by remember { mutableStateOf<String?>(null) }

    // Loose items: step is in KG (e.g., 0.25kg) to match BillingViewModel qty units.
    val looseStepByItemId = remember { mutableStateMapOf<Int, Double>() }
    var customWeightForItemId by remember { mutableStateOf<Int?>(null) }
    var customWeightText by remember { mutableStateOf("") }

    // NavHost is already padded by Scaffold's bottomBar (see MainActivity). Keep only a small
    // extra gap so the last item doesn't "kiss" the bottom edge above the menu bar.
    val bottomNavSafeGap = 16.dp

    // Expense form state (A6)
    val vendors by repo.vendors.collectAsState(initial = emptyList())
    val allTx by repo.allTransactions.collectAsState(initial = emptyList())
    var expenseAmount by rememberSaveable { mutableStateOf("") }
    var expenseDescription by rememberSaveable { mutableStateOf("") }
    var expensePaymentMode by rememberSaveable { mutableStateOf("CASH") } // CASH | UPI | CREDIT
    var expenseReceiptUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVendorId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showVendorDropdown by remember { mutableStateOf(false) }
    var vendorQuery by remember { mutableStateOf("") }
    var showAddVendorDialog by remember { mutableStateOf(false) }
    var newVendorName by remember { mutableStateOf("") }
    var newVendorPhone by remember { mutableStateOf("") }
    var newVendorGst by remember { mutableStateOf("") }

    val defaultExpenseCategories = remember {
        listOf("Supplies", "Rent", "Salary", "Utilities", "Transport", "Misc")
    }
    val parsedCategories = remember(allTx) {
        allTx.asSequence()
            .filter { it.type == "EXPENSE" && it.title.startsWith("Expense") }
            .mapNotNull { tx ->
                // Title format: Expense • Category • Desc • Vendor (MODE)
                tx.title.split("•").map { it.trim() }.getOrNull(1)?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .toList()
    }
    val expenseCategories = remember(parsedCategories) {
        (defaultExpenseCategories + parsedCategories).distinct()
    }
    var expenseCategory by rememberSaveable { mutableStateOf("Supplies") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var categoryQuery by remember { mutableStateOf("") }

    // Receipt photo capture (Expense mode)
    var pendingReceiptCameraUri by remember { mutableStateOf<Uri?>(null) }
    val receiptCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            expenseReceiptUri = pendingReceiptCameraUri?.toString()
        }
        pendingReceiptCameraUri = null
    }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }

    LaunchedEffect(triggerScanner) {
        if (triggerScanner) {
            showBillingScanner = true
            onTriggerConsumed()
        }
    }
    val vibrator: Vibrator? = remember {
        runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull()
    }
    val mediaPlayer: MediaPlayer? = remember {
        runCatching { MediaPlayer.create(context, R.raw.beep) }.getOrNull()
    }

    // Voice confirmation (TTS) for transaction saved.
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { tts.shutdown() }
        }
    }

    // More reliable for short loud beeps than MediaPlayer (fallback kept).
    val soundPool = remember {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attrs)
            .build()
    }
    var beepSoundId by remember { mutableIntStateOf(0) }
    var beepLoaded by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == beepSoundId && status == 0) {
                beepLoaded = true
            }
        }
        beepSoundId = runCatching { soundPool.load(context, R.raw.beep, 1) }.getOrDefault(0)
        onDispose {
            runCatching { mediaPlayer?.release() }
            runCatching { soundPool.release() }
        }
    }

    val newBarcode = navController.currentBackStackEntry?.savedStateHandle?.getLiveData<String>("barcode")?.observeAsState()

    LaunchedEffect(newBarcode) {
        newBarcode?.value?.let {
            viewModel.addItemToCartByBarcode(it)
            // Provide feedback
            // Provide feedback (safe on devices without vibrator / media)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(100)
                }
            }
            runCatching {
                if (beepLoaded) {
                    soundPool.play(beepSoundId, 1f, 1f, 1, 0, 1f)
                } else {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) mp.seekTo(0)
                        mp.setVolume(1f, 1f)
                        mp.start()
                    }
                }
            }
            // Clear the barcode to prevent re-triggering
            navController.currentBackStackEntry?.savedStateHandle?.set("barcode", null)
        }
    }

    // Listen to scan results to drive overlays + beep feedback.
    LaunchedEffect(Unit) {
        viewModel.scanResults.collectLatest { result ->
            when (result) {
                is BillingScanResult.Added -> {
                    scanOverlayState = BillingScanOverlayState.Added(result.item.id, result.item.name)
                    // Loud beep + haptic on success
                    runCatching {
                        if (beepLoaded) {
                            soundPool.play(beepSoundId, 1f, 1f, 1, 0, 1f)
                        } else {
                            mediaPlayer?.let { mp ->
                                if (mp.isPlaying) mp.seekTo(0)
                                mp.setVolume(1f, 1f)
                                mp.start()
                            }
                        }
                    }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(120)
                        }
                    }
                    // Keep the success overlay visible until the next scan (wireframe requirement),
                    // so the user can quickly adjust quantity from this overlay.
                }
                is BillingScanResult.NotFound -> {
                    scanOverlayState = BillingScanOverlayState.NotFound(result.barcode)
                }
            }
        }
    }

    // Transaction saved confirmation (voice + brief overlay)
    LaunchedEffect(Unit) {
        viewModel.billSavedEvents.collectLatest { evt ->
            showTxnSavedOverlay = true
            runCatching {
                tts.language = Locale.getDefault()
                tts.speak("Transaction saved", TextToSpeech.QUEUE_FLUSH, null, "txn_saved")
            }
            // Optional digital bill prompt (only when a customer with a valid phone is linked)
            val cust = customers.firstOrNull { it.id == evt.customerId }
            val hasPhone = cust != null && cust.phone.filter { ch -> ch.isDigit() }.length >= 10
            if (evt.customerId != null && hasPhone) {
                pendingDigitalBillEvent = evt
                showDigitalBillPrompt = true
            } else {
                pendingDigitalBillEvent = null
                showDigitalBillPrompt = false
            }
            delay(900)
            showTxnSavedOverlay = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(GrayBg)) {
        // Bill screen content (blurred/faded when scanner overlay is open)
        val canBlur = Build.VERSION.SDK_INT >= 31
        val contentFadeModifier =
            if (showBillingScanner && canBlur) Modifier.blur(14.dp).graphicsLayer { alpha = 0.35f }
            else if (showBillingScanner) Modifier.graphicsLayer { alpha = 0.35f }
            else Modifier

        if (screenMode == "BILL") {
            Column(modifier = Modifier.fillMaxSize().then(contentFadeModifier)) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    item {
                        ValleyTopBar(
                            title = "New Bill",
                            subtitle = "Scan or search items",
                            actionIcon = Icons.Default.QrCodeScanner,
                            onAction = {
                                showBillingScanner = true
                                scanOverlayState = BillingScanOverlayState.Idle
                            },
                            onSettings = onOpenSettings,
                            containerColor = Color.Black,
                            showCenterActionButton = false,
                            showActionIcon = true,
                            backgroundColor = GrayBg
                        )
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                    item {
                        // Mode switch: Bill Customer vs Record Expense
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { screenMode = "BILL" },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = KiranaGreenBg,
                                    contentColor = KiranaGreen
                                ),
                                border = null
                            ) {
                                Text("Bill Customer", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { screenMode = "EXPENSE" },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LossRed.copy(alpha = 0.75f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Record Expense", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = { viewModel.searchItems(it) },
                            placeholder = "Search items manually...",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (searchResults.isEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    quickAddName = searchQuery.trim()
                                                    quickAddPrice = ""
                                                    quickAddStock = ""
                                                    quickAddNameError = false
                                                    showQuickAddProduct = true
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = KiranaGreen)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Add \"$searchQuery\" as new product",
                                                modifier = Modifier.weight(1f),
                                                color = TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        searchResults.take(5).forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.addItemToBill(item, 1.0) }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(item.name, modifier = Modifier.weight(1f), color = TextPrimary)
                                                val priceText =
                                                    if (item.isLoose) "₹${item.pricePerKg.toInt()}/kg" else "₹${item.price.toInt()}"
                                                Text(priceText, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (billItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Empty Cart", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Scan or search to start billing", color = TextSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(billItems) { billItem ->
                            val isLoose = billItem.item.isLoose
                            if (isLoose) {
                                LaunchedEffect(billItem.item.id) {
                                    if (looseStepByItemId[billItem.item.id] == null) {
                                        looseStepByItemId[billItem.item.id] = 0.25
                                    }
                                }
                            }
                            val step = if (isLoose) (looseStepByItemId[billItem.item.id] ?: 0.25) else 1.0
                            val subtotal =
                                if (isLoose) billItem.item.pricePerKg * billItem.qty
                                else billItem.item.price * billItem.qty
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                CartItemCard(
                                    itemName = billItem.item.name,
                                    rackLocation = billItem.item.rackLocation,
                                    imageUri = billItem.item.imageUri,
                                    qty = billItem.qty,
                                    isLoose = isLoose,
                                    pricePerKg = billItem.item.pricePerKg,
                                    unitPrice = billItem.item.price,
                                    subtotal = subtotal,
                                    selectedStepKg = step,
                                    onSelectStepKg = { kg ->
                                        looseStepByItemId[billItem.item.id] = kg
                                        viewModel.updateItemQuantity(billItem.item.id, kg)
                                    },
                                    onCustomWeight = {
                                        customWeightForItemId = billItem.item.id
                                        customWeightText = String.format("%.3f", billItem.qty).trimEnd('0').trimEnd('.')
                                    },
                                    onInc = { viewModel.updateItemQuantity(billItem.item.id, billItem.qty + step) },
                                    onDec = {
                                        val newQty = billItem.qty - step
                                        if (newQty > 0.0) viewModel.updateItemQuantity(billItem.item.id, newQty)
                                        else viewModel.removeItemFromBill(billItem.item.id)
                                    },
                                    onDelete = { viewModel.removeItemFromBill(billItem.item.id) },
                                    onEditPrice = {
                                        priceEditItem = billItem
                                        showPriceEditDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Fixed footer: always sits right above the bottom menu bar (no big gap).
                if (billItems.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = bottomNavSafeGap),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = BgPrimary)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                Text("₹${totalAmount.toInt()}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showCheckoutDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ProfitGreen,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checkout", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(bottomNavSafeGap))
                }
            }
        } else {
            // EXPENSE MODE: keep it scrollable; header+mode switch are part of scroll.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(contentFadeModifier)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ValleyTopBar(
                    title = "Record Expense",
                    subtitle = "Track business spendings",
                    actionIcon = Icons.Default.QrCodeScanner,
                    onAction = {},
                    onSettings = onOpenSettings,
                    containerColor = Color.Black,
                    showCenterActionButton = false,
                    showActionIcon = false,
                    backgroundColor = GrayBg
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { screenMode = "BILL" },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = BgPrimary,
                            contentColor = TextPrimary
                        ),
                        border = null
                    ) {
                        Text("Bill Customer", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { screenMode = "EXPENSE" },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LossRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Record Expense", fontWeight = FontWeight.Bold)
                    }
                }

                // Continue with the existing expense UI (unchanged below this point)
                val scope = rememberCoroutineScope()
                val selectedVendor = vendors.firstOrNull { it.id == selectedVendorId }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KiranaInput(
                        value = expenseAmount,
                        onValueChange = { expenseAmount = InputFilters.decimal(it) },
                        placeholder = "0",
                        label = "Amount (₹)",
                        keyboardType = KeyboardType.Decimal
                    )

                    // Receipt photo (optional)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BgPrimary),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Receipt photo (optional)", fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (!expenseReceiptUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = expenseReceiptUri,
                                    contentDescription = "Expense receipt photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Text("Attach a photo of the bill for reference.", color = TextSecondary, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
                                        val photoFile = File.createTempFile("expense_receipt_", ".jpg", dir)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                        pendingReceiptCameraUri = uri
                                        receiptCameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Take photo", fontWeight = FontWeight.Bold)
                                }
                                if (!expenseReceiptUri.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { expenseReceiptUri = null },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Remove", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Text("PAY TO (VENDOR)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    ExposedDropdownMenuBox(
                        expanded = showVendorDropdown,
                        onExpandedChange = { showVendorDropdown = !showVendorDropdown }
                    ) {
                        OutlinedTextField(
                            value = selectedVendor?.name ?: "No Vendor",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showVendorDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = White,
                                unfocusedContainerColor = White,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = showVendorDropdown,
                            onDismissRequest = { showVendorDropdown = false }
                        ) {
                            // Search header (must be focusable; avoid disabled DropdownMenuItem wrapping)
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                OutlinedTextField(
                                    value = vendorQuery,
                                    onValueChange = { vendorQuery = it },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    placeholder = { Text("Search vendor...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = White,
                                        unfocusedContainerColor = White,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                            Divider(color = Gray200)

                            DropdownMenuItem(
                                text = { Text("No Vendor", color = TextPrimary) },
                                onClick = {
                                    selectedVendorId = null
                                    showVendorDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Blue600)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add new vendor", color = Blue600, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = { showAddVendorDialog = true }
                            )
                            vendors
                                .filter { vendorQuery.isBlank() || it.name.contains(vendorQuery, true) || it.phone.contains(vendorQuery) }
                                .forEach { v ->
                                    DropdownMenuItem(
                                        text = { Text(v.name, color = TextPrimary) },
                                        onClick = {
                                            selectedVendorId = v.id
                                            showVendorDropdown = false
                                            vendorQuery = ""
                                        }
                                    )
                                }
                        }
                    }

                    Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    ExposedDropdownMenuBox(
                        expanded = showCategoryDropdown,
                        onExpandedChange = { showCategoryDropdown = !showCategoryDropdown }
                    ) {
                        OutlinedTextField(
                            value = expenseCategory,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = White,
                                unfocusedContainerColor = White,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            // Search header (focusable)
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                OutlinedTextField(
                                    value = categoryQuery,
                                    onValueChange = { categoryQuery = it },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    placeholder = { Text("Search category...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = White,
                                        unfocusedContainerColor = White,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                            Divider(color = Gray200)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Blue600)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add new category", color = Blue600, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = { showAddCategoryDialog = true }
                            )
                            expenseCategories
                                .filter { categoryQuery.isBlank() || it.contains(categoryQuery, true) }
                                .forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c, color = TextPrimary) },
                                        onClick = {
                                            expenseCategory = c
                                            showCategoryDropdown = false
                                            categoryQuery = ""
                                        }
                                    )
                                }
                        }
                    }

                    OutlinedTextField(
                        value = expenseDescription,
                        onValueChange = { expenseDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Text("PAYMENT MODE", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = expensePaymentMode == "CASH", onClick = { expensePaymentMode = "CASH" }, label = { Text("Cash") })
                        FilterChip(selected = expensePaymentMode == "UPI", onClick = { expensePaymentMode = "UPI" }, label = { Text("UPI") })
                        FilterChip(selected = expensePaymentMode == "CREDIT", onClick = { expensePaymentMode = "CREDIT" }, label = { Text("Udhaar") })
                    }

                    Button(
                        onClick = {
                            val amt = expenseAmount.toDoubleOrNull() ?: 0.0
                            if (amt <= 0) return@Button
                            scope.launch {
                                repo.recordExpense(
                                    amount = amt,
                                    mode = expensePaymentMode,
                                    vendor = selectedVendor,
                                    category = expenseCategory,
                                    description = expenseDescription,
                                    receiptImageUri = expenseReceiptUri
                                )
                                // Reuse the transaction-saved overlay + voice.
                                showTxnSavedOverlay = true
                                runCatching {
                                    if (ttsReady) {
                                        tts.language = Locale.getDefault()
                                        tts.speak("Transaction saved", TextToSpeech.QUEUE_FLUSH, null, "txn_saved")
                                    }
                                }
                                delay(900)
                                showTxnSavedOverlay = false

                                // Reset form
                                expenseAmount = ""
                                expenseDescription = ""
                                selectedVendorId = null
                                expensePaymentMode = "CASH"
                                expenseReceiptUri = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Expense", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Add Vendor dialog (minimal)
                if (showAddVendorDialog) {
                    var addVendorPhoneError by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { showAddVendorDialog = false },
                        title = { Text("Add Vendor", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .imePadding()
                                    .heightIn(max = 520.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(value = newVendorName, onValueChange = { newVendorName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(
                                    value = newVendorPhone,
                                    onValueChange = { newVendorPhone = InputFilters.digitsOnly(it, maxLen = 10) },
                                    label = { Text("Phone") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true
                                )
                                if (addVendorPhoneError) {
                                    Text(
                                        text = "Mobile number must be 10 digits",
                                        color = LossRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                OutlinedTextField(value = newVendorGst, onValueChange = { newVendorGst = it }, label = { Text("GST (optional)") }, modifier = Modifier.fillMaxWidth())
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    if (newVendorPhone.length != 10) {
                                        addVendorPhoneError = true
                                        return@launch
                                    }
                                    addVendorPhoneError = false
                                    val created = repo.addVendor(newVendorName, newVendorPhone, newVendorGst)
                                    if (created != null) {
                                        selectedVendorId = created.id
                                    }
                                    newVendorName = ""; newVendorPhone = ""; newVendorGst = ""
                                    showAddVendorDialog = false
                                }
                            }) { Text("Add") }
                        },
                        dismissButton = { TextButton(onClick = { showAddVendorDialog = false }) { Text("Cancel") } }
                    )
                }

                if (showAddCategoryDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddCategoryDialog = false },
                        title = { Text("Add Category", fontWeight = FontWeight.Bold) },
                        text = {
                            OutlinedTextField(
                                value = newCategory,
                                onValueChange = { newCategory = it },
                                label = { Text("Category name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val c = newCategory.trim()
                                if (c.isNotBlank()) expenseCategory = c
                                newCategory = ""
                                showAddCategoryDialog = false
                            }) { Text("Add") }
                        },
                        dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }

    // Billing scanner overlay (camera on top of blurred bill screen)
    if (showBillingScanner) {
        BillingScannerOverlay(
            overlayState = scanOverlayState,
            billItems = billItems,
            onSetQty = { itemId, qty ->
                viewModel.updateItemQuantity(itemId, qty)
            },
            onDismiss = { 
                showBillingScanner = false
                scanOverlayState = BillingScanOverlayState.Idle
            },
            onBarcodeScanned = { barcode ->
                // When user scans again, clear previous NotFound state immediately.
                if (scanOverlayState is BillingScanOverlayState.NotFound) scanOverlayState = BillingScanOverlayState.Idle
                viewModel.addItemToCartByBarcode(barcode)
            },
            onAddToInventory = { barcode ->
                showBillingScanner = false
                scanOverlayState = BillingScanOverlayState.Idle
                // Jump to inventory and prefill barcode, but ensure BACK returns to Billing.
                runCatching {
                    android.util.Log.d("BillingOverlay", "AddNowToInventory clicked barcode=$barcode")
                    // Navigate first; then write to the *current* entry (now inventory) to avoid backstack lookup races.
                    navController.navigate("inventory") { launchSingleTop = true; restoreState = true }
                    navController.currentBackStackEntry?.savedStateHandle?.set("barcode", barcode)
                    navController.currentBackStackEntry?.savedStateHandle?.set("return_to", "bill")
                }.onFailure { e ->
                    android.util.Log.e("BillingOverlay", "AddNowToInventory failed; navigating to inventory fallback", e)
                    navController.navigate("inventory") { launchSingleTop = true }
                    // Best-effort: still try to prefill even on fallback
                    navController.currentBackStackEntry?.savedStateHandle?.set("barcode", barcode)
                    navController.currentBackStackEntry?.savedStateHandle?.set("return_to", "bill")
                }
            }
        )
    }

    // Complete Payment Modal
    if (showCheckoutDialog && billItems.isNotEmpty()) {
        CompletePaymentModal(
            totalAmount = totalAmount,
            customers = customers,
            shopName = shopSettings.shopName,
            upiId = shopSettings.upiId,
            onDismiss = { showCheckoutDialog = false },
            onComplete = { customerId, paymentMethod ->
                viewModel.completeBill(paymentMethod, customerId)
                showCheckoutDialog = false
                onCompletePayment()
            },
            onAddCustomer = { name, phone10 ->
                repo.addCustomer(name, phone10)
            }
        )
    }

    // Digital bill prompt after successful confirmation.
    if (showDigitalBillPrompt && pendingDigitalBillEvent != null) {
        val evt = pendingDigitalBillEvent!!
        val customer = customers.firstOrNull { it.id == evt.customerId }
        val custName = customer?.name.orEmpty()
        val custPhone = customer?.phone.orEmpty()
        DigitalBillPromptDialog(
            shopName = shopSettings.shopName,
            shopPhone = shopSettings.shopPhone,
            billNo = evt.txId.toString(),
            customerName = custName,
            customerPhone = custPhone,
            createdAtMillis = evt.createdAtMillis,
            paymentMode = evt.paymentMode,
            items = evt.items,
            totalAmount = evt.totalAmount,
            onDismiss = { showDigitalBillPrompt = false; pendingDigitalBillEvent = null }
        )
    }

    if (showPriceEditDialog && priceEditItem != null) {
        val item = priceEditItem!!
        var priceText by remember(item.item.id) { mutableStateOf(item.item.price.toString()) }
        var mode by remember(item.item.id) { mutableStateOf("DISCOUNT") } // DISCOUNT or PERMANENT
        AlertDialog(
            onDismissRequest = { showPriceEditDialog = false; priceEditItem = null },
            title = { Text("Edit Price", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.item.name, color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = InputFilters.decimal(it) },
                        label = { Text("Unit price (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = White,
                            unfocusedContainerColor = White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = mode == "DISCOUNT",
                            onClick = { mode = "DISCOUNT" },
                            label = { Text("Discount (this bill)") }
                        )
                        FilterChip(
                            selected = mode == "PERMANENT",
                            onClick = { mode = "PERMANENT" },
                            label = { Text("Permanent") }
                        )
                    }
                    Text(
                        if (mode == "PERMANENT") "Permanent updates inventory selling price for future bills."
                        else "Discount applies only to this bill.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = priceText.toDoubleOrNull()
                    if (p != null && p > 0) {
                        viewModel.updateCartItemUnitPrice(
                            itemId = item.item.id,
                            newUnitPrice = p,
                            persist = mode == "PERMANENT"
                        )
                    }
                    showPriceEditDialog = false
                    priceEditItem = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPriceEditDialog = false; priceEditItem = null }) { Text("Cancel") } }
        )
    }

    if (showQuickAddProduct) {
        AlertDialog(
            onDismissRequest = { showQuickAddProduct = false },
            title = { Text("Add New Product", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = quickAddName,
                        onValueChange = { quickAddName = it; if (quickAddNameError) quickAddNameError = false },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (quickAddNameError) {
                        Text(
                            text = quickAddErrorText ?: "Product name is required",
                            color = LossRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = quickAddPrice,
                        onValueChange = { quickAddPrice = InputFilters.decimal(it) },
                        label = { Text("Selling Price (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = quickAddStock,
                        onValueChange = { quickAddStock = InputFilters.digitsOnly(it) },
                        label = { Text("Stock") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    val name = quickAddName.trim()
                    if (name.isBlank()) {
                        quickAddNameError = true
                        quickAddErrorText = "Product name is required"
                        return@TextButton
                    }
                    quickAddNameError = false
                    quickAddErrorText = null
                    val price = quickAddPrice.toDoubleOrNull() ?: 0.0
                    val stock = quickAddStock.toIntOrNull() ?: 0
                    scope.launch {
                        val result = repo.addItemReturning(
                            ItemEntity(
                                name = name,
                                price = price,
                                stock = stock,
                                category = "General",
                                rackLocation = null,
                                marginPercentage = 0.0,
                                barcode = null,
                                costPrice = 0.0,
                                gstPercentage = null,
                                reorderPoint = 10,
                                vendorId = null,
                                imageUri = null,
                                expiryDateMillis = null,
                                isDeleted = false
                            )
                        )
                        when (result) {
                            is KiranaRepository.ItemAddResult.Success -> {
                                viewModel.addItemToBill(result.item, 1.0)
                                showQuickAddProduct = false
                            }
                            is KiranaRepository.ItemAddResult.DuplicateName -> {
                                quickAddNameError = true
                                quickAddErrorText = "Already exists: ${result.existingName}"
                            }
                            is KiranaRepository.ItemAddResult.DuplicateBarcode -> {
                                quickAddNameError = true
                                quickAddErrorText = "Barcode already used by: ${result.existingName}"
                            }
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddProduct = false }) { Text("Cancel") }
            }
        )
    }

    if (showTxnSavedOverlay) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Soft scrim so it feels like a brief confirmation dialog (but auto-dismisses quickly).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                color = BgPrimary,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = ProfitGreenBg) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ProfitGreen,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column {
                        Text("Transaction confirmed", color = TextPrimary, fontWeight = FontWeight.Black)
                        Text("Saved successfully", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (customWeightForItemId != null) {
        AlertDialog(
            onDismissRequest = { customWeightForItemId = null },
            title = { Text("Custom weight (kg)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    KiranaInput(
                        value = customWeightText,
                        onValueChange = { customWeightText = InputFilters.decimal(it, maxDecimals = 3) },
                        placeholder = "e.g. 0.75",
                        label = "WEIGHT (kg)",
                        keyboardType = KeyboardType.Decimal
                    )
                    Text("Tip: 0.5 = 500g", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = customWeightForItemId ?: return@TextButton
                    val kg = customWeightText.toDoubleOrNull() ?: 0.0
                    if (kg > 0.0) {
                        // Custom weight also becomes the active step going forward.
                        looseStepByItemId[id] = kg
                        viewModel.updateItemQuantity(id, kg)
                    }
                    customWeightForItemId = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { customWeightForItemId = null }) { Text("Cancel") } }
        )
    }
}

private sealed interface BillingScanOverlayState {
    data object Idle : BillingScanOverlayState
    data class Added(val itemId: Int, val name: String) : BillingScanOverlayState
    data class NotFound(val barcode: String) : BillingScanOverlayState
}

@Composable
private fun DigitalBillPromptDialog(
    shopName: String,
    shopPhone: String,
    billNo: String,
    customerName: String,
    customerPhone: String,
    createdAtMillis: Long,
    paymentMode: String,
    items: List<BillSavedLineItem>,
    totalAmount: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val paymentLabel = if (paymentMode == "CREDIT") "PAY_LATER" else "PAID"
    val normalizedPhone = customerPhone.filter { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send digital bill?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = customerName.ifBlank { "Customer" } + (if (normalizedPhone.isNotBlank()) " • $normalizedPhone" else ""),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = "A receipt image will be generated and shared.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val receiptData = ReceiptRenderData(
                        shopName = shopName.ifBlank { "SHOP" },
                        shopPhone = shopPhone.trim().ifBlank { null },
                        billNo = billNo,
                        customerName = customerName.trim().ifBlank { null },
                        customerPhone = normalizedPhone.ifBlank { null },
                        paymentLabel = paymentLabel,
                        txTypeLabel = "Sales",
                        createdAtMillis = createdAtMillis,
                        items = items.map {
                            ReceiptRenderItem(
                                itemId = it.itemId,
                                name = it.name,
                                qty = it.qty,
                                unitPrice = it.unitPrice,
                                lineTotal = it.lineTotal
                            )
                        },
                        totalAmount = totalAmount
                    )
                    val bmp = ReceiptImageRenderer.renderToBitmap(receiptData)
                    val uri = ReceiptImageRenderer.savePngToCache(
                        context = context,
                        bitmap = bmp,
                        fileName = "bill_${billNo}.png"
                    )
                    // Prefer WhatsApp if installed; otherwise fall back to chooser.
                    runCatching {
                        ReceiptImageRenderer.shareImage(
                            context = context,
                            imageUri = uri,
                            chooserTitle = "Send bill",
                            packageName = "com.whatsapp"
                        )
                    }.onFailure {
                        ReceiptImageRenderer.shareImage(
                            context = context,
                            imageUri = uri,
                            chooserTitle = "Send bill"
                        )
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Skip") }
        }
    )
}

@Composable
private fun BillingScannerOverlay(
    overlayState: BillingScanOverlayState,
    billItems: List<BoxCartItem>,
    onSetQty: (Int, Double) -> Unit,
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onAddToInventory: (String) -> Unit
) {
    val scannedLine = when (overlayState) {
        is BillingScanOverlayState.Added -> billItems.firstOrNull { it.item.id == overlayState.itemId }
        else -> null
    }
    val scannedQty: Double = scannedLine?.qty ?: 0.0
    val scannedIsLoose = scannedLine?.item?.isLoose == true
    val qtyStep = if (scannedIsLoose) 0.25 else 1.0

    fun formatQty(qty: Double): String {
        return if (scannedIsLoose) {
            val txt = String.format("%.3f", qty).trimEnd('0').trimEnd('.')
            "${txt} kg"
        } else {
            qty.toInt().toString()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        val isLandscape = maxWidth > maxHeight
        val bottomBarHeight = if (isLandscape) 64.dp else 84.dp
        val frameWidthFraction = if (isLandscape) 0.76f else 0.88f
        val frameHeight = (maxHeight - bottomBarHeight - 140.dp).coerceAtLeast(if (isLandscape) 180.dp else 240.dp)

        // Fullscreen camera (bill screen stays visible behind via BillingScreen blur)
        ScannerScreen(
            isContinuous = true,
            onBarcodeScanned = onBarcodeScanned,
            onClose = onDismiss,
            backgroundColor = Color.Transparent,
            showCloseButton = false,
            showViewfinder = false
        )

        // Big centered scan frame; status overlay must match this size (wireframe behavior)
        val frameModifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(frameWidthFraction)
            .height(frameHeight)
            .clip(RoundedCornerShape(28.dp))
            .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(28.dp))

        Box(modifier = frameModifier) {
            // Dim the camera slightly within the frame for readability
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))

            when (overlayState) {
                BillingScanOverlayState.Idle -> Unit
                is BillingScanOverlayState.Added -> {
                    FrameStatusOverlay(
                        isSuccess = true,
                        title = "Item Added!",
                        subtitle = overlayState.name,
                        qtyText = formatQty(scannedQty),
                        onInc = { onSetQty(overlayState.itemId, scannedQty + qtyStep) },
                        onDec = {
                            val next = (scannedQty - qtyStep).coerceAtLeast(0.0)
                            if (next > 0.0) onSetQty(overlayState.itemId, next)
                        }
                    )
                }
                is BillingScanOverlayState.NotFound -> {
                    FrameStatusOverlay(
                        isSuccess = false,
                        title = "Not Found",
                        subtitle = overlayState.barcode,
                        primaryActionText = "Add now to inventory",
                        onPrimaryAction = { onAddToInventory(overlayState.barcode) }
                    )
                }
            }
        }

        // "Bill ←" pill button: below scan frame, above bottom instruction bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarHeight + 10.dp),
            color = Blue600,
            shape = RoundedCornerShape(999.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Bill",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Bill",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Bottom instruction bar (matches your screenshot)
        Surface(
            color = BgPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomBarHeight)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Align barcode within the frame",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FrameStatusOverlay(
    isSuccess: Boolean,
    title: String,
    subtitle: String? = null,
    qtyText: String = "",
    onInc: (() -> Unit)? = null,
    onDec: (() -> Unit)? = null,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    val bg = if (isSuccess) Color(0xFF0E7A4B) else Color(0xFFD94841)
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = bg.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = bg, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)
            }

            if (isSuccess && onInc != null && onDec != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                        IconButton(onClick = onDec) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                    }
                    Text(qtyText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                        IconButton(onClick = onInc) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                }
            }

            if (!primaryActionText.isNullOrBlank() && onPrimaryAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(primaryActionText, color = bg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CartItemCard(
    itemName: String,
    rackLocation: String?,
    imageUri: String?,
    qty: Double,
    isLoose: Boolean,
    pricePerKg: Double,
    unitPrice: Double,
    subtotal: Double,
    selectedStepKg: Double,
    onSelectStepKg: (Double) -> Unit,
    onCustomWeight: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onDelete: () -> Unit,
    onEditPrice: () -> Unit
) {
    fun formatKgShort(kg: Double): String {
        if (kg <= 0.0) return "0kg"
        val txt = if (kg % 1.0 == 0.0) kg.toInt().toString()
        else String.format("%.3f", kg).trimEnd('0').trimEnd('.')
        return "${txt}kg"
    }

    fun formatQtyShort(): String = if (isLoose) formatKgShort(qty) else qty.toInt().toString()
    val lineTotalLabel = "₹${subtotal.toInt()}"

    KiranaCard(borderColor = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail (left)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Gray50),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = itemName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Gray300,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Middle column: name on top, qty adjustment below (portrait-friendly)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp)
            ) {
                // Top line: name (price + delete are on the same line, to the right)
                Text(
                    itemName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Qty stepper pill (below name) + delete icon beside it
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFFF1F3F6),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onDec, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = formatQtyShort(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .widthIn(min = if (isLoose) 52.dp else 22.dp)
                                    .clickable(enabled = isLoose) { if (isLoose) onCustomWeight() },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = TextPrimary
                            )
                            IconButton(onClick = onInc, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Gray400)
                    }
                }
            }

            // Right: price (delete moved next to qty control)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = lineTotalLabel,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.clickable(onClick = onEditPrice)
                )
            }
        }
    }
}

@Composable
fun CheckoutDialog(total: Double, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = White,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Confirm Payment", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text("₹${total.toInt()}", fontSize = 40.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KiranaButton(text = "Cash", onClick = { onConfirm("CASH") }, modifier = Modifier.weight(1f))
                    KiranaButton(text = "UPI", onClick = { onConfirm("UPI") }, modifier = Modifier.weight(1f))
                    KiranaButton(text = "Udhaar", onClick = { onConfirm("CREDIT") }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
