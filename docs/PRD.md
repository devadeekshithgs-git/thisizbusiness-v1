# thisizbusiness - Product Requirements Document (PRD)

## Complete Specification for Android Kirana Store Management App

---

## 1. Executive Summary

**thisizbusiness** is a native Android application designed for Indian Kirana (grocery/general) store owners to manage their day-to-day business operations including billing, inventory tracking, customer/vendor management, expense recording, and business analytics - all with an offline-first architecture.

### 1.1 Key Value Proposition
- **Offline-First**: Works without internet, syncs when connected
- **Fast Billing**: Barcode scanner + manual search for rapid checkout
- **Complete P&L View**: Real-time profit/loss tracking with visual charts
- **Udhaar (Credit) Management**: Track receivables from customers and payables to vendors
- **Simple & Beautiful**: Modern Material 3 design optimized for Indian shopkeepers

### 1.2 Target User
- Small to medium Kirana/grocery store owners in India
- Age: 25-55
- Tech comfort: Basic smartphone users
- Language: English (Hindi localization ready)

---

## 2. Technical Architecture

### 2.1 Platform & Stack
```
Platform: Native Android (Kotlin)
Min SDK: 26 (Android 8.0+)
Target SDK: 34 (Android 14)
UI Framework: Jetpack Compose with Material 3
Database: Room (SQLite) - Offline-first
Navigation: Jetpack Navigation Compose
State Management: ViewModel + StateFlow/Flow
DI: Manual (no Hilt for simplicity)
Image Loading: Coil
Barcode Scanning: Google ML Kit
QR Generation: ZXing
Network: OkHttp (for sync)
Preferences: DataStore
```

### 2.2 Package Structure
```
com.thisizbusiness.app/
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── Entities.kt          # Room entities
│   │   ├── Daos.kt              # Data Access Objects
│   │   ├── AppDatabase.kt    # Room database
│   │   ├── AppPrefsStore.kt     # App preferences (DataStore)
│   │   ├── ShopSettingsStore.kt # Shop settings (DataStore)
│   │   └── EntityTypes.kt       # Type aliases
│   ├── remote/
│   │   └── OpenFoodFactsClient.kt # Product lookup API
│   └── repository/
│       ├── AppRepository.kt  # Main repository
│       └── Other repositories...
├── sync/
│   ├── OutboxEntity.kt          # Sync outbox queue
│   ├── PendingSyncQueue.kt      # Queue management
│   ├── SyncEnvelope.kt          # API envelope format
│   ├── RemoteApi.kt             # Sync API interface
│   └── HttpRemoteApi.kt         # HTTP implementation
├── ui/
│   ├── components/              # Reusable UI components
│   ├── screens/                 # Feature screens
│   └── theme/                   # Colors, Typography, Theme
└── util/
    ├── ConnectivityMonitor.kt   # Network state
    ├── SyncEngine.kt            # Sync orchestration
    └── QrCodeUtil.kt            # UPI QR generation
```

---

## 3. Data Models

### 3.1 Core Entities

#### ItemEntity (Inventory Item)
```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val price: Double,                    // Selling price
    val stock: Int,
    val category: String,
    val rackLocation: String?,            // Physical location (e.g., "Rack A1")
    val marginPercentage: Double,         // Auto-calculated from cost/sell
    val barcode: String?,
    val costPrice: Double,
    val gstPercentage: Double?,
    val reorderPoint: Int,                // Low stock alert threshold
    val vendorId: Int?,                   // Linked vendor
    val imageUri: String? = null,         // Product photo (local URI)
    val expiryDateMillis: Long? = null,   // Optional expiry date
    var isDeleted: Boolean = false        // Soft delete flag
)
```

#### PartyEntity (Customer/Vendor)
```kotlin
@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val type: String,                     // "CUSTOMER" | "VENDOR"
    val gstNumber: String? = null,
    val balance: Double = 0.0             // +ve = receivable, -ve = payable
)
```

