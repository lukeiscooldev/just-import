package com.mcup.importer.extractor

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mcup.importer.MinecraftPackInfo
import com.mcup.importer.storage.SafStorageManager
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PackExtractor {

    /**
     * Inspects a selected .mcpack or .mcaddon file to read its manifest.json,
     * detect whether it is a resource pack, behavior pack, or composite addon bundle.
     */
    fun inspectPack(context: Context, uri: Uri): MinecraftPackInfo {
        var packName = "Custom Minecraft Pack"
        var packType = "resource"
        var isAddonBundle = false
        var uuid: String? = null
        var version: String? = null

        val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: 0L

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.endsWith("manifest.json")) {
                        val content = readStringFromStream(zis)
                        try {
                            val json = JSONObject(content)
                            val header = json.optJSONObject("header")
                            if (header != null) {
                                packName = header.optString("name", packName)
                                uuid = header.optString("uuid")
                                val verArray = header.optJSONArray("version")
                                if (verArray != null) {
                                    version = "${verArray.optInt(0)}.${verArray.optInt(1)}.${verArray.optInt(2)}"
                                }
                            }

                            val modules = json.optJSONArray("modules")
                            if (modules != null) {
                                for (i in 0 until modules.length()) {
                                    val modType = modules.getJSONObject(i).optString("type")
                                    if (modType == "data" || modType == "behavior") {
                                        packType = "behavior"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else if (name.endsWith(".mcpack")) {
                        isAddonBundle = true
                        packType = "addon_bundle"
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        return MinecraftPackInfo(
            name = packName,
            uri = uri,
            size = fileSize,
            isAddonBundle = isAddonBundle,
            packType = packType,
            uuid = uuid,
            version = version
        )
    }

    /**
     * Extracts .mcpack or .mcaddon into the target Minecraft scoped storage directory.
     */
    fun extractPackToDestination(
        context: Context,
        packUri: Uri,
        treeUri: Uri?,
        basePath: String,
        onProgress: (progress: Float, currentFile: String, bytesWritten: Long) -> Unit
    ) {
        val resolver = context.contentResolver
        val rootDoc = treeUri?.let { DocumentFile.fromTreeUri(context, it) }
            ?: throw IllegalStateException("Scoped Storage permission required. Please grant folder access.")

        val packInfo = inspectPack(context, packUri)
        val targetFolderName = packInfo.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

        // Determine destination folder (resource_packs vs behavior_packs)
        val parentTargetDir = if (packInfo.packType == "behavior") {
            SafStorageManager.getOrCreateDirectory(rootDoc, "behavior_packs")
        } else {
            SafStorageManager.getOrCreateDirectory(rootDoc, "resource_packs")
        }

        val packDestinationDir = SafStorageManager.getOrCreateDirectory(parentTargetDir, targetFolderName)

        var totalExtractedBytes = 0L
        val estimatedTotal = if (packInfo.size > 0) packInfo.size else 5_000_000L

        resolver.openInputStream(packUri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                val buffer = ByteArray(8192)

                while (entry != null) {
                    if (!entry.isDirectory) {
                        val pathSegments = entry.name.split("/").filter { it.isNotBlank() }
                        var currentDir = packDestinationDir
                        for (i in 0 until pathSegments.size - 1) {
                            currentDir = SafStorageManager.getOrCreateDirectory(currentDir, pathSegments[i])
                        }

                        val fileName = pathSegments.last()
                        val newFile = currentDir.createFile("application/octet-stream", fileName)
                            ?: currentDir.findFile(fileName)

                        if (newFile != null) {
                            resolver.openOutputStream(newFile.uri)?.use { out ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    out.write(buffer, 0, len)
                                    totalExtractedBytes += len
                                    val progress = (totalExtractedBytes.toFloat() / estimatedTotal).coerceIn(0f, 0.99f)
                                    onProgress(progress, fileName, totalExtractedBytes)
                                }
                                out.flush()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        onProgress(1.0f, "Completed Verification", totalExtractedBytes)
    }

    private fun readStringFromStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        return sb.toString()
    }
}
