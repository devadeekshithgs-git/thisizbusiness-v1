package com.kiranaflow.app.ui.screens.inventory

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kiranaflow.app.ui.components.KiranaButton
import com.kiranaflow.app.ui.theme.BgPrimary
import com.kiranaflow.app.ui.theme.Blue600
import com.kiranaflow.app.ui.theme.GrayBg
import com.kiranaflow.app.ui.theme.TextPrimary
import com.kiranaflow.app.ui.theme.TextSecondary
import com.kiranaflow.app.ui.theme.White
import java.io.File

@Composable
fun BillScannerScreen(
    onDismiss: () -> Unit,
    onBillDocumentSelected: (Uri) -> Unit,
    onInventoryFileSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val cr = context.contentResolver
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            pendingCameraUri?.let { onBillDocumentSelected(it) }
        }
        pendingCameraUri = null
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp).imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = White
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = GrayBg,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Vendor Bill OCR", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Supports: Camera, JPG/PNG/PDF", fontSize = 12.sp, color = TextSecondary)
                        KiranaButton(
                            text = "Scan bill (camera)",
                            onClick = {
                                val dir = File(context.cacheDir, "bill_scans").apply { mkdirs() }
                                val photoFile = File.createTempFile("bill_scan_", ".jpg", dir)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                pendingCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            icon = Icons.Default.FileOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
                        )
                        KiranaButton(
                            text = "Choose bill (image/PDF)",
                            onClick = { billPicker.launch(arrayOf("image/*", "application/pdf")) },
                            icon = Icons.Default.FileOpen,
                            colors = ButtonDefaults.buttonColors(containerColor = Blue600, contentColor = BgPrimary)
                        )
                    }
                }

                Surface(
                    color = GrayBg,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Bulk Inventory Upload", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Supports: .csv, .xlsx", fontSize = 12.sp, color = TextSecondary)
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
                    }
                }
            }
        }
    }
}