#### TransactionEntity
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String,                     // "SALE" | "EXPENSE" | "INCOME"
    val amount: Double,
    val date: Long,                       // Timestamp
    val time: String,                     // Formatted time for display
    val customerId: Int?,
    val vendorId: Int?,
    val paymentMode: String,              // "CASH" | "UPI" | "CREDIT"
    val receiptImageUri: String? = null   // Expense receipt photo
)
```

#### TransactionItemEntity (Line Items)
```kotlin
@Entity(
    tableName = "transaction_items",
    foreignKeys = [...]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionId: Int,
    val itemId: Int?,
    val itemNameSnapshot: String,         // Name at time of sale
    val qty: Int,
    val price: Double                     // Price at time of sale
)
```

#### ReminderEntity
```kotlin
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val type: String,                     // "ITEM" | "VENDOR" | "GENERAL"
    val refId: Int?,                      // Reference ID
    val dueAt: Long,
    val note: String? = null,
    val isDone: Boolean = false
)
```

#### OutboxEntity (Sync Queue)
```kotlin
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val opId: String = UUID.randomUUID().toString(),
    val entityType: String,               // ITEM | PARTY | TRANSACTION | etc.
    val entityId: String?,
    val op: String,                       // UPSERT | DELETE
    val payloadJson: String?,
    val createdAtMillis: Long,
    val lastAttemptAtMillis: Long? = null,
    val status: String = "PENDING",       // PENDING | DONE | FAILED
    val error: String? = null
)
```

---

## 4. Feature Specifications

### 4.1 Bottom Navigation Structure

The app uses a 5-tab floating bottom navigation with a distinctive "fluid notch" design:

| Tab | Route | Icon | Accent Color | Description |
|-----|-------|------|--------------|-------------|
| Home | `home` | Home | Blue (#2563EB) | Dashboard & P&L |
| Customers | `customers` | People | Purple (#9333EA) | Customer management |
| Bill | `bill` | Receipt | Green (#2E7D32) | Billing & Expenses |
| Items | `inventory` | Inventory2 | Orange (#EA580C) | Inventory management |
| Expenses | `vendors` | Payments | Cyan (#00BCD4) | Vendor & expense tracking |

**Navigation Behavior:**
- Single tap: Navigate to tab
- Long press: Navigate + trigger quick action (add item/customer, open scanner)
- Selected tab shows floating accent-colored circle in notch

---

### 4.2 Home/Dashboard Screen (`home`)

#### Layout Structure
```
┌─────────────────────────────────────┐
│ Good Morning,                       │
│ Owner Name              [Settings]  │
├─────────────────────────────────────┤
│ Profit & Loss                       │
│ ┌───────────┐   ┌───────────────┐   │
│ │ Revenue   │ − │ Expense       │   │
│ │ ₹24,555   │   │ ₹12,340       │   │
│ └───────────┘   └───────────────┘   │
│ ┌───────────────────────────────┐   │
│ │ Profit/Loss: ₹12,215          │   │
│ └───────────────────────────────┘   │
├─────────────────────────────────────┤
│ Reminders                    [Add]  │
│ • Payment due tomorrow              │
│ • Restock rice                      │
├─────────────────────────────────────┤
│ Expiring Soon                       │
│ • Milk - Expires 22 Dec             │
├─────────────────────────────────────┤
│ Sales Trend        [Today ▼]        │
│ ┌───────────────────────────────┐   │
│ │ [Interactive Line Chart]      │   │
│ │ +5.2% compared to yesterday   │   │
│ └───────────────────────────────┘   │
├─────────────────────────────────────┤
│ Recent Transactions    [View All]   │
│ • Sale - 3 items (CASH) +₹450       │
│ • Expense - Rent (UPI) -₹5000       │
└─────────────────────────────────────┘
```

#### Features
1. **Greeting Header**: Dynamic greeting based on time (Good Morning/Afternoon/Evening)
2. **P&L Cards**: Revenue, Expense, Net Profit/Loss with color coding
3. **Reminders Section**: Custom reminders with checkbox to mark done
4. **Expiring Soon**: Items with expiry dates within 7 days
5. **Sales Trend Chart**: Interactive bezier curve chart with:
   - Touch/drag to select data point
   - Tooltip showing date + value
   - Gradient fill under line
   - Period comparison (% change)
6. **Time Range Selector**: Today | 7D | 1M | 3M | 6M | 1Y | Custom
7. **Recent Transactions**: Last 5 transactions with type indicators

---

### 4.3 Billing Screen (`bill`)

#### Mode Toggle
The billing screen has two modes:
1. **Bill Customer** (default) - Create sales
2. **Record Expense** - Log business expenses

#### Bill Customer Mode

```
┌─────────────────────────────────────┐
│ New Bill           [Scanner Icon]   │
│ Scan or search items    [Settings]  │
├─────────────────────────────────────┤
│ [Bill Customer] [Record Expense]    │
├─────────────────────────────────────┤
│ 🔍 Search items manually...         │
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ [Delete] 📦 Toor Dal          │   │
│ │          ₹150  LOC A1         │   │
│ │               [-] 2 [+]  ₹300 │   │
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │ [Delete] 📦 Fortune Oil 1L    │   │
│ │          ₹155  LOC B2         │   │
│ │               [-] 1 [+]  ₹155 │   │
│ └───────────────────────────────┘   │
├─────────────────────────────────────┤
│ TOTAL AMOUNT              ₹455      │
│ [✓ Checkout]                        │
└─────────────────────────────────────┘
```

#### Scanner Overlay
When scanner is activated:
- Full-screen camera preview with blur behind
- Rounded viewfinder frame
- On scan success: Green overlay with "Item Added!" + qty stepper
- On scan failure: Red overlay with "Not Found" + "Add now to inventory" button
- Haptic feedback (vibration) + beep sound on successful scan
- "Bill ←" button to return to cart

#### Cart Item Features
- Product image/placeholder
- Name + unit price (tappable for price edit)
- Rack location badge
- Quantity stepper (+/-)
- Delete button (red)
- Line subtotal

#### Price Edit Dialog
- Edit unit price for this bill only (discount)
- Or permanently update inventory price
- Toggle: "Discount (this bill)" | "Permanent"

#### Checkout Flow
1. Tap "Checkout" → Opens Complete Payment Modal
2. Shows total amount prominently
3. Payment mode selection: Cash | UPI | Credit (Udhaar)
4. If UPI selected and UPI ID configured: Shows dynamic QR code
5. Optional customer linking (dropdown + add new)
6. "Mark Paid & Close" → Saves transaction, updates stock, shows confirmation

#### Transaction Saved Confirmation
- Brief toast overlay: "Transaction saved"
- TTS voice: "Transaction saved"
- Auto-dismiss after 900ms

---

#### Record Expense Mode

```
┌─────────────────────────────────────┐
│ Record Expense                      │
│ Track business spendings            │
├─────────────────────────────────────┤
│ Amount (₹)                          │
│ [_________________]                 │
├─────────────────────────────────────┤
│ Receipt photo (optional)            │
│ [Camera preview / placeholder]      │
│ [Take photo] [Remove]               │
├─────────────────────────────────────┤
│ PAY TO (VENDOR)                     │
│ [Dropdown: No Vendor ▼]             │
│ + Add new vendor                    │
├─────────────────────────────────────┤
│ CATEGORY                            │
│ [Dropdown: Supplies ▼]              │
│ + Add new category                  │
├─────────────────────────────────────┤
│ Description                         │
│ [_________________]                 │
├─────────────────────────────────────┤
│ PAYMENT MODE                        │
│ [Cash] [UPI] [Udhaar]               │
├─────────────────────────────────────┤
│ [Save Expense]                      │
└─────────────────────────────────────┘
```

#### Expense Categories (Default)
- Supplies, Rent, Salary, Utilities, Transport, Misc
- Custom categories auto-saved from previous expenses

---

### 4.4 Inventory Screen (`inventory`)

```
┌─────────────────────────────────────┐
│ Manage Inventory        [+ Add]     │
│ Track stock & prices    [Settings]  │
├─────────────────────────────────────┤
│ 🔍 Search items...                  │
├─────────────────────────────────────┤
│ [Select]                            │
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ 📦 Toor Dal Premium      ₹150 │   │
│ │ Staples • Rack A1             │   │
│ │ [45 in Stock] [25% Margin]    │   │
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │ 📦 Fortune Sun Oil 1L    ₹155 │   │
│ │ Oil • Rack B2                 │   │
│ │ [5 in Stock] [15% Margin]     │   │
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

