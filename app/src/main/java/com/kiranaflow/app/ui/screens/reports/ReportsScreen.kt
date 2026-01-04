package com.kiranaflow.app.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.GrayBg
import com.kiranaflow.app.ui.theme.Gray200
import com.kiranaflow.app.ui.theme.TextPrimary
import com.kiranaflow.app.ui.theme.TextSecondary
import com.kiranaflow.app.ui.theme.White

private data class ReportRow(
    val id: String,
    val title: String,
    val icon: ImageVector
)

private data class ReportSection(
    val title: String,
    val rows: List<ReportRow>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBackClick: () -> Unit,
    onReportClick: (String) -> Unit
) {

    val sections = remember {
        listOf(
            ReportSection(
                title = "Aggregate reports",
                rows = listOf(
                    ReportRow("cashbook_reports", "Cashbook Reports", Icons.Outlined.Description)
                )
            ),
            ReportSection(
                title = "Customer Report",
                rows = listOf(
                    ReportRow("customer_transactions", "Customer Transaction report", Icons.Outlined.Person),
                    ReportRow("customer_list", "Customer List", Icons.Outlined.Person)
                )
            ),
            ReportSection(
                title = "Bills Reports",
                rows = listOf(
                    ReportRow("sales_report_bills", "Sales Report", Icons.Outlined.ShowChart),
                    ReportRow("sales_daywise", "Sales Day-wise Reports", Icons.Outlined.ReceiptLong)
                )
            ),
            ReportSection(
                title = "Inventory Reports",
                rows = listOf(
                    ReportRow("stocks_summary", "Stocks Summary", Icons.Outlined.Inventory2)
                )
            ),
            ReportSection(
                title = "Supplier Reports",
                rows = listOf(
                    ReportRow("supplier_transactions", "Supplier Transaction Report", Icons.Outlined.Storefront),
                    ReportRow("supplier_list", "Supplier List", Icons.Outlined.List)
                )
            )
        )
    }

    Scaffold(
        containerColor = GrayBg,
        topBar = {
            Surface(color = BgPrimary) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Your Report",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(GrayBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            sections.forEach { section ->
                item {
                    Text(
                        text = section.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        section.rows.forEach { row ->
                            ReportListCard(
                                title = row.title,
                                icon = row.icon,
                                onClick = { onReportClick(row.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportListCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Gray200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}