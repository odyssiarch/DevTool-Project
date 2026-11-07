package com.devtool.app.core

class PatchPreset(val label: String, val bytes: ByteArray)

/** Patch siap pakai per arsitektur (nilai balik fungsi, ret, nop). */
object PatchPresets {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    fun forMachine(machine: Int, thumb: Boolean): List<PatchPreset> = when (machine) {
        ElfFile.EM_AARCH64 -> listOf(
            PatchPreset("Return true  (mov w0,#1; ret)", b(0x20, 0x00, 0x80, 0x52, 0xC0, 0x03, 0x5F, 0xD6)),
            PatchPreset("Return false / 0  (mov w0,#0; ret)", b(0x00, 0x00, 0x80, 0x52, 0xC0, 0x03, 0x5F, 0xD6)),
            PatchPreset("Return -1  (mov w0,#-1; ret)", b(0x00, 0x00, 0x80, 0x12, 0xC0, 0x03, 0x5F, 0xD6)),
            PatchPreset("Langsung return  (ret)", b(0xC0, 0x03, 0x5F, 0xD6)),
            PatchPreset("NOP  (1 instruksi)", b(0x1F, 0x20, 0x03, 0xD5)),
        )
        ElfFile.EM_ARM -> if (thumb) listOf(
            PatchPreset("Return true  (movs r0,#1; bx lr)", b(0x01, 0x20, 0x70, 0x47)),
            PatchPreset("Return false / 0  (movs r0,#0; bx lr)", b(0x00, 0x20, 0x70, 0x47)),
            PatchPreset("Langsung return  (bx lr)", b(0x70, 0x47)),
            PatchPreset("NOP  (thumb)", b(0x00, 0xBF)),
        ) else listOf(
            PatchPreset("Return true  (mov r0,#1; bx lr)", b(0x01, 0x00, 0xA0, 0xE3, 0x1E, 0xFF, 0x2F, 0xE1)),
            PatchPreset("Return false / 0  (mov r0,#0; bx lr)", b(0x00, 0x00, 0xA0, 0xE3, 0x1E, 0xFF, 0x2F, 0xE1)),
            PatchPreset("Langsung return  (bx lr)", b(0x1E, 0xFF, 0x2F, 0xE1)),
            PatchPreset("NOP  (arm)", b(0x00, 0xF0, 0x20, 0xE3)),
        )
        ElfFile.EM_X86_64, ElfFile.EM_X86 -> listOf(
            PatchPreset("Return true  (mov eax,1; ret)", b(0xB8, 0x01, 0x00, 0x00, 0x00, 0xC3)),
            PatchPreset("Return false / 0  (xor eax,eax; ret)", b(0x31, 0xC0, 0xC3)),
            PatchPreset("Langsung return  (ret)", b(0xC3)),
            PatchPreset("NOP", b(0x90)),
        )
        else -> emptyList()
    }
}

/** Disassembler AArch64 untuk instruksi yang umum dijumpai saat patch; sisanya ditampilkan sebagai .inst. */
object Arm64 {
    private val CONDS = arrayOf("eq", "ne", "cs", "cc", "mi", "pl", "vs", "vc", "hi", "ls", "ge", "lt", "gt", "le", "al", "nv")
    private val SHIFTS = arrayOf("lsl", "lsr", "asr", "ror")

    private fun sx(v: Int, bits: Int): Int = (v shl (32 - bits)) shr (32 - bits)

    private fun rg(n: Int, x: Boolean, sp: Boolean = false): String =
        if (n == 31) (if (sp) "sp" else if (x) "xzr" else "wzr") else (if (x) "x" else "w") + n

    private fun hx(v: Long): String = "0x" + java.lang.Long.toHexString(v)

    private fun imm(v: Long): String = if (v < 0) "#-" + hx(-v) else "#" + hx(v)