#### Item Card Features
- Product photo or placeholder icon
- Name + selling price
- Category + rack location
- Stock badge (green if ok, red if below reorder point)
- Margin percentage badge
- Tap to edit

#### Selection Mode
- Long-press to enter selection mode
- Checkboxes appear
- Bulk delete with confirmation

#### Add/Edit Item Dialog (Full-screen)

```
┌─────────────────────────────────────┐
│ Add New Product / Edit Product  [X] │
├─────────────────────────────────────┤
│         [Product Photo]             │
│    [Camera]      [Gallery]          │
├─────────────────────────────────────┤
│ ITEM NAME                           │
│ [e.g. Basmati Rice 5kg]             │
├─────────────────────────────────────┤
│ CATEGORY                            │
│ [Dropdown + search + add new]       │
├─────────────────────────────────────┤
│ VENDOR (OPTIONAL)                   │
│ [Dropdown + search + add new]       │
├─────────────────────────────────────┤
│ BARCODE (OPTIONAL)                  │
│ [Tap to scan ----------------]      │
│ (Auto-lookup from OpenFoodFacts)    │
├─────────────────────────────────────┤
│ Cost Price ₹    Selling Price ₹     │
│ [____]          [____]              │
├─────────────────────────────────────┤
│ Stock           GST % (Optional)    │
│ [____]          [____]              │
├─────────────────────────────────────┤
│ Location        Reorder Point       │
│ [Rack A1]       [10]                │
├─────────────────────────────────────┤
│ EXPIRY (OPTIONAL)                   │
│ [Select date...] [Clear]            │
├─────────────────────────────────────┤
│ [Delete]        [Save Product]      │
└─────────────────────────────────────┘
```

