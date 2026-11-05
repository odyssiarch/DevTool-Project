package com.devtool.app.core

import java.io.ByteArrayOutputStream

/** Arsitektur target stub. */
enum class StubArch(val abi: String, val machine: Int) {
    ARM64("arm64-v8a", 183),
    X86_64("x86_64", 62),
}

/** Satu fungsi stub: nama simbol dan nilai tetap yang dikembalikan (-1 sampai 0x7FFFFFFF). */
data class StubFunction(val name: String, val value: Long = 0L)

/**
 * Penulis ELF langsung (tanpa compiler/NDK) untuk stub.
 *
 *  - library(): berkas .so (ET_DYN) yang mengekspor fungsi-fungsi, masing-masing hanya mengembalikan nilai tetap.
 *    Cocok untuk fungsi biasa maupun fungsi JNI (Java_paket_Kelas_metode) yang bertipe kembalian bilangan/pointer/void.
 *  - executable(): binary statis (ET_EXEC) yang opsional mencetak pesan lalu keluar dengan kode tertentu.
 *
 * Tata letak library: satu segmen R+X (header, hash, simbol, teks) dan satu segmen RW (.dynamic) yang
 * berbagi halaman berkas dengan segmen pertama. Perataan 0x4000 supaya aman di perangkat halaman 16 KB.
 * Section header sengaja disertakan karena linker Android menolak ELF tanpa section header.
 */
object StubElf {
    private const val LIB_ALIGN = 0x4000L
    private const val EXE_BASE = 0x400000L
    private const val MAX_FUNCS = 2000
    private const val MAX_MESSAGE = 1000

    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    // ---------- penulis byte little-endian ----------

    private class Le {
        private val o = ByteArrayOutputStream()
        val size: Int get() = o.size()
        fun u8(v: Int) { o.write(v and 0xFF) }
        fun u16(v: Int) { u8(v); u8(v ushr 8) }
        fun u32(v: Long) { for (i in 0..3) u8(((v ushr (8 * i)) and 0xFF).toInt()) }
        fun u64(v: Long) { for (i in 0..7) u8(((v ushr (8 * i)) and 0xFF).toInt()) }
        fun bytes(b: ByteArray) { o.write(b, 0, b.size) }
        fun zeros(n: Int) { for (i in 0 until n) u8(0) }
        fun padTo(pos: Int) { zeros(pos - size) }
        fun toByteArray(): ByteArray = o.toByteArray()
    }

    private fun align(v: Int, a: Int): Int = (v + a - 1) / a * a

    // ---------- validasi dan penguraian masukan ----------

    fun validName(name: String): Boolean = NAME.matches(name)

