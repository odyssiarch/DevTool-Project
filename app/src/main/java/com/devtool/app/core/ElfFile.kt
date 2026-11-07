package com.devtool.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfSymbol(
    val name: String,
    val vaddr: Long,
    val size: Long,
    /** Offset di berkas, atau -1 jika alamat tidak terpetakan ke segmen LOAD. */
    val offset: Long,
    val thumb: Boolean,
)

class ElfInfo(
    val is64: Boolean,
    val little: Boolean,
    val machine: Int,
    val type: Int,
    val sections: List<String>,
    val needed: List<String>,
    val soname: String?,
    val funcs: List<ElfSymbol>,
) {
    val machineName: String get() = ElfFile.machineName(machine)
    val typeName: String get() = ElfFile.typeName(type)
}

private class Seg(val off: Long, val vaddr: Long, val filesz: Long)
private class Sec(val name: Int, val type: Long, val off: Long, val size: Long, val link: Int, val entsize: Long)

/** Pembaca ELF sederhana: header, section, simbol fungsi, dan dependensi (NEEDED). */
object ElfFile {
    const val EM_X86 = 3
    const val EM_ARM = 40
    const val EM_X86_64 = 62
    const val EM_AARCH64 = 183

    fun machineName(m: Int): String = when (m) {
        EM_AARCH64 -> "ARM64 (AArch64)"
        EM_ARM -> "ARM 32-bit"
        EM_X86_64 -> "x86-64"
        EM_X86 -> "x86 32-bit"
        8 -> "MIPS"
        243 -> "RISC-V"
        else -> "mesin $m"
    }

    fun typeName(t: Int): String = when (t) {
        1 -> "REL (objek)"
        2 -> "EXEC"
        3 -> "DYN (shared library / PIE)"
        4 -> "CORE"
        else -> "tipe $t"
    }

    /** Mengembalikan null jika bukan ELF atau rusak. */
    fun parse(data: ByteArray): ElfInfo? {
        if (data.size < 52) return null
        if (data[0] != 0x7F.toByte() || data[1] != 'E'.code.toByte() ||
            data[2] != 'L'.code.toByte() || data[3] != 'F'.code.toByte()
        ) return null
        val is64 = data[4].toInt() == 2
        val little = data[5].toInt() != 2
        val bb = ByteBuffer.wrap(data).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        fun u16(o: Int): Int = bb.getShort(o).toInt() and 0xFFFF
        fun u32(o: Int): Long = bb.getInt(o).toLong() and 0xFFFFFFFFL
        fun u64(o: Int): Long = bb.getLong(o)

        fun cstr(off: Long): String {
            if (off < 0 || off >= data.size) return ""
            val s = off.toInt()
            var e = s
            while (e < data.size && data[e] != 0.toByte()) e++
            return String(data, s, e - s, Charsets.UTF_8)
        }

        try {
            val type = u16(16)
            val machine = u16(18)
            val phoff = if (is64) u64(32) else u32(28)
            val shoff = if (is64) u64(40) else u32(32)
            val phentsize = if (is64) u16(54) else u16(42)
            val phnum = if (is64) u16(56) else u16(44)
            val shentsize = if (is64) u16(58) else u16(46)
            val shnum = if (is64) u16(60) else u16(48)
            val shstrndx = if (is64) u16(62) else u16(50)

            val loads = ArrayList<Seg>()
            for (i in 0 until phnum) {
                val p = (phoff + i.toLong() * phentsize).toInt()
                if (p + phentsize > data.size) break
                if (u32(p) == 1L) {
                    if (is64) loads.add(Seg(u64(p + 8), u64(p + 16), u64(p + 32)))
                    else loads.add(Seg(u32(p + 4), u32(p + 8), u32(p + 16)))
                }
            }

            fun toOffset(v: Long): Long {
                for (s in loads) if (v >= s.vaddr && v < s.vaddr + s.filesz) return v - s.vaddr + s.off
                return -1
            }

            val secs = ArrayList<Sec>()
            for (i in 0 until shnum) {
                val p = (shoff + i.toLong() * shentsize).toInt()
                if (p + shentsize > data.size) break
                if (is64) secs.add(Sec(u32(p).toInt(), u32(p + 4), u64(p + 24), u64(p + 32), u32(p + 40).toInt(), u64(p + 56)))
                else secs.add(Sec(u32(p).toInt(), u32(p + 4), u32(p + 16), u32(p + 20), u32(p + 24).toInt(), u32(p + 36)))
            }

            val shstr = secs.getOrNull(shstrndx)
            val names = secs.map { if (shstr == null) "" else cstr(shstr.off + it.name) }

            val funcs = ArrayList<ElfSymbol>()
            val seen = HashSet<String>()
            for (s in secs) {
                if (s.type != 11L && s.type != 2L) continue
                val str = secs.getOrNull(s.link) ?: continue
                val es = if (s.entsize > 0) s.entsize.toInt() else if (is64) 24 else 16
                val n = (s.size / es).toInt()
                for (i in 0 until n) {
                    val p = (s.off + i.toLong() * es).toInt()
                    if (p + es > data.size) break
                    val nameOff: Long
                    val info: Int
                    val shndx: Int
                    val value: Long
                    val size: Long
                    if (is64) {
                        nameOff = u32(p)
                        info = data[p + 4].toInt() and 0xFF
                        shndx = u16(p + 6)
                        value = u64(p + 8)
                        size = u64(p + 16)
                    } else {
                        nameOff = u32(p)
                        value = u32(p + 4)
                        size = u32(p + 8)
                        info = data[p + 12].toInt() and 0xFF
                        shndx = u16(p + 14)
                    }
                    if ((info and 0xF) != 2 || shndx == 0) continue
                    val name = cstr(str.off + nameOff)
                    if (name.isEmpty()) continue
                    val thumb = machine == EM_ARM && (value and 1L) == 1L
                    val v = if (thumb) value and 1L.inv() else value
                    if (!seen.add("$name@$v")) continue
                    funcs.add(ElfSymbol(name, v, size, toOffset(v), thumb))
                }
            }
            funcs.sortBy { it.vaddr }

            val needed = ArrayList<String>()
            var soname: String? = null
            for (s in secs) {
                if (s.type != 6L) continue
                val str = secs.getOrNull(s.link) ?: continue
                val es = if (is64) 16 else 8
                var p = s.off
                while (p + es <= s.off + s.size && p + es <= data.size) {
                    val tag = if (is64) u64(p.toInt()) else u32(p.toInt())
                    val v = if (is64) u64(p.toInt() + 8) else u32(p.toInt() + 4)
                    if (tag == 0L) break
                    if (tag == 1L) needed.add(cstr(str.off + v))
                    if (tag == 14L) soname = cstr(str.off + v)
                    p += es
                }
            }

            return ElfInfo(is64, little, machine, type, names.filter { it.isNotEmpty() }, needed, soname, funcs)
        } catch (e: Exception) {
            return null
        }
    }
}