#### Barcode Scanner Integration
1. Tap barcode field → Opens scanner screen
2. Single-scan mode (not continuous)
3. On scan: Look up in local inventory
4. If found: Pre-fill edit form
5. If not found: Query OpenFoodFacts API for product info
6. Pre-fill name + category from API response

---

### 4.5 Customers Screen (`customers`)

```
┌─────────────────────────────────────┐
│ Customers              [+] [⚙️]     │
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ TOTAL RECEIVABLES             │   │
│ │ ₹12,500                       │   │
│ │ Money pending from market     │   │
│ └───────────────────────────────┘   │
├─────────────────────────────────────┤
│ 🔍 Search customers...              │
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ 🔴 [S] Sharma Ji      DUE     │   │
│ │     📞 9876543210     ₹2,500  │   │
│ │     [Remind]    SALES: ₹15K   │   │
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

#### Customer Card
- Avatar with initial
- Red left border if has outstanding dues
- Name + phone
- Due amount (receivable)
- Total sales metric
- Remind button (opens messaging)

#### Customer Detail Sheet (Bottom Sheet)
- Full customer details
- Transaction history
- Record payment form:
  - Amount input
  - Payment mode (Cash/UPI/Credit)
  - "Record Payment" button
- Updates customer balance on payment

---

### 4.6 Vendors/Expenses Screen (`vendors`)

```
┌─────────────────────────────────────┐
│ Vendors & Expenses      [+] [⚙️]    │
├─────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────────┐ │
│ │ PAYABLES    │ │ ITEMS TO REORDER│ │
│ │ ₹12,000     │ │ 5               │ │
│ │ [View All →]│ │ [View All →]    │ │
│ └─────────────┘ └─────────────────┘ │
├─────────────────────────────────────┤
│ 🔍 Search vendors...                │
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ 🚚 Hindustan Unilever         │   │
│ │ 📞 1800111111                 │   │
│ │ PURCHASES: ₹50K   DUE: ₹12K   │   │
│ │ [Pay Now] [Edit] [Delete]     │   │
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

#### KPI Cards
1. **Total Payables**: Sum of negative vendor balances → Links to Payables detail
2. **Items to Reorder**: Count of items below reorder point → Links to Reorder list

#### Vendor Detail Screen
- Vendor info (name, phone, GST)
- Balance status
- Transaction history
- Record purchase form:
  - Amount
  - Payment mode
  - Note (optional)
- Pay vendor action

