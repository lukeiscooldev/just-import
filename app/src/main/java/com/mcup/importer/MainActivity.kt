package com.mcup.importer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcup.importer.ui.theme.MinecraftImporterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MinecraftImporterViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming .mcpack or .mcaddon via "Open with" intent
        intent?.data?.let { uri ->
            viewModel.handleIncomingUri(this, uri)
        }

        setContent {
            MinecraftImporterTheme {
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                // Launcher 1: Browse .mcpack / .mcaddon file
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let {
                        viewModel.onFileSelected(context, it)
                    }
                }

                // Launcher 2: SAF Scoped Storage Folder Grant (Android 11+)
                val safFolderLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { treeUri: Uri? ->
                    treeUri?.let {
                        viewModel.onSafFolderGranted(context, it)
                    }
                }

                // Show success toast on complete
                LaunchedEffect(uiState.importSuccess) {
                    if (uiState.importSuccess) {
                        Toast.makeText(
                            context,
                            "Pack successfully imported to Minecraft!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                    Text(
                                        "Minecraft Pack Importer",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 1: File Picker Card
                            item {
                                PackPickerCard(
                                    selectedPack = uiState.selectedPack,
                                    onPickFile = {
                                        // Pick .mcpack, .mcaddon, or zip files
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "application/octet-stream",
                                                "application/zip",
                                                "*/*"
                                            )
                                        )
                                    }
                                )
                            }

                            // Section 2: Target Minecraft Package Selector
                            item {
                                TargetPackageSelectorCard(
                                    packages = uiState.availablePackages,
                                    selectedTarget = uiState.selectedPackage,
                                    onSelectTarget = { viewModel.selectPackage(it) },
                                    customPath = uiState.customPath,
                                    onCustomPathChange = { viewModel.setCustomPath(it) }
                                )
                            }

                            // Section 3: SAF Scoped Storage Authorization (Android 11+)
                            item {
                                SafPermissionCard(
                                    isPermissionGranted = uiState.isSafGranted,
                                    targetFolder = uiState.resolvedTargetPath,
                                    onRequestSaf = {
                                        // Trigger ACTION_OPEN_DOCUMENT_TREE
                                        safFolderLauncher.launch(null)
                                    }
                                )
                            }

                            // Section 4: Action Button
                            item {
                                Button(
                                    onClick = { viewModel.startImport(context) },
                                    enabled = uiState.selectedPack != null && !uiState.isImporting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (uiState.isImporting) "Importing Pack..." else "Import Pack Directly",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        // Progress & Verification Dialog Overlay
                        if (uiState.isImporting) {
                            ImportProgressDialog(
                                progress = uiState.importProgress,
                                currentFile = uiState.currentFileName,
                                bytesTransferred = uiState.bytesTransferred,
                                totalBytes = uiState.totalBytes
                            )
                        }

                        // Success Dialog
                        if (uiState.showSuccessDialog) {
                            SuccessVerificationDialog(
                                packName = uiState.selectedPack?.name ?: "Pack",
                                targetPath = uiState.resolvedTargetPath,
                                onDismiss = { viewModel.dismissSuccessDialog() },
                                onLaunchMinecraft = {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(
                                        uiState.selectedPackage.packageName
                                    )
                                    if (launchIntent != null) {
                                        startActivity(launchIntent)
                                    } else {
                                        Toast.makeText(context, "Cannot open Minecraft automatically", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
