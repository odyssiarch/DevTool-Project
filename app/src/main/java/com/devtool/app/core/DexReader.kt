package com.devtool.app.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexContent(val version: String, val logical: Int, val classes: List<String>)

/**
 * Pembaca dex berbasis header. Mendukung versi 035 sampai 041 dan seterusnya
 * selama tata letak header sama, termasuk kontainer dex 041: beberapa dex logis
 * digabung dalam satu berkas, tiap dex diawali header sendiri, dan semua offset
 * dihitung dari awal berkas fisik.
 */
object DexReader {
    fun read(file: File): DexContent {
        val bytes = file.readBytes()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val heads = headers(b)
        if (heads.isEmpty()) throw IllegalArgumentException("Bukan berkas dex")
        val version = String(bytes, heads[0] + 4, 3, Charsets.US_ASCII)
        val names = heads.flatMap { runCatching { classNamesAt(bytes, b, it) }.getOrDefault(emptyList()) }
        return DexContent(version, heads.size, names)
    }

    fun classNames(file: File): List<String> = read(file).classes

    private fun isMagic(b: ByteBuffer, p: Int): Boolean {
        if (p < 0 || p + 8 > b.limit()) return false
        return b.get(p).toInt() == 0x64 && b.get(p + 1).toInt() == 0x65 &&
            b.get(p + 2).toInt() == 0x78 && b.get(p + 3).toInt() == 0x0A &&
            (4..6).all { b.get(p + it).toInt() in 0x30..0x39 } && b.get(p + 7).toInt() == 0
    }

    /** Posisi awal tiap dex logis di dalam berkas. Dex biasa menghasilkan satu posisi (0). */
    private fun headers(b: ByteBuffer): List<Int> {
        val out = ArrayList<Int>()
        var pos = 0
        while (isMagic(b, pos) && pos + 0x70 <= b.limit()) {
            out.add(pos)
            val size = b.getInt(pos + 0x20) // file_size: ukuran dex logis ini
            if (size < 0x70 || size > b.limit() - pos) break
            pos += size
            if (!isMagic(b, pos)) {
                val aligned = (pos + 3) and 3.inv()
                if (isMagic(b, aligned)) pos = aligned
            }
        }
        return out
    }

    private fun classNamesAt(bytes: ByteArray, b: ByteBuffer, base: Int): List<String> {
        val strOff = b.getInt(base + 0x3C)
        val typeOff = b.getInt(base + 0x44)
        val classSize = b.getInt(base + 0x60)
        val classOff = b.getInt(base + 0x64)

        fun str(idx: Int): String {
            var p = b.getInt(strOff + idx * 4)
            while ((b.get(p).toInt() and 0x80) != 0) p++
            p++
            val start = p
            while (b.get(p).toInt() != 0) p++
            return String(bytes, start, p - start, Charsets.UTF_8)
        }

        return List(classSize) { i ->
            val classIdx = b.getInt(classOff + i * 32)
            val descIdx = b.getInt(typeOff + classIdx * 4)
            str(descIdx).removePrefix("L").removeSuffix(";").replace('/', '.')
        }
    }
}