#### Items to Reorder Screen
- List of items where stock < reorderPoint
- Each shows: Name, current stock, reorder point, vendor
- Quick reorder action

---

### 4.7 Transactions Screen (`transactions`)

Accessed via Home → "View All" on Recent Transactions

```
┌─────────────────────────────────────┐
│ ← All Transactions     [Select][X]  │
├─────────────────────────────────────┤
│ 🔍 Search by customer/vendor...     │
│ 45 results                          │
├─────────────────────────────────────┤
│ FILTERS                    [Show ▼] │
│ [All] [Customers] [Vendors]         │
│ [Any] [Cash] [UPI] [Udhaar]         │
│ [Today][7D][1M][3M][6M][1Y][Custom] │
│ 🔍 Filter by product bought         │
│ Any date        [Pick] [Clear]      │
│ [Clear all filters]                 │
├─────────────────────────────────────┤
│ ┌───────────────────────────────┐   │
│ │ 📈 Sale - 3 items (CASH)      │   │
│ │ [SALE] [Sharma Ji] [CASH]     │   │
│ │ Toor Dal×2, Rice×1    +₹450   │   │
│ └───────────────────────────────┘   │
│ ┌───────────────────────────────┐   │
│ │ 📉 Expense • Rent (UPI)       │   │
│ │ [EXPENSE] [UPI]       -₹5000  │   │
│ └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

#### Filter Options
- Party type: All | Customers | Vendors
- Payment mode: Any | Cash | UPI | Credit
- Date range: Quick presets + custom picker
- Product filter: Search items in transaction

#### Transaction Card
- Type icon (sale=green up, expense=red down, income=blue up)
- Title
- Type + Party + Payment mode pills
- Line items summary
- Amount with sign

#### Selection Mode
- Long-press to enter
- Bulk delete with confirmation

#### Transaction Detail Screen
- Full transaction info
- All line items
- Party details (if linked)
- Receipt image (if expense with photo)

---

### 4.8 Settings Drawer

Slides in from right edge.

```
┌─────────────────────────────────────┐
│ Settings                        [X] │
├─────────────────────────────────────┤
│ ▼ Shop Settings                     │
│   SHOP NAME                         │
│   [Bhanu Super Mart]                │
│   OWNER NAME                        │
│   [Owner Ji]                        │
│   @ UPI ID (FOR QR CODE)            │
│   Required for dynamic QR           │
│   [9876543210@upi]                  │
│   [✓ Save Settings]                 │
├─────────────────────────────────────┤
│ ▼ Demo & Testing                    │
│   Enable demo data [Toggle]         │
│   (Loads 1+ year synthetic data)    │
│   [Reset on next restart]           │
│   ─────────────────────────         │
│   Cloud Sync                        │
│   Pending changes: 5                │
│   Simulate sync success [Toggle]    │
│   Use real backend [Toggle]         │
│   [Sync now]                        │
│   Last sync: 20 Dec 24, 10:30 AM    │
│   [View Outbox (5 pending)]         │
└─────────────────────────────────────┘
```

#### Shop Settings
- Shop name: Used in receipts/QR
- Owner name: Used in greeting
- UPI ID: Required for dynamic QR generation

#### Demo & Testing (Development)
- Demo mode toggle: Seeds 380+ days of synthetic transactions
- Database reset request
- Sync controls:
  - Simulate sync (mark outbox as done without server)
  - Use real backend toggle
  - Manual sync trigger
  - Outbox viewer with retry/reset controls

---

## 5. Design System

### 5.1 Color Palette

#### Profit/Success Colors
```kotlin
val ProfitGreen = Color(0xFF2E7D32)      // Primary green
val ProfitGreenBg = Color(0xFFE0F7F4)    // Light green background
```

#### Loss/Error Colors
```kotlin
val LossRed = Color(0xFFB71C1C)          // Primary red
val LossRedBg = Color(0xFFFFEBEE)        // Light red background
val LowStockRed = Color(0xFFFFE0E6)      // Low stock indicator
```

#### Interactive Colors
```kotlin
val InteractiveCyan = Color(0xFF00BCD4)  // Cyan/teal accent
val Blue600 = Color(0xFF2563EB)          // Blue accent
val Purple600 = Color(0xFF9333EA)        // Purple accent
val AlertOrange = Color(0xFFEA580C)      // Warning orange
```

#### Backgrounds
```kotlin
val BgPrimary = Color(0xFFFFFFFF)        // White
val BgCard = Color(0xFFF7F7F7)           // Light gray
```

#### Text Colors
```kotlin
val TextPrimary = Color(0xFF1A1A1A)      // Near black
val TextSecondary = Color(0xFF808080)    // Medium gray
```

#### Chart Colors
```kotlin
val ChartUp = Color(0xFF10B981)          // Green trend line
val ChartDown = Color(0xFFEF4444)        // Red trend line
```

### 5.2 Typography

```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