    /**
     * Uraikan daftar fungsi, satu per baris: `nama` atau `nama=nilai`.
     * Nilai: bilangan desimal, 0x..., true (1), false (0), null (0). Baris kosong dan yang diawali # diabaikan.
     */
    fun parseFunctions(text: String): List<StubFunction> {
        val out = ArrayList<StubFunction>()
        val seen = HashSet<String>()
        text.lines().forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val parts = line.split("=", limit = 2)
            val name = parts[0].trim()
            if (!validName(name)) {
                throw IllegalArgumentException("Baris ${idx + 1}: nama \"$name\" tidak valid (huruf, angka, garis bawah; tidak boleh diawali angka)")
            }
            if (!seen.add(name)) throw IllegalArgumentException("Baris ${idx + 1}: nama \"$name\" dobel")
            val value = if (parts.size == 1) 0L else parseValue(parts[1].trim(), idx + 1)
            out.add(StubFunction(name, value))
        }
        if (out.isEmpty()) throw IllegalArgumentException("Daftar fungsi masih kosong")
        if (out.size > MAX_FUNCS) throw IllegalArgumentException("Terlalu banyak fungsi (maksimal $MAX_FUNCS)")
        return out
    }

    private fun parseValue(s: String, line: Int): Long {
        val v: Long? = when (s.lowercase()) {
            "true" -> 1L
            "false", "null" -> 0L
            else -> if (s.lowercase().startsWith("0x")) s.substring(2).toLongOrNull(16) else s.toLongOrNull()
        }
        if (v == null || v < -1L || v > 0x7FFFFFFFL) {
            throw IllegalArgumentException("Baris $line: nilai \"$s\" tidak valid (boleh -1 sampai 2147483647)")
        }
        return v
    }

    // ---------- kode mesin ----------

    private fun slot(arch: StubArch): Int = if (arch == StubArch.ARM64) 4 else 16
    private fun padByte(arch: StubArch): Int = if (arch == StubArch.ARM64) 0x00 else 0xCC

    /** Kode fungsi: kembalikan [value] di register hasil lalu ret. */
    private fun retCode(arch: StubArch, value: Long): ByteArray {
        val w = Le()
        if (arch == StubArch.ARM64) {
            if (value == -1L) {
                w.u32(0x92800000L)                              // movn x0, #0
            } else {
                val lo = value and 0xFFFFL
                val hi = (value shr 16) and 0xFFFFL
                w.u32(0xD2800000L or (lo shl 5))                // movz x0, #lo
                if (hi != 0L) w.u32(0xF2A00000L or (hi shl 5))  // movk x0, #hi, lsl 16
            }
            w.u32(0xD65F03C0L)                                  // ret
        } else {
            w.u8(0x48); w.u8(0xC7); w.u8(0xC0)                  // mov rax, imm32 (sign-extend)
            w.u32(value and 0xFFFFFFFFL)
            w.u8(0xC3)                                          // ret
        }
        return w.toByteArray()
    }

    // ---------- library (.so) ----------

    fun library(arch: StubArch, soname: String, funcs: List<StubFunction>): ByteArray {
        require(funcs.isNotEmpty()) { "Daftar fungsi masih kosong" }
        require(soname.isNotEmpty() && !soname.contains('/') && !soname.contains('\u0000')) { "Nama library tidak valid" }

        val n = funcs.size
        val nsyms = n + 1
        val phnum = 4
        val hashOff = align(64 + phnum * 56, 8)
        val hashSize = 4 * (2 + nsyms)
        val symOff = align(hashOff + hashSize, 8)
        val symSize = 24 * nsyms
        val strOff = symOff + symSize

        val strtab = Le()
        strtab.u8(0)
        val sonameIdx = strtab.size
        strtab.bytes(soname.toByteArray(Charsets.UTF_8)); strtab.u8(0)
        val nameIdx = IntArray(n)
        for (i in 0 until n) {
            nameIdx[i] = strtab.size
            strtab.bytes(funcs[i].name.toByteArray(Charsets.US_ASCII)); strtab.u8(0)
        }
        val strBytes = strtab.toByteArray()

        val textOff = align(strOff + strBytes.size, 16)
        val text = Le()
        val funcOff = IntArray(n)
        val funcLen = IntArray(n)
        for (i in 0 until n) {
            while (text.size % slot(arch) != 0) text.u8(padByte(arch))
            val c = retCode(arch, funcs[i].value)
            funcOff[i] = text.size
            funcLen[i] = c.size
            text.bytes(c)
        }
        val textBytes = text.toByteArray()

        val dynOff = align(textOff + textBytes.size, 8)
        val dynSize = 16 * 7
        val vdyn = LIB_ALIGN + dynOff

        val shNames = listOf(".hash", ".dynsym", ".dynstr", ".text", ".dynamic", ".shstrtab")
        val shstr = Le()
        shstr.u8(0)
        val shNameIdx = HashMap<String, Int>()
        for (nm in shNames) {
            shNameIdx[nm] = shstr.size
            shstr.bytes(nm.toByteArray(Charsets.US_ASCII)); shstr.u8(0)
        }
        val shstrBytes = shstr.toByteArray()
        val shstrOff = dynOff + dynSize
        val shOff = align(shstrOff + shstrBytes.size, 8)
        val shnum = 7
        val total = shOff + shnum * 64
        val loadEnd = dynOff.toLong()

        val w = Le()
        // header ELF
        w.u8(0x7F); w.u8('E'.code); w.u8('L'.code); w.u8('F'.code)
        w.u8(2); w.u8(1); w.u8(1); w.u8(0)               // 64-bit, little-endian, versi 1, System V
        w.zeros(8)
        w.u16(3)                                          // ET_DYN
        w.u16(arch.machine)
        w.u32(1L)
        w.u64(0L)                                         // entry
        w.u64(64L)                                        // e_phoff
        w.u64(shOff.toLong())                             // e_shoff
        w.u32(0L)                                         // flags
        w.u16(64); w.u16(56); w.u16(phnum); w.u16(64); w.u16(shnum); w.u16(6)

        fun ph(type: Long, flags: Long, off: Long, va: Long, fileSz: Long, memSz: Long, al: Long) {
            w.u32(type); w.u32(flags); w.u64(off); w.u64(va); w.u64(va); w.u64(fileSz); w.u64(memSz); w.u64(al)
        }
        ph(1L, 5L, 0L, 0L, loadEnd, loadEnd, LIB_ALIGN)                                       // LOAD R+X
        ph(1L, 6L, dynOff.toLong(), vdyn, dynSize.toLong(), dynSize.toLong(), LIB_ALIGN)      // LOAD RW (.dynamic)
        ph(2L, 6L, dynOff.toLong(), vdyn, dynSize.toLong(), dynSize.toLong(), 8L)             // DYNAMIC
        ph(0x6474E551L, 6L, 0L, 0L, 0L, 0L, 16L)                                              // GNU_STACK

        // .hash: satu bucket, rantai berurutan (cukup untuk pencarian simbol)
        w.padTo(hashOff)
        w.u32(1L); w.u32(nsyms.toLong()); w.u32(1L)
        w.u32(0L)
        for (i in 1 until nsyms) w.u32(if (i < n) (i + 1).toLong() else 0L)

        // .dynsym
        w.padTo(symOff)
        w.zeros(24)
        for (i in 0 until n) {
            w.u32(nameIdx[i].toLong())
            w.u8(0x12); w.u8(0)                           // GLOBAL FUNC, visibilitas default
            w.u16(4)                                      // indeks section .text
            w.u64((textOff + funcOff[i]).toLong())
            w.u64(funcLen[i].toLong())
        }

        // .dynstr
        w.bytes(strBytes)

        // .text
        w.padTo(textOff)
        w.bytes(textBytes)

        // .dynamic
        w.padTo(dynOff)
        fun dyn(tag: Long, v: Long) { w.u64(tag); w.u64(v) }
        dyn(4L, hashOff.toLong())          // DT_HASH
        dyn(5L, strOff.toLong())           // DT_STRTAB
        dyn(6L, symOff.toLong())           // DT_SYMTAB
        dyn(10L, strBytes.size.toLong())   // DT_STRSZ
        dyn(11L, 24L)                      // DT_SYMENT
        dyn(14L, sonameIdx.toLong())       // DT_SONAME
        dyn(0L, 0L)                        // DT_NULL

        // .shstrtab dan tabel section header
        w.bytes(shstrBytes)
        w.padTo(shOff)
        fun sh(name: String, type: Long, flags: Long, addr: Long, off: Long, size: Long, link: Long, info: Long, al: Long, ent: Long) {
            w.u32((shNameIdx[name] ?: 0).toLong()); w.u32(type); w.u64(flags); w.u64(addr); w.u64(off); w.u64(size)
            w.u32(link); w.u32(info); w.u64(al); w.u64(ent)
        }
        w.zeros(64)
        sh(".hash", 5L, 2L, hashOff.toLong(), hashOff.toLong(), hashSize.toLong(), 2L, 0L, 8L, 4L)
        sh(".dynsym", 11L, 2L, symOff.toLong(), symOff.toLong(), symSize.toLong(), 3L, 1L, 8L, 24L)
        sh(".dynstr", 3L, 2L, strOff.toLong(), strOff.toLong(), strBytes.size.toLong(), 0L, 0L, 1L, 0L)
        sh(".text", 1L, 6L, textOff.toLong(), textOff.toLong(), textBytes.size.toLong(), 0L, 0L, 16L, 0L)
        sh(".dynamic", 6L, 3L, vdyn, dynOff.toLong(), dynSize.toLong(), 3L, 0L, 8L, 16L)
        sh(".shstrtab", 3L, 0L, 0L, shstrOff.toLong(), shstrBytes.size.toLong(), 0L, 0L, 1L, 0L)

        check(w.size == total) { "Ukuran ELF tidak cocok (${w.size} dari $total)" }
        return w.toByteArray()
    }

    // ---------- binary (executable statis) ----------

    /** Binary statis: cetak [message] (kalau ada) ke stdout, lalu keluar dengan [exitCode] (0..255). */
    fun executable(arch: StubArch, exitCode: Int, message: String?): ByteArray {
        val msg = (message ?: "").toByteArray(Charsets.UTF_8)
        require(msg.size <= MAX_MESSAGE) { "Pesan terlalu panjang (maksimal $MAX_MESSAGE byte)" }
        val code = (exitCode and 0xFF).toLong()
        val codeOff = 64 + 2 * 56
        val c = Le()

        if (arch == StubArch.ARM64) {
            if (msg.isNotEmpty()) {
                c.u32(0xD2800020L)                                        // mov x0, #1 (stdout)
                val imm = 28L                                             // jarak ke pesan dari instruksi adr
                c.u32(0x10000001L or ((imm and 3L) shl 29) or (((imm shr 2) and 0x7FFFFL) shl 5)) // adr x1, pesan
                c.u32(0xD2800000L or (msg.size.toLong() shl 5) or 2L)     // mov x2, #panjang
                c.u32(0xD2800000L or (64L shl 5) or 8L)                   // mov x8, #64 (write)
                c.u32(0xD4000001L)                                        // svc #0
            }
            c.u32(0xD2800000L or (code shl 5))                            // mov x0, #kode
            c.u32(0xD2800000L or (93L shl 5) or 8L)                       // mov x8, #93 (exit)
            c.u32(0xD4000001L)                                            // svc #0
            c.bytes(msg)
        } else {
            var leaAt = -1
            if (msg.isNotEmpty()) {
                c.u8(0xBF); c.u32(1L)                                     // mov edi, 1 (stdout)
                leaAt = c.size
                c.u8(0x48); c.u8(0x8D); c.u8(0x35); c.u32(0L)             // lea rsi, [rip+disp] (diisi nanti)
                c.u8(0xBA); c.u32(msg.size.toLong())                      // mov edx, panjang
                c.u8(0xB8); c.u32(1L)                                     // mov eax, 1 (write)
                c.u8(0x0F); c.u8(0x05)                                    // syscall
            }
            c.u8(0xBF); c.u32(code)                                       // mov edi, kode
            c.u8(0xB8); c.u32(60L)                                        // mov eax, 60 (exit)
            c.u8(0x0F); c.u8(0x05)                                        // syscall
            c.bytes(msg)
            if (msg.isNotEmpty()) {
                // hitung ulang karena disp perlu tahu posisi pesan; susun ulang dengan disp yang benar
                val body = c.toByteArray()
                val msgPos = body.size - msg.size
                val disp = (msgPos - (leaAt + 7)).toLong()
                for (i in 0..3) body[leaAt + 3 + i] = ((disp ushr (8 * i)) and 0xFF).toByte()
                return wrapExecutable(arch, body, codeOff)
            }
        }
        return wrapExecutable(arch, c.toByteArray(), codeOff)
    }

    private fun wrapExecutable(arch: StubArch, code: ByteArray, codeOff: Int): ByteArray {
        val total = codeOff + code.size
        val w = Le()
        w.u8(0x7F); w.u8('E'.code); w.u8('L'.code); w.u8('F'.code)
        w.u8(2); w.u8(1); w.u8(1); w.u8(0)
        w.zeros(8)
        w.u16(2)                                          // ET_EXEC
        w.u16(arch.machine)
        w.u32(1L)
        w.u64(EXE_BASE + codeOff)                         // entry
        w.u64(64L)
        w.u64(0L)
        w.u32(0L)
        w.u16(64); w.u16(56); w.u16(2); w.u16(64); w.u16(0); w.u16(0)
        // LOAD R+X: seluruh berkas dipetakan di EXE_BASE
        w.u32(1L); w.u32(5L); w.u64(0L); w.u64(EXE_BASE); w.u64(EXE_BASE)
        w.u64(total.toLong()); w.u64(total.toLong()); w.u64(LIB_ALIGN)
        // GNU_STACK
        w.u32(0x6474E551L); w.u32(6L); w.u64(0L); w.u64(0L); w.u64(0L); w.u64(0L); w.u64(0L); w.u64(16L)
        w.bytes(code)
        return w.toByteArray()
    }
}
