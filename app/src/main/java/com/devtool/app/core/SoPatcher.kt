package com.devtool.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Kesalahan saat membaca atau mengubah berkas ELF. Pesannya aman ditampilkan ke pengguna. */
class ElfException(message: String) : Exception(message)

/** Fungsi yang diekspor library: nama, alamat virtual, ukuran (0 = tidak diketahui), dan offset di berkas. */
data class ElfFunction(val name: String, val vaddr: Long, val size: Long, val fileOffset: Long)

/** Hasil patch satu fungsi. */
data class PatchItem(val name: String, val ok: Boolean, val detail: String)

/** Ringkasan isi library untuk ditampilkan. */
data class SoInfo(
    val arch: StubArch,
    val soname: String?,
    val needed: List<String>,
    val functions: List<ElfFunction>,
)

/**
 * Pembaca dan pengubah ELF64 little-endian (arm64 dan x86_64) langsung di memori, tanpa NDK.
 * [data] DIUBAH di tempat, jadi berikan salinan kalau berkas asli masih dibutuhkan.
 * Semua perubahan ukurannya tetap: tidak ada yang menggeser isi berkas, jadi tabel header tidak rusak.
 */
class ElfFile(val data: ByteArray) {
    private val bb: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    private class Seg(val off: Long, val vaddr: Long, val filesz: Long)
    private class Dyn(val index: Int, val tag: Long, val value: Long)

    private val loads = ArrayList<Seg>()
    private var dynOff = -1L
    private var dynSize = 0L
    private var strOff = -1L
    private var symOff = -1L
    private var symCount = 0

    var arch: StubArch = StubArch.ARM64
        private set

    init {
        guard {
            if (data.size < 64 || u32(0) != 0x464C457FL) throw ElfException("Bukan berkas ELF")
            if (u8(4) != 2) throw ElfException("Hanya ELF 64-bit yang didukung")
            if (u8(5) != 1) throw ElfException("Hanya ELF little-endian yang didukung")
            arch = when (u16(18)) {
                183 -> StubArch.ARM64
                62 -> StubArch.X86_64
                else -> throw ElfException("Arsitektur belum didukung (hanya arm64 dan x86_64)")
            }

            val phoff = u64(32)
            val phentsize = u16(54)
            val phnum = u16(56)
            for (i in 0 until phnum) {
                val p = phoff + i.toLong() * phentsize
                val type = u32(p)
                if (type == 1L) loads.add(Seg(u64(p + 8), u64(p + 16), u64(p + 32)))
                if (type == 2L) { dynOff = u64(p + 8); dynSize = u64(p + 32) }
            }
            if (dynOff < 0) throw ElfException("Tidak ada segmen DYNAMIC (ini bukan shared library)")

            var strV = -1L
            var symV = -1L
            var hashV = -1L
            var gnuV = -1L
            for (d in dynEntries()) {
                when (d.tag) {
                    4L -> hashV = d.value
                    5L -> strV = d.value
                    6L -> symV = d.value
                    0x6ffffef5L -> gnuV = d.value
                }
            }
            if (strV < 0 || symV < 0) throw ElfException("Tabel simbol dinamis tidak ditemukan")
            strOff = v2o(strV)
            symOff = v2o(symV)
            symCount = countSymbols(hashV, gnuV)
        }
    }

    // ---------- pembacaan dasar ----------

    private inline fun <T> guard(block: () -> T): T {
        try {
            return block()
        } catch (e: IndexOutOfBoundsException) {
            throw ElfException("Berkas ELF rusak atau terpotong")
        }
    }

    private fun idx(o: Long): Int {
        if (o < 0 || o > Int.MAX_VALUE) throw ElfException("Offset di luar berkas")
        return o.toInt()
    }

    private fun u8(o: Long): Int = data[idx(o)].toInt() and 0xFF
    private fun u16(o: Long): Int = bb.getShort(idx(o)).toInt() and 0xFFFF
    private fun u32(o: Long): Long = bb.getInt(idx(o)).toLong() and 0xFFFFFFFFL
    private fun u64(o: Long): Long = bb.getLong(idx(o))