    fun word(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8) or
            ((d[o + 2].toInt() and 0xFF) shl 16) or ((d[o + 3].toInt() and 0xFF) shl 24)

    private fun bitmask(sf: Int, n: Int, immr: Int, imms: Int): Long? {
        val x = (n shl 6) or (imms.inv() and 0x3F)
        if (x == 0) return null
        val len = 31 - Integer.numberOfLeadingZeros(x)
        if (len < 1) return null
        if (sf == 0 && n == 1) return null
        val esize = 1 shl len
        val levels = esize - 1
        val s = imms and levels
        val r = immr and levels
        if (s == levels) return null
        val welem = (1L shl (s + 1)) - 1
        val mask = if (esize == 64) -1L else (1L shl esize) - 1
        val rotated = if (r == 0) welem else ((welem ushr r) or (welem shl (esize - r))) and mask
        var res = 0L
        var p = 0
        val datasize = if (sf == 1) 64 else 32
        while (p < datasize) {
            res = res or (rotated shl p)
            p += esize
        }
        return if (sf == 1) res else res and 0xFFFFFFFFL
    }

    /** Nama instruksi load/store dan apakah register tujuan 64-bit. null = tidak dikenal. */
    private fun ldst(size: Int, opc: Int, unscaled: Boolean): Pair<String, Boolean>? {
        val u = if (unscaled) "u" else ""
        return when (opc) {
            0 -> when (size) {
                0 -> "st${u}rb" to false
                1 -> "st${u}rh" to false
                2 -> "st${u}r" to false
                else -> "st${u}r" to true
            }
            1 -> when (size) {
                0 -> "ld${u}rb" to false
                1 -> "ld${u}rh" to false
                2 -> "ld${u}r" to false
                else -> "ld${u}r" to true
            }
            2 -> when (size) {
                0 -> "ld${u}rsb" to true
                1 -> "ld${u}rsh" to true
                2 -> "ld${u}rsw" to true
                else -> null
            }
            else -> when (size) {
                0 -> "ld${u}rsb" to false
                1 -> "ld${u}rsh" to false
                else -> null
            }
        }
    }

