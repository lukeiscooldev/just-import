package com.mcup.importer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MinecraftPackInfo(
    val name: String,
    val uri: Uri,
    val size: Long,
    val isAddonBundle: Boolean,
    val packType: String, // "resource", "behavior", or "addon_bundle"
    val uuid: String? = null,
    val version: String? = null
)

data class MinecraftPackageInfo(
    val name: String,
    val packageName: String,
    val basePath: String,
    val isInstalled: Boolean
)

data class ImporterUiState(
    val selectedPack: MinecraftPackInfo? = null,
    val availablePackages: List<MinecraftPackageInfo> = emptyList(),
    val selectedPackage: MinecraftPackageInfo = MinecraftPackageInfo(
        name = "Minecraft Bedrock Retail",
        packageName = "com.mojang.minecraftpe",
        basePath = "Android/data/com.mojang.minecraftpe/files/games/com.mojang",
        isInstalled = true
    ),
    val customPath: String = "",
    val resolvedTargetPath: String = "",
    val isSafGranted: Boolean = false,
    val safTreeUri: Uri? = null,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val currentFileName: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val importSuccess: Boolean = false,
    val showSuccessDialog: Boolean = false,
    val errorMessage: String? = null
)

class MinecraftImporterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImporterUiState())
    val uiState: StateFlow<ImporterUiState> = _uiState.asStateFlow()

    init {
        detectInstalledPackages()
        updateTargetPath()
    }

    private fun detectInstalledPackages() {
        val defaultPackages = listOf(
            MinecraftPackageInfo(
                "Minecraft Bedrock",
                "com.mojang.minecraftpe",
                "Android/data/com.mojang.minecraftpe/files/games/com.mojang",
                isInstalled = true
            ),
            MinecraftPackageInfo(
                "Minecraft Preview",
                "com.mojang.minecraftpreview",
                "Android/data/com.mojang.minecraftpreview/files/games/com.mojang",
                isInstalled = false
            ),
            MinecraftPackageInfo(
                "Minecraft Education",
                "com.mojang.minecraftpe.edu",
                "Android/data/com.mojang.minecraftpe.edu/files/games/com.mojang",
                isInstalled = false
            )
        )
        _uiState.update { it.copy(availablePackages = defaultPackages) }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val packInfo = PackExtractor.inspectPack(context, uri)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        selectedPack = packInfo,
                        totalBytes = packInfo.size
                    )
                }
                updateTargetPath()
            }
        }
    }

    fun handleIncomingUri(context: Context, uri: Uri) {
        onFileSelected(context, uri)
    }

    fun onSafFolderGranted(context: Context, treeUri: Uri) {
        // Persist URI permission so the user doesn't have to grant it again!
        SafStorageManager.persistTreePermission(context, treeUri)
        _uiState.update {
            it.copy(isSafGranted = true, safTreeUri = treeUri)
        }
    }

    fun selectPackage(pkg: MinecraftPackageInfo) {
        _uiState.update { it.copy(selectedPackage = pkg) }
        updateTargetPath()
    }

    fun setCustomPath(path: String) {
        _uiState.update { it.copy(customPath = path) }
        updateTargetPath()
    }

    private fun updateTargetPath() {
        val state = _uiState.value
        val basePath = if (state.customPath.isNotBlank()) state.customPath else state.selectedPackage.basePath
        val subFolder = when (state.selectedPack?.packType) {
            "behavior" -> "behavior_packs"
            "addon_bundle" -> "resource_packs & behavior_packs"
            else -> "resource_packs"
        }
        _uiState.update {
            it.copy(resolvedTargetPath = "$basePath/$subFolder")
        }
    }

    fun startImport(context: Context) {
        val state = _uiState.value
        val pack = state.selectedPack ?: return

        _uiState.update {
            it.copy(
                isImporting = true,
                importProgress = 0f,
                importSuccess = false,
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                PackExtractor.extractPackToDestination(
                    context = context,
                    packUri = pack.uri,
                    treeUri = state.safTreeUri,
                    basePath = state.selectedPackage.basePath,
                    onProgress = { progress, fileName, bytesWritten ->
                        _uiState.update {
                            it.copy(
                                importProgress = progress,
                                currentFileName = fileName,
                                bytesTransferred = bytesWritten
                            )
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importProgress = 1f,
                            importSuccess = true,
                            showSuccessDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = e.localizedMessage ?: "Import failed"
                        )
                    }
                }
            }
        }
    }

    fun dismissSuccessDialog() {
        _uiState.update { it.copy(showSuccessDialog = false) }
    }
}