    private fun cstr(o: Long): String {
        val start = idx(o)
        var end = start
        while (end < data.size && data[end].toInt() != 0 && end - start < 4096) end++
        return String(data, start, end - start, Charsets.UTF_8)
    }

    private fun v2oOrNull(va: Long): Long? {
        for (s in loads) if (va >= s.vaddr && va < s.vaddr + s.filesz) return s.off + (va - s.vaddr)
        return null
    }

    private fun v2o(va: Long): Long =
        v2oOrNull(va) ?: throw ElfException("Alamat 0x${va.toString(16)} di luar segmen yang dimuat")

    private fun dynEntries(): List<Dyn> {
        val out = ArrayList<Dyn>()
        var o = dynOff
        var i = 0
        while (o + 16 <= dynOff + dynSize) {
            val tag = u64(o)
            if (tag == 0L) break
            out.add(Dyn(i, tag, u64(o + 8)))
            o += 16
            i++
        }
        return out
    }

    /** Jumlah simbol di .dynsym: dari section header, atau DT_HASH, atau DT_GNU_HASH. */
    private fun countSymbols(hashV: Long, gnuV: Long): Int {
        val shoff = u64(40)
        val shentsize = u16(58)
        val shnum = u16(60)
        if (shoff > 0 && shentsize >= 64) {
            for (i in 0 until shnum) {
                val s = shoff + i.toLong() * shentsize
                if (s + 64 > data.size) break
                if (u32(s + 4) == 11L && u64(s + 24) == symOff) return (u64(s + 32) / 24).toInt()
            }
        }
        if (hashV >= 0) return u32(v2o(hashV) + 4).toInt()
        if (gnuV >= 0) {
            val g = v2o(gnuV)
            val nbuckets = u32(g).toInt()
            val symoffset = u32(g + 4).toInt()
            val bloom = u32(g + 8).toInt()
            val buckets = g + 16 + bloom * 8L
            val chains = buckets + nbuckets * 4L
            var maxSym = 0
            for (b in 0 until nbuckets) {
                val s = u32(buckets + b * 4L).toInt()
                if (s > maxSym) maxSym = s
            }
            if (maxSym < symoffset) return symoffset
            var i = maxSym
            while (true) {
                val c = u32(chains + (i - symoffset) * 4L)
                if ((c and 1L) != 0L) break
                i++
            }
            return i + 1
        }
        throw ElfException("Jumlah simbol tidak bisa ditentukan")
    }

    // ---------- informasi ----------

    fun soname(): String? = guard {
        dynEntries().firstOrNull { it.tag == 14L }?.let { cstr(strOff + it.value) }
    }

    fun needed(): List<String> = guard {
        dynEntries().filter { it.tag == 1L }.map { cstr(strOff + it.value) }
    }

    /** Fungsi yang didefinisikan dan diekspor (tipe FUNC, bukan undefined). */
    fun functions(): List<ElfFunction> = guard {
        val out = ArrayList<ElfFunction>()
        for (i in 1 until symCount) {
            val s = symOff + i * 24L
            val info = u8(s + 4)
            val shndx = u16(s + 6)
            val value = u64(s + 8)
            val size = u64(s + 16)
            if ((info and 0xF) != 2 || shndx == 0 || value == 0L) continue
            val fo = v2oOrNull(value) ?: continue
            out.add(ElfFunction(cstr(strOff + u32(s)), value, size, fo))
        }
        out
    }

    fun info(): SoInfo = SoInfo(arch, soname(), needed(), functions())

    // ---------- patch fungsi: kembalikan nilai tetap ----------