/** Alat umum untuk berkas biner: parse hex, cari pola dengan wildcard, ambil string. */
object BinUtil {
    fun parseHex(s: String): ByteArray? {
        val t = s.replace("0x", "", ignoreCase = true).filter { !it.isWhitespace() && it != ',' }
        if (t.isEmpty() || t.length % 2 != 0) return null
        val out = ByteArray(t.length / 2)
        for (i in out.indices) {
            val h = Character.digit(t[2 * i], 16)
            val l = Character.digit(t[2 * i + 1], 16)
            if (h < 0 || l < 0) return null
            out[i] = ((h shl 4) or l).toByte()
        }
        return out
    }

    /** "20 00 ?? 52" -> [0x20, 0x00, -1, 0x52]; -1 berarti wildcard. */
    fun parsePattern(s: String): IntArray? {
        val toks = s.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (toks.isEmpty()) return null
        val out = ArrayList<Int>()
        for (t in toks) {
            if (t == "??" || t == "?" || t == "**") {
                out.add(-1)
                continue
            }
            if (t.length % 2 != 0) return null
            var i = 0
            while (i < t.length) {
                val v = t.substring(i, i + 2).toIntOrNull(16) ?: return null
                out.add(v)
                i += 2
            }
        }
        return out.toIntArray()
    }

    fun find(data: ByteArray, pat: IntArray, limit: Int = 300): List<Int> {
        val out = ArrayList<Int>()
        if (pat.isEmpty()) return out
        val last = data.size - pat.size
        var i = 0
        while (i <= last && out.size < limit) {
            var ok = true
            for (j in pat.indices) {
                val p = pat[j]
                if (p >= 0 && (data[i + j].toInt() and 0xFF) != p) {
                    ok = false
                    break
                }
            }
            if (ok) out.add(i)
            i++
        }
        return out
    }

    fun strings(data: ByteArray, minLen: Int = 6, limit: Int = 5000): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var start = -1
        for (i in 0..data.size) {
            val c = if (i < data.size) data[i].toInt() and 0xFF else 0
            if (c in 0x20..0x7E) {
                if (start < 0) start = i
            } else {
                if (start >= 0 && i - start >= minLen) {
                    out.add(start to String(data, start, i - start, Charsets.ISO_8859_1))
                    if (out.size >= limit) break
                }
                start = -1
            }
        }
        return out
    }

    fun hex(data: ByteArray, off: Int, len: Int): String {
        val end = minOf(data.size, off + len)
        val sb = StringBuilder()
        for (i in off until end) {
            if (i > off) sb.append(' ')
            sb.append(String.format("%02X", data[i].toInt() and 0xFF))
        }
        return sb.toString()
    }
}