**Text Styles by Usage:**
- Headlines: 24-32sp, FontWeight.Black
- Titles: 18-20sp, FontWeight.Bold
- Body: 14-16sp, FontWeight.Normal/Medium
- Labels: 10-12sp, FontWeight.Bold, ALL CAPS
- Captions: 11-12sp, FontWeight.Medium

### 5.3 Spacing & Sizing

- Screen padding: 16dp horizontal
- Card padding: 16-24dp
- Card corner radius: 12-24dp
- Button height: 52-56dp
- Button corner radius: 14-16dp
- Icon sizes: 16dp (small), 20-24dp (medium), 32-48dp (large)
- Avatar size: 40-50dp
- Input height: 48-52dp

### 5.4 Component Library

#### AppCard
```kotlin
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = White,
    borderColor: Color = Gray100,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```
- Shadow: 2dp elevation
- Corner radius: 24dp
- Padding: 20dp

#### AppButton
```kotlin
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    colors: ButtonColors = ...
)
```
- Height: 56dp
- Corner radius: 16dp
- Font: Bold

#### AppInput
```kotlin
@Composable
fun AppInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    keyboardType: KeyboardType = Text
)
```
- Height: 52dp
- Corner radius: 16dp
- Label: ALL CAPS, 10sp

#### SearchField
- Height: 48dp
- Corner radius: 12dp
- Leading search icon
- Background: BgCard

#### CircleButton
- Size: 40dp
- Used in headers for actions

#### ValleyTopBar
Custom top bar with:
- Title + subtitle
- Action button (floating, colored)
- Settings icon button

---

## 6. Navigation Flow

### 6.1 Navigation Graph

```
NavHost(startDestination = "bill") {
    composable("home") { HomeScreen() }
    composable("customers") { PartiesScreen(type="CUSTOMER") }
    composable("bill") { BillingScreen() }
    composable("inventory") { InventoryScreen() }
    composable("vendors") { VendorsScreen() }
    
    // Transaction routes
    composable("transactions") { TransactionsScreen() }
    composable("transaction/{txId}") { TransactionDetailScreen() }
    
    // Vendor sub-routes
    composable("vendors/payables") { TotalPayablesScreen() }
    composable("vendors/reorder") { ItemsToReorderScreen() }
    composable("vendors/detail/{vendorId}") { VendorDetailScreen() }
    
    // Scanner routes
    composable("scanner/inventory") { ScannerScreen(isContinuous=false) }
    composable("scanner/billing") { ScannerScreen(isContinuous=true) }
}
```

### 6.2 State Passing

Uses `savedStateHandle` for passing data between screens:
- Barcode from scanner → Inventory/Billing
- Return route indicator for back navigation

---

## 7. Offline-First Architecture

### 7.1 Local-First Principle
- All data stored in Room database
- UI reads from local DB via Flow
- Writes go to local DB immediately
- Background sync to cloud when online

### 7.2 Sync Outbox Pattern

```
User Action → Repository → Local DB Write → Outbox Entry → Background Sync
```

#### Outbox Entry Structure
```kotlin
data class OutboxEntity(
    val opId: String,           // Idempotency key
    val entityType: String,     // What entity was changed
    val entityId: String?,      // Which entity
    val op: String,             // What operation
    val payloadJson: String?,   // Data payload
    val status: String,         // PENDING | DONE | FAILED
    val error: String?          // Error message if failed
)
```