    private fun le32(v: Long): ByteArray =
        byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /**
     * Kode pengganti awal fungsi. Kalau [keepPad] dan fungsi aslinya dimulai dengan landing pad (bti/paciasp di
     * arm64, endbr64 di x86_64), landing pad itu dipertahankan sebagai `bti c` / `endbr64` supaya tetap aman di
     * perangkat yang memaksa BTI atau CET. paciasp sendiri tidak dipertahankan karena akan menandatangani LR
     * dan membuat `ret` gagal di perangkat PAC.
     */
    private fun buildPatch(fn: ElfFunction, value: Long, keepPad: Boolean): ByteArray {
        val ret = StubElf.retCode(arch, value)
        if (!keepPad) return ret
        val off = fn.fileOffset
        return if (arch == StubArch.ARM64) {
            val first = u32(off)
            if (first in ARM64_LANDING_PADS) le32(0xD503245FL) + ret else ret
        } else {
            val endbr = u8(off) == 0xF3 && u8(off + 1) == 0x0F && u8(off + 2) == 0x1E && u8(off + 3) == 0xFA
            if (endbr) byteArrayOf(0xF3.toByte(), 0x0F, 0x1E, 0xFA.toByte()) + ret else ret
        }
    }

    /** Timpa awal tiap fungsi dengan "return nilai". Fungsi yang tidak muat atau tidak ketemu dilewati dan dilaporkan. */
    fun patchFunctions(funcs: List<StubFunction>): List<PatchItem> = guard {
        val table = functions().groupBy { it.name }
        val out = ArrayList<PatchItem>()
        for (f in funcs) {
            val fn = table[f.name]?.firstOrNull()
            if (fn == null) {
                out.add(PatchItem(f.name, false, "tidak ada di simbol eksport"))
                continue
            }
            if (fn.size == 0L) {
                out.add(PatchItem(f.name, false, "ukuran fungsi tidak diketahui (0), dilewati"))
                continue
            }
            var code = buildPatch(fn, f.value, true)
            var catatan = ""
            if (fn.size < code.size) {
                val tanpaPad = buildPatch(fn, f.value, false)
                if (fn.size >= tanpaPad.size) {
                    code = tanpaPad
                    catatan = " (tanpa landing pad: fungsi terlalu kecil)"
                } else {
                    out.add(PatchItem(f.name, false, "fungsi hanya ${fn.size} byte, butuh ${tanpaPad.size}"))
                    continue
                }
            }
            if (fn.fileOffset + code.size > data.size) {
                out.add(PatchItem(f.name, false, "di luar berkas"))
                continue
            }
            System.arraycopy(code, 0, data, fn.fileOffset.toInt(), code.size)
            out.add(PatchItem(f.name, true, "0x${fn.fileOffset.toString(16)}, ${code.size} byte, return ${f.value}$catatan"))
        }
        out
    }

    // ---------- patch hex ----------

    /** Cari pola; -1 pada pola = wildcard (??). Mengembalikan offset kecocokan. */
    fun findHex(pat: IntArray, limit: Int = Int.MAX_VALUE): List<Int> {
        var first = 0
        while (first < pat.size && pat[first] < 0) first++
        if (first == pat.size) throw IllegalArgumentException("Pola tidak boleh isinya wildcard semua")
        val fb = pat[first].toByte()
        val out = ArrayList<Int>()
        val last = data.size - pat.size
        var i = 0
        while (i <= last && out.size < limit) {
            if (data[i + first] == fb) {
                var ok = true
                var j = 0
                while (j < pat.size) {
                    val p = pat[j]
                    if (p >= 0 && data[i + j] != p.toByte()) { ok = false; break }
                    j++
                }
                if (ok) out.add(i)
            }
            i++
        }
        return out
    }

    /** Ganti pola dengan [rep] (panjang sama; -1 pada [rep] = biarkan byte aslinya). */
    fun applyHex(pat: IntArray, rep: IntArray, all: Boolean): List<Int> {
        if (rep.size != pat.size) {
            throw IllegalArgumentException("Panjang pengganti (${rep.size} byte) harus sama dengan pola (${pat.size} byte)")
        }
        val hits = findHex(pat)
        if (hits.isEmpty()) throw IllegalArgumentException("Pola tidak ditemukan")
        if (!all && hits.size != 1) {
            throw IllegalArgumentException("Ditemukan ${hits.size} kecocokan. Perpanjang pola sampai tepat 1, atau centang \"ganti semua\".")
        }
        for (h in hits) for (j in rep.indices) if (rep[j] >= 0) data[h + j] = rep[j].toByte()
        return hits
    }

