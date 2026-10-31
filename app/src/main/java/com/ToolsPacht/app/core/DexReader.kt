package com.jartool.app.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DexReader {
    /** Daftar nama class (format a.b.C) dari header dex. */
    fun classNames(file: File): List<String> {
        val bytes = file.readBytes()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val strOff = b.getInt(0x3C)
        val typeOff = b.getInt(0x44)
        val classSize = b.getInt(0x60)
        val classOff = b.getInt(0x64)

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
