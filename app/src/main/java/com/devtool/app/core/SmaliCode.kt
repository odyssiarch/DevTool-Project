package com.devtool.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Mengambil teks Smali satu class untuk ditampilkan. */
object SmaliCode {
    /**
     * Memakai folder Smali proyek kalau JAR sudah dibongkar untuk diedit.
     * Kalau belum, JAR dibongkar sekali ke cache lalu dipakai ulang.
     */
    suspend fun load(ctx: Context, project: String, cls: String, log: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val root = if (SmaliEngine.hasSmali(ctx, project)) {
                log("Membaca Smali dari folder proyek")
                SmaliEngine.smaliRoot(ctx, project)
            } else {
                SmaliDiff.ensureDir(ctx, project, "JAR", log)
            }
            val rel = cls.replace('.', '/') + ".smali"
            val file = SmaliDiff.dexFolders(root).map { File(it, rel) }.firstOrNull { it.isFile }
                ?: throw IllegalStateException("Smali untuk $cls tidak ditemukan")
            file.readText()
        }
}
