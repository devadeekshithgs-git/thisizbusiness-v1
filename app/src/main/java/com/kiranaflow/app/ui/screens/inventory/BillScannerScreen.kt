package com.kiranaflow.app.ui.screens.inventory

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.Blue600
import com.kiranaflow.app.ui.theme.GrayBg
import com.kiranaflow.app.ui.theme.TextPrimary
import com.kiranaflow.app.ui.theme.TextSecondary
import com.kiranaflow.app.ui.theme.White
import com.kiranaflow.app.util.DocumentScannerHelper
import com.kiranaflow.app.util.InventoryImportDemoGenerator
import com.kiranaflow.app.util.gst.GstFileExporter
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.app.Activity

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BillScannerScreen(
    onDismiss: () -> Unit,
    onBillDocumentSelected: (Uri) -> Unit,
    onInventoryFileSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val cr = context.contentResolver
    val scope = rememberCoroutineScope()
    var pendingDocScanIntentSender by remember { mutableStateOf<IntentSender?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            pendingCameraUri?.let { onBillDocumentSelected(it) }
        }
        pendingCameraUri = null
    }

    val docScannerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            val out = DocumentScannerHelper.parseResult(res.data)
            val uri = out?.pageUris?.firstOrNull() ?: out?.pdfUri
            if (uri != null) onBillDocumentSelected(uri)
            pendingDocScanIntentSender = null
        }

    val billPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    cr.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                onBillDocumentSelected(uri)
            }
        }
    )

    val inventoryFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                runCatching {
                    cr.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                onInventoryFileSelected(uri)
            }
        }
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Import Stock", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                        Text(
                            "Scan vendor bills (image/PDF) or upload inventory sheet (CSV/XLSX)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            
            item {
                Surface(
                    color = GrayBg,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Bulk Inventory Upload", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "Required: item_name (or name). Optional: stock or qty, cost_price, sell_price, category, vendor",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        KiranaButton(
                            text = "Choose inventory file (CSV/XLSX)",
                            onClick = {
                                inventoryFilePicker.launch(
                                    arrayOf(
                                        "text/csv",
                                        "application/csv",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    )
                                )
                            },
                            icon = Icons.Default.UploadFile,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val text = InventoryImportDemoGenerator.generateCsv()
                                    val uri = GstFileExporter.saveTextToDownloads(
                                        context = context,
                                        displayName = "inventory_import_demo.csv",
                                        mimeType = "text/csv",
                                        text = text
                                    )
                                    Toast.makeText(
                                        context,
                                        if (uri != null) "Demo CSV saved to Downloads" else "Could not save demo CSV",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Download demo CSV") }

                            TextButton(
                                onClick = {
                                    val bytes = InventoryImportDemoGenerator.generateXlsx()
                                    val uri = GstFileExporter.saveBytesToDownloads(
                                        context = context,
                                        displayName = "inventory_import_demo.xlsx",
                                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        bytes = bytes
                                    )
                                    Toast.makeText(
                                        context,
                                        if (uri != null) "Demo XLSX saved to Downloads" else "Could not save demo XLSX",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Download demo XLSX") }
                        }
                    }
                }
            }
        }
    }
}


