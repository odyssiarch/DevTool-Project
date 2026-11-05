package com.devtool.app.core

import java.io.File

/** Operasi berkas untuk Editor Smali: daftar, cari, baca, simpan (dengan cadangan), kembalikan. */
object SmaliFiles {
    const val MAX_BYTES = 600_000L
    const val SEARCH_LIMIT = 200

    /** Folder/berkas internal (diawali "_", misalnya _backup dan _build) tidak ditampilkan. */
    private fun hidden(f: File) = f.name.startsWith("_")

    fun list(dir: File): List<File> =
        dir.listFiles()
            ?.filter { !hidden(it) && it.name != "layout.txt" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()

    fun search(root: File, query: String): List<File> =
        root.walkTopDown()
            .onEnter { it == root || !hidden(it) }
            .filter { it.isFile && it.name.endsWith(".smali") && it.name.contains(query, ignoreCase = true) }
            .take(SEARCH_LIMIT)
            .toList()

    fun backupOf(root: File, file: File): File = File(root, "_backup/" + file.relativeTo(root).path)

    fun read(file: File): String = file.readText()

    /**
     * Simpan [content] ke [file]. Versi asli dicadangkan sekali saja, sebelum simpan pertama.
     * Ditulis ke berkas sementara dulu lalu di-rename, supaya berkas .smali tidak setengah
     * tertulis kalau aplikasi dimatikan di tengah proses.
     */
    fun save(root: File, file: File, content: String) {
        val backup = backupOf(root, file)
        if (!backup.exists()) {
            backup.parentFile?.mkdirs()
            file.copyTo(backup)
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(file)) file.writeText(content)
        } finally {
            tmp.delete()
        }
    }

    /** Kembalikan [file] ke cadangan asli dan kembalikan isinya. */
    fun restore(root: File, file: File): String {
        backupOf(root, file).copyTo(file, overwrite = true)
        return file.readText()
    }
}
