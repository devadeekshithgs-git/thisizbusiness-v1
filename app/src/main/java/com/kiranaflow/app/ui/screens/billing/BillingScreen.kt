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
import com.kiranaflow.app.ui.components.AddFab
import com.kiranaflow.app.ui.components.SolidTopBar
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
import kotlin.math.roundToInt

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
    var showBillingScanner by remember { mutableStateOf(false) }
    var scanOverlayState by remember { mutableStateOf<BillingScanOverlayState>(BillingScanOverlayState.Idle) }
    var priceEditItem by remember { mutableStateOf<BoxCartItem?>(null) }
    var showPriceEditDialog by remember { mutableStateOf(false) }
    var showTxnSavedOverlay by remember { mutableStateOf(false) }
    var screenMode by rememberSaveable { mutableStateOf("BILL") } // BILL | EXPENSE

    // Loose items (sold by weight): store selected step size (grams) per item in cart.
    val looseStepByItemId = remember { mutableStateMapOf<Int, Int>() }
    var customWeightForItemId by remember { mutableStateOf<Int?>(null) }
    var customWeightText by remember { mutableStateOf("") }

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
        viewModel.billSavedEvents.collectLatest {
            showTxnSavedOverlay = true
            runCatching {
                tts.language = Locale.getDefault()
                tts.speak("Transaction saved", TextToSpeech.QUEUE_FLUSH, null, "txn_saved")
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(contentFadeModifier)
        ) {
            SolidTopBar(
                title = if (screenMode == "BILL") "New Bill" else "Record Expense",
                subtitle = if (screenMode == "BILL") "Scan or search items" else "Track business spendings",
                onSettings = onOpenSettings,
                containerColor = tabCapsuleColor("bill")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Mode switch: Bill Customer vs Record Expense (A6)
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
                        containerColor = if (screenMode == "BILL") KiranaGreenBg else BgPrimary,
                        contentColor = if (screenMode == "BILL") KiranaGreen else TextPrimary
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
                        containerColor = if (screenMode == "EXPENSE") LossRed else LossRed.copy(alpha = 0.75f),
                        contentColor = Color.White
                    )
                ) {
                    Text("Record Expense", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (screenMode == "BILL") {
                // Use LazyColumn for the entire bill content to support landscape scrolling
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Search field as first item
                        item {
                            SearchField(
                                query = searchQuery,
                                onQueryChange = { viewModel.searchItems(it) },
                                placeholder = "Search items manually...",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Search Results Dropdown
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
                                                        // Navigate to full add product screen in inventory
                                                        val productName = searchQuery.trim()
                                                        viewModel.onSearchChange("") // Clear search
                                                        navController.navigate("inventory") { launchSingleTop = true; restoreState = true }
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("prefill_name", productName)
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("return_to", "bill")
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
                                                        .clickable {
                                                            viewModel.addItemToBill(item, 1)
                                                        }
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
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // Cart List or Empty State
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
                            items(billItems, key = { it.item.id }) { billItem ->
                                val isLoose = billItem.item.isLoose
                                if (isLoose) {
                                    LaunchedEffect(billItem.item.id) {
                                        if (looseStepByItemId[billItem.item.id] == null) {
                                            looseStepByItemId[billItem.item.id] = 250
                                        }
                                    }
                                }
                                val step = if (isLoose) (looseStepByItemId[billItem.item.id] ?: 250) else 1
                                val subtotal =
                                    if (isLoose) billItem.item.pricePerKg * (billItem.qty / 1000.0)
                                    else billItem.item.price * billItem.qty
                                CartItemCard(
                                    itemName = billItem.item.name,
                                    rackLocation = billItem.item.rackLocation,
                                    imageUri = billItem.item.imageUri,
                                    qty = billItem.qty,
                                    isLoose = isLoose,
                                    pricePerKg = billItem.item.pricePerKg,
                                    unitPrice = billItem.item.price,
                                    subtotal = subtotal,
                                    selectedStepGrams = step,
                                    onSelectStepGrams = { grams ->
                                        // Portion chips set BOTH the active +/- step and the quantity immediately.
                                        // This ensures the subtotal updates instantly (requested behavior).
                                        looseStepByItemId[billItem.item.id] = grams
                                        viewModel.updateItemQuantity(billItem.item.id, grams)
                                    },
                                    onCustomWeight = {
                                        customWeightForItemId = billItem.item.id
                                        val kg = billItem.qty / 1000.0
                                        customWeightText = String.format("%.3f", kg).trimEnd('0').trimEnd('.')
                                    },
                                    onInc = { viewModel.updateItemQuantity(billItem.item.id, billItem.qty + step) },
                                    onDec = {
                                        val newQty = billItem.qty - step
                                        if (newQty > 0) viewModel.updateItemQuantity(billItem.item.id, newQty)
                                        else viewModel.removeItemFromBill(billItem.item.id)
                                    },
                                    onDelete = { viewModel.removeItemFromBill(billItem.item.id) },
                                    onEditPrice = {
                                        priceEditItem = billItem
                                        showPriceEditDialog = true
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp).padding(horizontal = 16.dp))
                            }
                        }
                    }

                    BillingCheckoutBar(
                        enabled = billItems.isNotEmpty(),
                        onCheckout = { showCheckoutDialog = true },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // EXPENSE MODE (A6)
                val scope = rememberCoroutineScope()
                val selectedVendor = vendors.firstOrNull { it.id == selectedVendorId }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
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

        // Bottom-right overlay (above bottom menu bar):
        // - Add button on top
        // - Total amount directly below it
        if (screenMode == "BILL") {
            Column(
                modifier = contentFadeModifier
                    .then(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 112.dp)
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AddFab(
                    onClick = {
                        showBillingScanner = true
                        scanOverlayState = BillingScanOverlayState.Idle
                    },
                    containerColor = tabCapsuleColor("bill")
                )

                Surface(
                    color = BgPrimary,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            "TOTAL AMOUNT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "₹${totalAmount.toInt()}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }

    // Billing scanner overlay (camera on top of blurred bill screen)
    if (showBillingScanner) {
        BillingScannerOverlay(
            overlayState = scanOverlayState,
            billItems = billItems,
            onSetQty = { itemId, qty -> viewModel.updateItemQuantity(itemId, qty) },
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
        // Map bill items to the format expected by the modal for WhatsApp sharing
        val billItemDataList = remember(billItems) {
            billItems.map { cartItem ->
                val lineTotal = if (cartItem.item.isLoose) {
                    cartItem.item.pricePerKg * (cartItem.qty / 1000.0)
                } else {
                    cartItem.item.price * cartItem.qty
                }
                val unitPrice = if (cartItem.item.isLoose) cartItem.item.pricePerKg else cartItem.item.price
                com.kiranaflow.app.ui.components.dialogs.BillItemData(
                    name = cartItem.item.name,
                    qty = cartItem.qty,
                    isLoose = cartItem.item.isLoose,
                    unitPrice = unitPrice,
                    lineTotal = lineTotal
                )
            }
        }
        
        CompletePaymentModal(
            totalAmount = totalAmount,
            customers = customers,
            shopName = shopSettings.shopName,
            upiId = shopSettings.upiId,
            receiptTemplate = shopSettings.receiptTemplate,
            billItems = billItemDataList,
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
                        placeholder = "e.g. 1.25",
                        label = "WEIGHT (kg)",
                        keyboardType = KeyboardType.Decimal
                    )
                    Text("Example: 0.5 = 500g", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = customWeightForItemId ?: return@TextButton
                    val kg = customWeightText.toDoubleOrNull() ?: 0.0
                    val grams = (kg * 1000.0).roundToInt()
                    if (grams > 0) {
                        // Custom weight also becomes the active step going forward.
                        looseStepByItemId[id] = grams
                        viewModel.updateItemQuantity(id, grams)
                    }
                    customWeightForItemId = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { customWeightForItemId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BillingCheckoutBar(
    enabled: Boolean,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgPrimary)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) ProfitGreen else Gray300,
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checkout", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

private sealed interface BillingScanOverlayState {
    data object Idle : BillingScanOverlayState
    data class Added(val itemId: Int, val name: String) : BillingScanOverlayState
    data class NotFound(val barcode: String) : BillingScanOverlayState
}

@Composable
private fun BillingScannerOverlay(
    overlayState: BillingScanOverlayState,
    billItems: List<BoxCartItem>,
    onSetQty: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onAddToInventory: (String) -> Unit
) {
    val qtyForScanned = when (overlayState) {
        is BillingScanOverlayState.Added -> billItems.firstOrNull { it.item.id == overlayState.itemId }?.qty ?: 0
        else -> 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // Fullscreen camera (bill screen stays visible behind via BillingScreen blur)
        ScannerScreen(
            isContinuous = true,
            onBarcodeScanned = onBarcodeScanned,
            onClose = onDismiss,
            backgroundColor = Color.Transparent,
            showCloseButton = false,
            showViewfinder = false
        )

        // "Bill ←" pill button: below scan frame, above bottom instruction bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp + 10.dp),
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

        // Big centered scan frame; status overlay must match this size (wireframe behavior)
        val frameModifier = Modifier
            .fillMaxWidth(0.88f)
            .fillMaxHeight(0.62f)
            .align(Alignment.Center)
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
                        qty = qtyForScanned,
                        onInc = { onSetQty(overlayState.itemId, qtyForScanned + 1) },
                        onDec = {
                            if (qtyForScanned > 1) onSetQty(overlayState.itemId, qtyForScanned - 1)
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

        // Bottom instruction bar (matches your screenshot)
        Surface(
            color = BgPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
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
    qty: Int = 0,
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

            if (isSuccess && qty > 0 && onInc != null && onDec != null) {
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
                    Text(qty.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
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
    qty: Int,
    isLoose: Boolean,
    pricePerKg: Double,
    unitPrice: Double,
    subtotal: Double,
    selectedStepGrams: Int,
    onSelectStepGrams: (Int) -> Unit,
    onCustomWeight: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
    onDelete: () -> Unit,
    onEditPrice: () -> Unit
) {
    fun formatWeightShort(grams: Int): String {
        if (grams <= 0) return "0g"
        return if (grams >= 1000) {
            val kg = grams / 1000.0
            // Keep it short: 1kg, 1.25kg, etc.
            val txt = if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format("%.2f", kg).trimEnd('0').trimEnd('.')
            "${txt}kg"
        } else {
            "${grams}g"
        }
    }

    KiranaCard(borderColor = Color.Transparent) {
        // Two-row layout for responsiveness:
        // Row 1: main content + qty/subtotal
        // Row 2 (loose only): portion chips on a full-width line to avoid overlap on small screens.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Delete (left) – prominent and easy to hit
            Surface(
                color = LossRedBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = LossRed, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(BgCard),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = itemName,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Name + unit/loc
            Column(modifier = Modifier.weight(1f)) {
                Text(itemName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val unitLabel = if (isLoose) "₹${pricePerKg.toInt()}/kg" else "₹${unitPrice.toInt()}"
                    Text(
                        unitLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        modifier = Modifier.clickable { onEditPrice() }
                    )
                    if (!rackLocation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val locText = rackLocation.trim().removePrefix("Rack").trim().removePrefix(":").trim()
                        Surface(
                            color = Gray100,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "LOC $locText",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Qty + subtotal (right)
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .widthIn(min = 96.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = Color(0xFFF1F3F6),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDec, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Remove, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            if (isLoose) formatWeightShort(qty) else qty.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.width(if (isLoose) 56.dp else 28.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick = onInc, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "₹${subtotal.toInt()}",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }

        if (isLoose) {
            Spacer(modifier = Modifier.height(12.dp))
            val leftGutter = 40.dp + 8.dp + 48.dp + 12.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = leftGutter)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(250, 500, 1000, 2000)
                options.forEach { g ->
                    FilterChip(
                        selected = selectedStepGrams == g,
                        onClick = { onSelectStepGrams(g) },
                        label = { Text(formatWeightShort(g), fontWeight = FontWeight.Bold) }
                    )
                }
                AssistChip(
                    onClick = onCustomWeight,
                    label = { Text("Custom", fontWeight = FontWeight.Bold) }
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
