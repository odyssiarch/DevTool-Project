package com.jartool.app.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Satu proyek = satu folder berisi original.jar, dex/, dan hasil kerja lain. */
object Workspace {
    fun root(ctx: Context): File = File(ctx.filesDir, "projects").apply { mkdirs() }

    fun projects(ctx: Context): List<File> =
        root(ctx).listFiles()
            ?.filter { it.isDirectory && File(it, "original.jar").exists() }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun jarOf(ctx: Context, project: String): File = File(File(root(ctx), project), "original.jar")

    fun importFile(ctx: Context, file: File): String =
        file.inputStream().use { importStream(ctx, file.name, it) }

    fun importUri(ctx: Context, uri: Uri): String {
        val name = ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "berkas.jar"
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Tidak bisa membaca berkas")
        return input.use { importStream(ctx, name, it) }
    }

    private fun importStream(ctx: Context, displayName: String, input: InputStream): String {
        val base = displayName.removeSuffix(".jar")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "proyek" }
        var name = base
        var i = 2
        while (File(root(ctx), name).exists()) name = "${base}_${i++}"
        val dir = File(root(ctx), name).apply { mkdirs() }
        val jar = File(dir, "original.jar")
        try {
            jar.outputStream().use { input.copyTo(it) }
            ZipFile(jar).close()
        } catch (e: ZipException) {
            dir.deleteRecursively()
            throw IllegalArgumentException("Bukan berkas JAR atau ZIP yang valid")
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        return name
    }
}
