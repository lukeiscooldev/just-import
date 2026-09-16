package com.mcup.importer.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile

object SafStorageManager {

    /**
     * Persists URI permissions across device reboots and app launches.
     * Required for Scoped Storage on Android 11 (API 30) and newer.
     */
    fun persistTreePermission(context: Context, treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Traverses or creates the required nested directories inside the granted SAF tree.
     * E.g., navigates to "games/com.mojang/resource_packs" or "behavior_packs".
     */
    fun getOrCreateDirectory(
        parent: DocumentFile,
        subDirectoryName: String
    ): DocumentFile {
        val existing = parent.findFile(subDirectoryName)
        if (existing != null && existing.isDirectory) {
            return existing
        }
        return parent.createDirectory(subDirectoryName)
            ?: throw IllegalStateException("Could not create directory: $subDirectoryName")
    }

    /**
     * Resolves the target pack directory based on Android version and tree URI.
     */
    fun resolveTargetFolder(
        context: Context,
        treeUri: Uri?,
        subPath: String
    ): DocumentFile? {
        if (treeUri == null) return null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null

        val segments = subPath.split("/").filter { it.isNotBlank() }
        var current: DocumentFile = root
        for (segment in segments) {
            current = getOrCreateDirectory(current, segment)
        }
        return current
    }
}