    // ---------- dependency (DT_NEEDED) ----------

    /** Ganti nama dependency di tempat. Nama baru tidak boleh lebih panjang dari nama lama. */
    fun renameNeeded(old: String, new: String) = guard {
        val nb = new.toByteArray(Charsets.UTF_8)
        val ob = old.toByteArray(Charsets.UTF_8)
        if (nb.isEmpty() || nb.any { it.toInt() == 0 }) throw IllegalArgumentException("Nama baru tidak valid")
        if (nb.size > ob.size) {
            throw IllegalArgumentException("Nama baru (${nb.size} byte) lebih panjang dari nama lama (${ob.size} byte)")
        }
        val ents = dynEntries()
        val e = ents.firstOrNull { it.tag == 1L && cstr(strOff + it.value) == old }
            ?: throw IllegalArgumentException("Dependency \"$old\" tidak ada")
        val start = strOff + e.value
        val end = start + ob.size
        // string dinamis bisa dipakai bersama (gabung ekor). Kalau iya, mengubahnya merusak nama lain.
        for (d in ents) {
            if ((d.tag == 1L || d.tag == 14L) && d.index != e.index) {
                val p = strOff + d.value
                if (p >= start && p < end) throw IllegalArgumentException("Nama itu dipakai bersama entri lain, tidak aman diubah")
            }
        }
        for (i in 1 until symCount) {
            val p = strOff + u32(symOff + i * 24L)
            if (p >= start && p < end) throw IllegalArgumentException("Nama itu dipakai bersama nama simbol, tidak aman diubah")
        }
        System.arraycopy(nb, 0, data, start.toInt(), nb.size)
        for (k in nb.size until ob.size) data[(start + k).toInt()] = 0
    }

    /** Hapus satu dependency dari tabel DYNAMIC: entri sesudahnya digeser naik, entri terakhir jadi kosong. */
    fun removeNeeded(name: String) = guard {
        val e = dynEntries().firstOrNull { it.tag == 1L && cstr(strOff + it.value) == name }
            ?: throw IllegalArgumentException("Dependency \"$name\" tidak ada")
        val total = (dynSize / 16).toInt()
        val base = dynOff.toInt()
        var i = e.index
        while (i < total - 1) {
            System.arraycopy(data, base + (i + 1) * 16, data, base + i * 16, 16)
            i++
        }
        java.util.Arrays.fill(data, base + (total - 1) * 16, base + total * 16, 0.toByte())
    }

    companion object {
        private val ARM64_LANDING_PADS = setOf(
            0xD503245FL, // bti c
            0xD503249FL, // bti j
            0xD50324DFL, // bti jc
            0xD503233FL, // paciasp
            0xD503237FL, // pacibsp
        )

        private val HEX_PAIR = Regex("^[0-9A-Fa-f]{2}$")

        /** "AA BB ?? CC" atau "AABB??CC". `??` hanya boleh bila [wildcard] true. */
        fun parseHex(text: String, wildcard: Boolean): IntArray {
            val t = text.replace(Regex("[\\s,]"), "")
            if (t.isEmpty() || t.length % 2 != 0) {
                throw IllegalArgumentException("Hex harus berpasangan, 2 digit per byte")
            }
            return IntArray(t.length / 2) { i ->
                val p = t.substring(2 * i, 2 * i + 2)
                when {
                    p == "??" -> if (wildcard) -1 else throw IllegalArgumentException("?? tidak boleh di sini")
                    HEX_PAIR.matches(p) -> p.toInt(16)
                    else -> throw IllegalArgumentException("Hex tidak valid: $p")
                }
            }
        }
    }
}
