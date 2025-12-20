package com.kiranaflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.data.local.CustomerEntity
import com.kiranaflow.app.data.local.ItemEntity
import com.kiranaflow.app.data.local.TransactionEntity
import com.kiranaflow.app.data.local.VendorEntity
import com.kiranaflow.app.data.local.PartyEntity
import com.kiranaflow.app.ui.theme.*

@Composable
fun KiranaCard(
    modifier: Modifier = Modifier,
    color: Color = White,
    borderColor: Color = Gray100,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp, // Subtle shadow like shadow-sm/xl depending on context, keeping low for now
                shape = RoundedCornerShape(24.dp),
                spotColor = Color(0x05000000) 
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp), // rounded-[1.5rem]
        colors = CardDefaults.cardColors(containerColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun KiranaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = KiranaGreen, contentColor = White)
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(56.dp) // py-5 equivalent ish
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), // rounded-2xl
        colors = colors,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
            )
        }
    }
}

@Composable
fun KiranaInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    /**
     * Optional UI-level filter for restricting input (e.g. digits-only, decimals).
     * If provided, the filtered value is what gets propagated to [onValueChange].
     */
    inputFilter: ((String) -> String)? = null
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray400,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(White)
                .border(1.dp, Gray200, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Gray400, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val next = inputFilter?.invoke(raw) ?: raw
                    onValueChange(next)
                },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Gray900
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = Gray400, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp), // px-6 mb-6
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = 30.sp, 
                fontWeight = FontWeight.Black,
                color = Gray900
            ) // text-3xl font-black
        )
        if (action != null) {
            action()
        }
    }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color = Gray900,
    contentColor: Color = White
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .shadow(4.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    trendUp: Boolean = true,
    modifier: Modifier = Modifier
) {
    KiranaCard(modifier = modifier, onClick = {}) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = title.uppercase(),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gray400,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = value,
                    style = TextStyle(
                        fontSize = 20.sp, // text-xl
                        fontWeight = FontWeight.Bold,
                        color = Gray900
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (trendUp) Blue50 else LossRedBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = if (trendUp) Blue600 else LossRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// KpiCard - Metric display card with icon, label, value
@Composable
@Suppress("UNUSED_PARAMETER")
fun KpiCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    backgroundColor: Color = BgPrimary,
    textColor: Color = TextPrimary,
    amountColor: Color = TextPrimary,
    isPositive: Boolean = true,
    prefix: String = ""
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$prefix$amount",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                )
            }
        }
    }
}

// TransactionCard - Transaction list item
@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isExpense = transaction.type == "EXPENSE"
    val amountColor = if (isExpense) LossRed else ProfitGreen
    val iconBg = if (isExpense) LossRedBg else ProfitGreenBg
    val icon = if (isExpense) Icons.Default.TrendingDown else Icons.Default.TrendingUp
    
    KiranaCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        formatTransactionDate(transaction.date, transaction.time),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "${if (isExpense) "-" else "+"}₹${kotlin.math.abs(transaction.amount).toInt()}",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = amountColor
            )
        }
    }
}

// CustomerCard - Customer list item with avatar, due amount, actions
@Composable
fun CustomerCard(
    customer: CustomerEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemindClick: () -> Unit = {}
) {
    val amountDue = customer.balance // PartyEntity uses 'balance' field
    val hasOverdue = amountDue > 0
    val leftBarColor = if (hasOverdue) LossRed else ProfitGreen
    val amountColor = if (hasOverdue) LossRed else TextPrimary
    val initial = customer.name.firstOrNull()?.toString() ?: "?"
    val avatarColor = Color(0xFFFF6B6B) // Default avatar color since PartyEntity doesn't have avatarColor
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left border indicator
            if (hasOverdue) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(leftBarColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Customer info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    customer.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        customer.phone,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasOverdue) {
                        TextButton(onClick = onRemindClick) {
                            Icon(
                                Icons.Default.Message,
                                contentDescription = null,
                                tint = ProfitGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remind", color = ProfitGreen, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TOTAL SALES", fontSize = 10.sp, color = TextSecondary)
                        Text("₹0", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary) // TODO: derive from transactions
                    }
                }
            }
            
            // Due amount
            Column(horizontalAlignment = Alignment.End) {
                Text("DUE", fontSize = 10.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "₹${amountDue.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = amountColor
                )
            }
        }
    }
}

// ItemCard - Inventory item with stock status, margin, edit/delete
@Composable
fun ItemCard(
    item: ItemEntity,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val stockStatus = when {
        item.stock > 20 -> Pair("${item.stock} in Stock", ProfitGreen)
        item.stock > 5 -> Pair("${item.stock} in Stock", Color(0xFFFFA500)) // Orange
        else -> Pair("${item.stock} in Stock", LossRed)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gray100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Gray400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${item.category} • ${item.rackLocation}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(stockStatus.second.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    stockStatus.first,
                                    color = stockStatus.second,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${item.marginPercentage.toInt()}% Margin",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Text(
                    "₹${item.price.toInt()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = LossRed,
                        containerColor = LossRedBg
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp, color = LossRed)
                }
            }
        }
    }
}

// VendorCard - Vendor list item with payables, actions
@Composable
fun VendorCard(
    vendor: VendorEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPayNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dueAmount = -vendor.balance // For vendors, negative balance means payable
    val hasPayable = dueAmount > 0
    val amountColor = if (hasPayable) LossRed else TextPrimary
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gray100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Gray400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            vendor.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            vendor.phone,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text("PURCHASES", fontSize = 10.sp, color = TextSecondary)
                                Text("₹0", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary) // TODO: Calculate totalPurchases from transactions
                            }
                            Column {
                                Text("DUE AMOUNT", fontSize = 10.sp, color = TextSecondary)
                                Text("₹${dueAmount.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = amountColor)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPayNow,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                ) {
                    Text("Pay Now", fontSize = 12.sp)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LossRed)
                }
            }
        }
    }
}

// SearchField - Standardized search input
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, Gray200, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(placeholder, color = TextSecondary, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
        }
    }
}

// FilterButton - Time-range filter pill buttons
@Composable
fun FilterButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) InteractiveCyan else BgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) White else TextPrimary
        )
    }
}

// Helper function to format transaction date
private fun formatTransactionDate(date: Long, time: String?): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = date
    val month = calendar.get(java.util.Calendar.MONTH) + 1
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val timeStr = if (time != null && time.isNotEmpty()) " • $time" else ""
    return "$month/$day$timeStr"
}
