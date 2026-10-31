package com.jartool.app.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object Exporter {
    fun exportJar(ctx: Context, project: String, dest: Uri): String {
        val src = Workspace.outputOf(ctx, project)
        val out = ctx.contentResolver.openOutputStream(dest, "w") ?: error("Tidak bisa menulis ke tujuan")
        out.use { o -> src.inputStream().use { it.copyTo(o) } }
        return "JAR tersimpan (${formatSize(src.length())})"
    }

    fun exportDex(ctx: Context, project: String, tree: Uri): String {
        val jar = Workspace.outputOf(ctx, project)
        val tmp = File(ctx.cacheDir, "export-dex").apply { deleteRecursively(); mkdirs() }
        val files = JarPackager.extractDex(jar, tmp)
        if (files.isEmpty()) error("JAR ini tidak berisi classes.dex")
        val resolver = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        for (f in files) {
            val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", f.name)
                ?: error("Tidak bisa membuat ${f.name}")
            val out = resolver.openOutputStream(doc, "w") ?: error("Tidak bisa menulis ${f.name}")
            out.use { o -> f.inputStream().use { it.copyTo(o) } }
        }
        tmp.deleteRecursively()
        return "${files.size} berkas dex tersimpan"
    }
}