#### Sync Operation Types
- UPSERT / DELETE (generic)
- UPSERT_CUSTOMER / UPSERT_VENDOR
- CREATE_SALE / CREATE_EXPENSE / CREATE_PAYMENT
- CREATE_VENDOR_PURCHASE
- UPSERT_MANY (for transaction items)
- MARK_DONE (for reminders)

### 7.3 Connectivity Monitoring

```kotlin
class ConnectivityMonitor(context: Context) {
    val isOnline: StateFlow<Boolean>
    // Uses ConnectivityManager + NetworkCallback
}
```

- Shows offline banner when disconnected
- Shows sync status chip (pending count, errors)

### 7.4 Sync Engine

```kotlin
class StubSyncEngine(db: AppDatabase, prefsStore: AppPrefsStore) {
    suspend fun syncOnce(): SyncResult
    suspend fun syncPendingOnly(simulateSuccess: Boolean): SyncResult
    suspend fun syncFailedOnly(simulateSuccess: Boolean): SyncResult
    suspend fun retryEntry(entryId: Int, simulateSuccess: Boolean): SyncResult
    suspend fun resetFailedToPending()
}
```

#### Sync Modes
1. **Simulate Success**: Mark entries as DONE without server
2. **Real Backend**: POST to configured backend URL
3. **Manual Control**: Retry, reset, clear from Settings

---

## 8. External Integrations

### 8.1 OpenFoodFacts API
- Used for product lookup by barcode
- Returns product name, category, brand
- Fallback: Manual entry if API fails

### 8.2 UPI QR Generation
- Uses ZXing library
- Generates UPI deep link with amount
- Format: `upi://pay?pa={upiId}&pn={shopName}&am={amount}`

### 8.3 Backend API (Optional)

Configured via gradle.properties:
```properties
THISIZBUSINESS_BACKEND_BASE_URL=https://your-backend.com
THISIZBUSINESS_BACKEND_API_KEY=your-api-key
```

Sync envelope format:
```json
{
  "ops": [
    {
      "opId": "uuid",
      "entityType": "ITEM",
      "entityId": "1",
      "op": "UPSERT",
      "payload": { ... }
    }
  ]
}
```

---

## 9. Device Features

### 9.1 Camera
- Barcode scanning (ML Kit)
- Product photos (inventory)
- Receipt photos (expenses)

### 9.2 Haptic Feedback
- Vibration on successful scan
- Short pulse (100-120ms)

### 9.3 Audio Feedback
- Beep sound on successful scan
- Uses SoundPool for low-latency playback
- Fallback to MediaPlayer

### 9.4 Text-to-Speech
- "Transaction saved" voice confirmation
- Uses Android TTS engine

---

## 10. Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

FileProvider for camera image sharing:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

---

## 11. Data Seeding (Demo Mode)

When demo mode is enabled:
- Generates 380+ days of synthetic data
- Creates sample vendors (Hindustan Unilever, ITC Limited)
- Creates sample customers (Sharma Ji)
- Creates sample inventory items
- Generates realistic transaction patterns:
  - 0-6 sales per day (more on weekends)
  - 0-2 expenses per day
  - Weekly bulk purchase
  - Mix of payment modes
  - Balance updates for credit transactions

---

## 12. Key User Flows

### 12.1 Quick Sale Flow
1. Open app (lands on Bill screen)
2. Tap scanner icon → Camera opens
3. Scan item barcodes (continuous mode)
4. Adjust quantities if needed
5. Tap "Checkout"
6. Select payment mode (Cash/UPI/Credit)
7. Link customer (optional)
8. Tap "Mark Paid & Close"
9. Transaction saved, stock updated

### 12.2 Add New Item Flow
1. Go to Inventory tab
2. Tap + button (or long-press tab)
3. Fill item details
4. Optionally scan barcode for auto-lookup
5. Take/select product photo
6. Set cost/sell prices (margin auto-calculated)
7. Save

### 12.3 Record Credit Sale Flow
1. Complete billing as normal
2. At checkout, select "Credit" (Udhaar)
3. Link or create customer (required for credit)
4. Complete transaction
5. Customer balance updated (positive = receivable)

### 12.4 Collect Payment Flow
1. Go to Customers tab
2. Tap customer with dues
3. In detail sheet, enter payment amount
4. Select payment mode
5. Tap "Record Payment"
6. Customer balance reduced, INCOME transaction created

