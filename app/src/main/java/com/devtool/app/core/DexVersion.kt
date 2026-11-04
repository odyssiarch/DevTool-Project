package com.devtool.app.core

import java.io.File

object DexVersion {
    /** Mengembalikan versi dari header dex, misalnya "035" atau "039". Null jika bukan dex. */
    fun read(file: File): String? = file.inputStream().use { s ->
        val h = ByteArray(8)
        if (s.read(h) < 8) return null
        val magic = String(h, 0, 4, Charsets.US_ASCII)
        if (magic != "dex\n" || h[7] != 0.toByte()) return null
        String(h, 4, 3, Charsets.US_ASCII)
    }
}