    fun disasm(w: Int, pc: Long): String {
        if (w == 0xD503201F.toInt()) return "nop"
        val m31 = w and 0xFFFFFC1F.toInt()
        if (m31 == 0xD65F0000.toInt()) {
            val n = (w shr 5) and 31
            return if (n == 30) "ret" else "ret x$n"
        }
        if (m31 == 0xD61F0000.toInt()) return "br x${(w shr 5) and 31}"
        if (m31 == 0xD63F0000.toInt()) return "blr x${(w shr 5) and 31}"
        if ((w and 0xFFE0001F.toInt()) == 0xD4000001.toInt()) return "svc #" + hx(((w shr 5) and 0xFFFF).toLong())
        if ((w and 0xFFE0001F.toInt()) == 0xD4200000.toInt()) return "brk #" + hx(((w shr 5) and 0xFFFF).toLong())

        val top6 = w and 0xFC000000.toInt()
        if (top6 == 0x14000000 || top6 == 0x94000000.toInt()) {
            val t = pc + (sx(w and 0x3FFFFFF, 26).toLong() shl 2)
            return (if (top6 == 0x14000000) "b " else "bl ") + hx(t)
        }
        if ((w and 0xFF000010.toInt()) == 0x54000000) {
            val t = pc + (sx((w shr 5) and 0x7FFFF, 19).toLong() shl 2)
            return "b.${CONDS[w and 15]} " + hx(t)
        }
        if ((w and 0x7E000000) == 0x34000000) {
            val x = (w ushr 31) == 1
            val nz = (w shr 24) and 1
            val t = pc + (sx((w shr 5) and 0x7FFFF, 19).toLong() shl 2)
            return (if (nz == 1) "cbnz " else "cbz ") + rg(w and 31, x) + ", " + hx(t)
        }
        if ((w and 0x7E000000) == 0x36000000) {
            val bit = ((w ushr 31) shl 5) or ((w shr 19) and 31)
            val nz = (w shr 24) and 1
            val t = pc + (sx((w shr 5) and 0x3FFF, 14).toLong() shl 2)
            return (if (nz == 1) "tbnz " else "tbz ") + rg(w and 31, bit >= 32) + ", #$bit, " + hx(t)
        }
        // MOVN / MOVZ / MOVK
        if ((w and 0x1F800000) == 0x12800000) {
            val sf = w ushr 31
            val opc = (w shr 29) and 3
            val hw = (w shr 21) and 3
            if (!(sf == 0 && hw > 1) && opc != 1) {
                val i16 = ((w shr 5) and 0xFFFF).toLong()
                val sh = hw * 16
                val rd = rg(w and 31, sf == 1)
                return when (opc) {
                    0 -> {
                        val v = (i16 shl sh).inv() and (if (sf == 1) -1L else 0xFFFFFFFFL)
                        "mov $rd, " + imm(if (sf == 1) v else v.toInt().toLong())
                    }
                    2 -> "mov $rd, " + imm(i16 shl sh)
                    else -> "movk $rd, #" + hx(i16) + (if (sh > 0) ", lsl #$sh" else "")
                }
            }
        }
        // ADD / SUB (immediate)
        if ((w and 0x1F000000) == 0x11000000) {
            val x = (w ushr 31) == 1
            val op = (w shr 30) and 1
            val s = (w shr 29) and 1
            val sh = (w shr 22) and 1
            val v = (((w shr 10) and 0xFFF) shl (12 * sh)).toLong()
            val rn = (w shr 5) and 31
            val rd = w and 31
            if (op == 1 && s == 1 && rd == 31) return "cmp " + rg(rn, x, true) + ", #" + hx(v)
            if (op == 0 && s == 1 && rd == 31) return "cmn " + rg(rn, x, true) + ", #" + hx(v)
            if (op == 0 && s == 0 && v == 0L && (rd == 31 || rn == 31)) return "mov " + rg(rd, x, true) + ", " + rg(rn, x, true)
            val name = if (op == 0) (if (s == 1) "adds" else "add") else (if (s == 1) "subs" else "sub")
            return "$name " + rg(rd, x, s == 0) + ", " + rg(rn, x, true) + ", #" + hx(v)
        }
        // Logical (shifted register)
        if ((w and 0x1F000000) == 0x0A000000) {
            val x = (w ushr 31) == 1
            val opc = (w shr 29) and 3
            val n = (w shr 21) and 1
            val sh = (w shr 22) and 3
            val rm = (w shr 16) and 31
            val i6 = (w shr 10) and 63
            val rn = (w shr 5) and 31
            val rd = w and 31
            if (opc == 1 && n == 0 && rn == 31 && sh == 0 && i6 == 0) return "mov " + rg(rd, x) + ", " + rg(rm, x)
            if (opc == 3 && n == 0 && rd == 31) return "tst " + rg(rn, x) + ", " + rg(rm, x)
            val name = when (opc) {
                0 -> if (n == 1) "bic" else "and"
                1 -> if (n == 1) "orn" else "orr"
                2 -> if (n == 1) "eon" else "eor"
                else -> if (n == 1) "bics" else "ands"
            }
            return "$name " + rg(rd, x) + ", " + rg(rn, x) + ", " + rg(rm, x) +
                (if (i6 != 0) ", ${SHIFTS[sh]} #$i6" else "")
        }
        // ADD / SUB (shifted register)
        if ((w and 0x1F200000) == 0x0B000000) {
            val x = (w ushr 31) == 1
            val op = (w shr 30) and 1
            val s = (w shr 29) and 1
            val sh = (w shr 22) and 3
            val rm = (w shr 16) and 31
            val i6 = (w shr 10) and 63
            val rn = (w shr 5) and 31
            val rd = w and 31
            if (op == 1 && s == 1 && rd == 31) return "cmp " + rg(rn, x) + ", " + rg(rm, x)
            if (op == 0 && s == 1 && rd == 31) return "cmn " + rg(rn, x) + ", " + rg(rm, x)
            if (op == 1 && s == 0 && rn == 31) return "neg " + rg(rd, x) + ", " + rg(rm, x)
            val name = if (op == 0) (if (s == 1) "adds" else "add") else (if (s == 1) "subs" else "sub")
            return "$name " + rg(rd, x) + ", " + rg(rn, x) + ", " + rg(rm, x) +
                (if (i6 != 0) ", ${SHIFTS[sh]} #$i6" else "")
        }
        // Logical (immediate)
        if ((w and 0x1F800000) == 0x12000000) {
            val sf = w ushr 31
            val opc = (w shr 29) and 3
            val bm = bitmask(sf, (w shr 22) and 1, (w shr 16) and 63, (w shr 10) and 63)
            if (bm != null) {
                val x = sf == 1
                val rn = (w shr 5) and 31
                val rd = w and 31
                if (opc == 1 && rn == 31) return "mov " + rg(rd, x, true) + ", #" + hx(bm)
                if (opc == 3 && rd == 31) return "tst " + rg(rn, x) + ", #" + hx(bm)
                val name = arrayOf("and", "orr", "eor", "ands")[opc]
                return "$name " + rg(rd, x, opc != 3) + ", " + rg(rn, x) + ", #" + hx(bm)
            }
        }
        // SBFM / BFM / UBFM
        if ((w and 0x1F800000) == 0x13000000) {
            val sf = w ushr 31
            val x = sf == 1
            val opc = (w shr 29) and 3
            val immr = (w shr 16) and 63
            val imms = (w shr 10) and 63
            val rn = (w shr 5) and 31
            val rd = w and 31
            val dm1 = if (x) 63 else 31
            val ds = dm1 + 1
            if (opc == 2) {
                if (imms == dm1) return "lsr " + rg(rd, x) + ", " + rg(rn, x) + ", #$immr"
                if (imms + 1 == immr) return "lsl " + rg(rd, x) + ", " + rg(rn, x) + ", #${dm1 - imms}"
                if (immr == 0 && imms == 7) return "uxtb " + rg(rd, false) + ", " + rg(rn, false)
                if (immr == 0 && imms == 15) return "uxth " + rg(rd, false) + ", " + rg(rn, false)
                return if (imms >= immr) "ubfx " + rg(rd, x) + ", " + rg(rn, x) + ", #$immr, #${imms - immr + 1}"
                else "ubfiz " + rg(rd, x) + ", " + rg(rn, x) + ", #${ds - immr}, #${imms + 1}"
            }
            if (opc == 0) {
                if (imms == dm1) return "asr " + rg(rd, x) + ", " + rg(rn, x) + ", #$immr"
                if (immr == 0 && imms == 31 && x) return "sxtw " + rg(rd, true) + ", " + rg(rn, false)
                if (immr == 0 && imms == 7) return "sxtb " + rg(rd, x) + ", " + rg(rn, false)
                if (immr == 0 && imms == 15) return "sxth " + rg(rd, x) + ", " + rg(rn, false)
                return if (imms >= immr) "sbfx " + rg(rd, x) + ", " + rg(rn, x) + ", #$immr, #${imms - immr + 1}"
                else "sbfiz " + rg(rd, x) + ", " + rg(rn, x) + ", #${ds - immr}, #${imms + 1}"
            }
            return "bfm " + rg(rd, x) + ", " + rg(rn, x) + ", #$immr, #$imms"
        }
        // LDR / STR (unsigned offset)
        if ((w and 0x3B000000) == 0x39000000) {
            val size = w ushr 30
            val v = (w shr 26) and 1
            val opc = (w shr 22) and 3
            val off = (((w shr 10) and 0xFFF) shl size).toLong()
            val rn = rg((w shr 5) and 31, true, true)
            val rt = w and 31
            if (v == 0) {
                val nm = ldst(size, opc, false)
                if (nm != null) {
                    return nm.first + " " + rg(rt, nm.second) + ", [" + rn + (if (off != 0L) ", #" + hx(off) else "") + "]"
                }
            }
        }
        // LDR / STR (unscaled, post-index, pre-index)
        if ((w and 0x3B200000) == 0x38000000 && ((w shr 10) and 3) != 2) {
            val size = w ushr 30
            val v = (w shr 26) and 1
            val opc = (w shr 22) and 3
            val idx = (w shr 10) and 3
            val i9 = sx((w shr 12) and 0x1FF, 9).toLong()
            val rn = rg((w shr 5) and 31, true, true)
            val rt = w and 31
            if (v == 0) {
                val nm = ldst(size, opc, idx == 0)
                if (nm != null) {
                    val addr = when (idx) {
                        1 -> "[$rn], " + imm(i9)
                        3 -> "[$rn, " + imm(i9) + "]!"
                        else -> "[$rn" + (if (i9 != 0L) ", " + imm(i9) else "") + "]"
                    }
                    return nm.first + " " + rg(rt, nm.second) + ", " + addr
                }
            }
        }
        // LDR / STR (register offset)
        if ((w and 0x3B200C00) == 0x38200800) {
            val size = w ushr 30
            val v = (w shr 26) and 1
            val opc = (w shr 22) and 3
            val rm = (w shr 16) and 31
            val opt = (w shr 13) and 7
            val s = (w shr 12) and 1
            val rn = rg((w shr 5) and 31, true, true)
            val rt = w and 31
            if (v == 0) {
                val nm = ldst(size, opc, false)
                if (nm != null) {
                    val ext = when (opt) {
                        2 -> "uxtw"
                        3 -> "lsl"
                        6 -> "sxtw"
                        7 -> "sxtx"
                        else -> ""
                    }
                    if (ext.isNotEmpty()) {
                        val tail = if (s == 1) " #$size" else if (opt == 3) "" else ""
                        val extText = if (opt == 3 && s == 0) "" else ", $ext$tail"
                        return nm.first + " " + rg(rt, nm.second) + ", [" + rn + ", " + rg(rm, (opt and 1) == 1) + extText + "]"
                    }
                }
            }
        }
        // LDR (literal)
        if ((w and 0x3B000000) == 0x18000000 && ((w shr 26) and 1) == 0) {
            val opc = w ushr 30
            val t = pc + (sx((w shr 5) and 0x7FFFF, 19).toLong() shl 2)
            val rt = w and 31
            when (opc) {
                0 -> return "ldr " + rg(rt, false) + ", " + hx(t)
                1 -> return "ldr " + rg(rt, true) + ", " + hx(t)
                2 -> return "ldrsw " + rg(rt, true) + ", " + hx(t)
            }
        }
        // LDP / STP
        if ((w and 0x3E000000) == 0x28000000 && ((w shr 26) and 1) == 0) {
            val opc = w ushr 30
            val mode = (w shr 23) and 3
            val l = (w shr 22) and 1
            if (opc != 3 && mode != 0 && !(opc == 1 && l == 0)) {
                val x = opc == 2
                val scale = if (opc == 0 || opc == 1) 2 else 3
                val off = (sx((w shr 15) and 0x7F, 7) shl scale).toLong()
                val rt2 = (w shr 10) and 31
                val rn = rg((w shr 5) and 31, true, true)
                val rt = w and 31
                val name = if (l == 1) (if (opc == 1) "ldpsw" else "ldp") else "stp"
                val rx = x || opc == 1
                val addr = when (mode) {
                    1 -> "[$rn], " + imm(off)
                    3 -> "[$rn, " + imm(off) + "]!"
                    else -> "[$rn" + (if (off != 0L) ", " + imm(off) else "") + "]"
                }
                return "$name " + rg(rt, rx) + ", " + rg(rt2, rx) + ", " + addr
            }
        }
        // ADRP / ADR
        if ((w and 0x9F000000.toInt()) == 0x90000000.toInt() || (w and 0x9F000000.toInt()) == 0x10000000) {
            val isPage = (w ushr 31) == 1
            val immlo = (w shr 29) and 3
            val immhi = (w shr 5) and 0x7FFFF
            val v = sx((immhi shl 2) or immlo, 21).toLong()
            val rd = rg(w and 31, true)
            return if (isPage) "adrp $rd, " + hx((pc and 0xFFFL.inv()) + (v shl 12)) else "adr $rd, " + hx(pc + v)
        }
        // CSEL / CSINC / CSINV / CSNEG
        if ((w and 0x1FE00000) == 0x1A800000) {
            val op = (w shr 30) and 1
            val op2 = (w shr 10) and 3
            if (op2 <= 1) {
                val x = (w ushr 31) == 1
                val rm = (w shr 16) and 31
                val cond = (w shr 12) and 15
                val rn = (w shr 5) and 31
                val rd = w and 31
                if (op == 0 && op2 == 1 && rn == 31 && rm == 31 && cond < 14) return "cset " + rg(rd, x) + ", " + CONDS[cond xor 1]
                if (op == 1 && op2 == 0 && rn == 31 && rm == 31 && cond < 14) return "csetm " + rg(rd, x) + ", " + CONDS[cond xor 1]
                val name = if (op == 0) (if (op2 == 0) "csel" else "csinc") else (if (op2 == 0) "csinv" else "csneg")
                return "$name " + rg(rd, x) + ", " + rg(rn, x) + ", " + rg(rm, x) + ", " + CONDS[cond]
            }
        }
        // MADD / MSUB
        if ((w and 0x1FE00000) == 0x1B000000) {
            val x = (w ushr 31) == 1
            val sub = (w shr 15) and 1
            val rm = (w shr 16) and 31
            val ra = (w shr 10) and 31
            val rn = (w shr 5) and 31
            val rd = w and 31
            if (ra == 31) return (if (sub == 1) "mneg " else "mul ") + rg(rd, x) + ", " + rg(rn, x) + ", " + rg(rm, x)
            return (if (sub == 1) "msub " else "madd ") + rg(rd, x) + ", " + rg(rn, x) + ", " + rg(rm, x) + ", " + rg(ra, x)
        }
        // UDIV / SDIV / shift variabel
        if ((w and 0x7FE00000) == 0x1AC00000) {
            val x = (w ushr 31) == 1
            val name = when ((w shr 10) and 0x3F) {
                2 -> "udiv"
                3 -> "sdiv"
                8 -> "lsl"
                9 -> "lsr"
                10 -> "asr"
                11 -> "ror"
                else -> null
            }
            if (name != null) {
                return "$name " + rg(w and 31, x) + ", " + rg((w shr 5) and 31, x) + ", " + rg((w shr 16) and 31, x)
            }
        }
        return ".inst 0x" + String.format("%08x", w)
    }

    /** Daftar baris: alamat, byte, dan instruksi. */
    fun listing(d: ByteArray, off: Int, count: Int, vaddr: Long): List<String> {
        val out = ArrayList<String>()
        var o = off
        var pc = vaddr
        for (i in 0 until count) {
            if (o + 4 > d.size) break
            val w = word(d, o)
            out.add(
                String.format(
                    "%x:  %02X %02X %02X %02X   %s", pc,
                    d[o].toInt() and 0xFF, d[o + 1].toInt() and 0xFF,
                    d[o + 2].toInt() and 0xFF, d[o + 3].toInt() and 0xFF,
                    disasm(w, pc),
                ),
            )
            o += 4
            pc += 4
        }
        return out
    }
}