---

## 13. Error Handling

### 13.1 Form Validation
- Required fields: Name, phone (for parties), amount (for expenses)
- Numeric validation for prices, stock
- Phone number normalization (last 10 digits)

### 13.2 Duplicate Prevention
- Customers de-duped by phone number
- Items soft-deleted to preserve transaction history

### 13.3 Camera Errors
- Permission denial: Show request button
- Camera bind failure: Display error message
- ML Kit failure: Log and continue

### 13.4 Sync Errors
- Network failures: Mark entry as FAILED
- Store error message
- Allow manual retry from Settings

---

## 14. Performance Considerations

### 14.1 Database
- Indices on foreign keys
- Flow-based reactive queries
- Soft delete for items (preserve history)

### 14.2 Images
- Coil for efficient loading
- Local URI storage (not base64)
- Placeholder icons for missing images

### 14.3 Charts
- Canvas-based rendering
- Lazy calculation of data points
- Smooth bezier curves

### 14.4 Lists
- LazyColumn for all lists
- Key-based recomposition
- Content padding for bottom nav

---

## 15. Future Enhancements (Not Implemented)

1. **Multi-language support** (Hindi, regional languages)
2. **Bill printing** (Bluetooth thermal printers)
3. **WhatsApp reminders** for credit dues
4. **GST reports** and invoicing
5. **Multi-device sync** with cloud account
6. **Backup/restore** to Google Drive
7. **Dark mode** support
8. **Voice commands** for hands-free billing
9. **AI-powered inventory predictions**
10. **Barcode label printing**

---

## 16. Build & Configuration

### 16.1 Build.gradle.kts (App)
```kotlin
android {
    namespace = "com.thisizbusiness.app"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.thisizbusiness.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        // Backend configuration (optional)
        buildConfigField("String", "BACKEND_BASE_URL", "\"${property}\"")
        buildConfigField("String", "BACKEND_API_KEY", "\"${property}\"")
    }
}
```

### 16.2 Key Dependencies
```kotlin
// Compose
implementation(platform("androidx.compose:compose-bom:2023.08.00"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// CameraX + ML Kit
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("com.google.mlkit:barcode-scanning:17.2.0")

// Image loading
implementation("io.coil-kt:coil-compose:2.5.0")

// QR generation
implementation("com.google.zxing:core:3.5.3")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.1.1")

// Networking
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

---

## 17. Asset Requirements

### 17.1 Audio
- `res/raw/beep.mp3` - Scanner success sound

### 17.2 File Paths (for FileProvider)
```xml
<!-- res/xml/file_paths.xml -->
<paths>
    <files-path name="receipts" path="receipts/" />
    <cache-path name="cache" path="." />
</paths>
```

---

## 18. Testing Checklist

### 18.1 Core Flows
- [ ] Add item to inventory
- [ ] Scan barcode and add to cart
- [ ] Complete cash/UPI/credit sale
- [ ] Add customer/vendor
- [ ] Record expense with receipt photo
- [ ] Collect payment from customer
- [ ] View transaction history
- [ ] Filter transactions

### 18.2 Edge Cases
- [ ] Empty cart checkout (disabled)
- [ ] Duplicate phone number handling
- [ ] Camera permission denial
- [ ] Offline mode operation
- [ ] App restart with pending sync
- [ ] Large transaction lists performance

### 18.3 Device Testing
- [ ] Android 8.0 (minSdk)
- [ ] Android 14 (targetSdk)
- [ ] Various screen sizes
- [ ] RTL layout (if applicable)

---

## 19. Glossary

| Term | Description |
|------|-------------|
| Kirana | Indian term for small grocery/general store |
| Udhaar | Credit/loan - buying on credit to pay later |
| Receivables | Money owed TO the shop (from customers) |
| Payables | Money owed BY the shop (to vendors) |
| P&L | Profit and Loss |
| Reorder Point | Stock level below which to restock |
| GST | Goods and Services Tax (India) |
| UPI | Unified Payments Interface (India's digital payment system) |

---

*Document Version: 1.0*
*Last Updated: December 2024*
*Generated from thisizbusiness Android Codebase*
