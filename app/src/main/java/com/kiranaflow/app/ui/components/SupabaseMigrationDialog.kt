package com.kiranaflow.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog for migrating data to Supabase Direct Integration
 */
@Composable
fun SupabaseMigrationDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onStartMigration: (forceOverwrite: Boolean) -> Unit,
    migrationInProgress: Boolean = false,
    migrationResult: SupabaseMigrationResult? = null
) {
    if (!isVisible) return

    var forceOverwrite by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Migrate to Supabase",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Move your local data to Supabase for real-time sync and cloud backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Migration Info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "What will be migrated:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text("• All items and inventory", style = MaterialTheme.typography.bodySmall)
                        Text("• Customers and vendors", style = MaterialTheme.typography.bodySmall)
                        Text("• Sales and expense transactions", style = MaterialTheme.typography.bodySmall)
                        Text("• Transaction line items", style = MaterialTheme.typography.bodySmall)
                        Text("• Reminders", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Force Overwrite Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = forceOverwrite,
                        onCheckedChange = { forceOverwrite = it }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overwrite existing data",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Replace any existing data in Supabase",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Migration Progress/Result
                when {
                    migrationInProgress -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Migrating data...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    migrationResult != null -> {
                        MigrationResultCard(result = migrationResult)
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !migrationInProgress
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onStartMigration(forceOverwrite) },
                        modifier = Modifier.weight(1f),
                        enabled = !migrationInProgress
                    ) {
                        Text("Start Migration")
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationResultCard(result: SupabaseMigrationResult) {
    val cardColor = if (result.success) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    Card(colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (result.success) "✅ Migration Successful" else "❌ Migration Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (result.success) {
                Text("Items migrated: ${result.itemsMigrated}", style = MaterialTheme.typography.bodySmall)
                Text("Parties migrated: ${result.partiesMigrated}", style = MaterialTheme.typography.bodySmall)
                Text("Transactions migrated: ${result.transactionsMigrated}", style = MaterialTheme.typography.bodySmall)
                Text("Transaction items: ${result.transactionItemsMigrated}", style = MaterialTheme.typography.bodySmall)
                Text("Reminders migrated: ${result.remindersMigrated}", style = MaterialTheme.typography.bodySmall)
            } else {
                result.errors.forEach { error ->
                    Text(
                        text = "• $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

/**
 * Data class for migration result
 */
data class SupabaseMigrationResult(
    val success: Boolean,
    val itemsMigrated: Int = 0,
    val partiesMigrated: Int = 0,
    val transactionsMigrated: Int = 0,
    val transactionItemsMigrated: Int = 0,
    val remindersMigrated: Int = 0,
    val errors: List<String> = emptyList()
)
