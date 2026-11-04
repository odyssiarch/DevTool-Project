package com.devtool.app.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import kotlin.math.abs

/**
 * Menyusun beberapa dex biasa (header 0x70, versi 035 sampai 040) menjadi satu
 * kontainer dex 041. Tiap dex tetap membawa data sendiri, jadi yang dilakukan
 * hanya menggeser semua offset: header membesar 8 byte (container_size dan
 * header_offset), dan dex ke-2 dst. diletakkan setelah dex sebelumnya.
 *
 * Satu-satunya data yang berubah panjang adalah class_data_item, karena code_off
 * ditulis dengan ULEB128. Bagian itu ditulis ulang, lalu bagian sesudahnya digeser.
 */
object ContainerPacker {
    private const val OLD_H = 0x70
    private const val NEW_H = 0x78

    private class Rd(val b: ByteArray, var p: Int) {
        fun u8(): Int = b[p++].toInt() and 0xFF

        fun uleb(): Int {
            var r = 0
            var s = 0
            var x: Int
            do {
                x = u8()
                r = r or ((x and 0x7F) shl s)
                s += 7
            } while ((x and 0x80) != 0)
            return r
        }

        fun sleb(): Int {
            var r = 0
            var s = 0
            var x: Int
            do {
                x = u8()
                r = r or ((x and 0x7F) shl s)
                s += 7
            } while ((x and 0x80) != 0)
            if (s < 32 && (x and 0x40) != 0) r = r or (-1 shl s)
            return r
        }
    }

    private class Entry(val type: Int, val size: Int, val off: Int)

    private fun wu(out: ByteArrayOutputStream, v: Int) {
        var x = v
        while (true) {
            val b = x and 0x7F
            x = x ushr 7
            if (x == 0) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    /** Gabungkan [parts] (urutan = urutan dex di kontainer) menjadi satu berkas kontainer 041. */
    fun pack(parts: List<File>, out: File) {
        require(parts.isNotEmpty()) { "Tidak ada dex untuk dikemas" }
        val chunks = ArrayList<ByteArray>()
        val starts = ArrayList<Int>()
        var at = 0
        for (f in parts) {
            val c = relocate(f.readBytes(), at)
            starts.add(at)
            chunks.add(c)
            at += c.size
        }
        val total = at
        out.parentFile?.mkdirs()
        out.outputStream().buffered().use { os ->
            for ((i, c) in chunks.withIndex()) {
                val b = ByteBuffer.wrap(c).order(ByteOrder.LITTLE_ENDIAN)
                b.putInt(0x70, total)      // container_size
                b.putInt(0x74, starts[i])  // header_offset
                val sha = MessageDigest.getInstance("SHA-1")
                sha.update(c, 32, c.size - 32)
                System.arraycopy(sha.digest(), 0, c, 12, 20)
                val ad = Adler32()
                ad.update(c, 12, c.size - 12)
                b.putInt(8, ad.value.toInt())
                os.write(c)
            }
        }
    }

    /** Ubah satu dex biasa menjadi dex 041 yang diletakkan pada posisi [s] di dalam kontainer. */
    private fun relocate(old: ByteArray, s: Int): ByteArray {
        require(old.size > OLD_H && String(old, 0, 4, Charsets.US_ASCII) == "dex\n") { "Bukan berkas dex" }
        val o = ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN)
        require(o.getInt(0x24) == OLD_H) { "Header dex bukan 0x70, packer hanya menerima dex versi 035 sampai 040" }

        val mapOff = o.getInt(0x34)
        val mapCount = o.getInt(mapOff)
        val entries = List(mapCount) { i ->
            val q = mapOff + 4 + 12 * i
            Entry(o.getShort(q).toInt() and 0xFFFF, o.getInt(q + 4), o.getInt(q + 8))
        }

        val cd = entries.firstOrNull { it.type == 0x2000 && it.size > 0 }
        val cdStart = cd?.off ?: 0
        val cdEnd = if (cd == null) Int.MAX_VALUE
        else (entries.filter { it.off > cd.off }.minOfOrNull { it.off } ?: old.size)

        var delta = 0 // pertambahan panjang bagian class_data

        // offset lama -> offset baru (mutlak dari awal kontainer)
        fun nf(v: Int): Int = if (v == 0) 0 else s + v + 8 + (if (v >= cdEnd) delta else 0)
        // posisi lama -> posisi baru di dalam buffer dex ini
        fun np(oldPos: Int): Int = oldPos + 8 + (if (oldPos >= cdEnd) delta else 0)

        fun buildClassData(): Pair<ByteArray, HashMap<Int, Int>> {
            val out = ByteArrayOutputStream(cdEnd - cdStart + 1024)
            val map = HashMap<Int, Int>()
            val rd = Rd(old, cdStart)
            repeat(cd!!.size) {
                map[rd.p] = cdStart + 8 + out.size()
                val sf = rd.uleb()
                val inf = rd.uleb()
                val dm = rd.uleb()
                val vm = rd.uleb()
                wu(out, sf); wu(out, inf); wu(out, dm); wu(out, vm)
                repeat(sf + inf) { wu(out, rd.uleb()); wu(out, rd.uleb()) }
                repeat(dm + vm) {
                    wu(out, rd.uleb())
                    wu(out, rd.uleb())
                    val c = rd.uleb()
                    wu(out, if (c == 0) 0 else nf(c))
                }
            }
            return out.toByteArray() to map
        }

        var cdBytes = ByteArray(0)
        var cdMap = HashMap<Int, Int>()
        if (cd != null) {
            val oldLen = cdEnd - cdStart
            while (true) {
                val (bytes, m) = buildClassData()
                cdBytes = bytes
                cdMap = m
                val need = bytes.size - oldLen
                val d = need + Math.floorMod(-need, 4) // naik ke kelipatan 4 supaya perataan bagian sesudahnya tetap
                if (d <= delta) break
                delta = d
            }
        }

        val total = (8 + old.size + delta + 3) and 3.inv()
        val nb = ByteArray(total)
        System.arraycopy(old, 0, nb, 0, OLD_H)
        if (cd == null) {
            System.arraycopy(old, OLD_H, nb, NEW_H, old.size - OLD_H)
        } else {
            System.arraycopy(old, OLD_H, nb, NEW_H, cdStart - OLD_H)
            System.arraycopy(cdBytes, 0, nb, cdStart + 8, cdBytes.size)
            System.arraycopy(old, cdEnd, nb, cdEnd + 8 + delta, old.size - cdEnd)
        }
        val n = ByteBuffer.wrap(nb).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        nb[4] = '0'.code.toByte(); nb[5] = '4'.code.toByte(); nb[6] = '1'.code.toByte()
        n.putInt(0x20, total)
        n.putInt(0x24, NEW_H)
        for (off in intArrayOf(0x30, 0x34, 0x3C, 0x44, 0x4C, 0x54, 0x5C, 0x64)) n.putInt(off, nf(o.getInt(off)))
        n.putInt(0x68, o.getInt(0x68) + delta)
        n.putInt(0x6C, nf(o.getInt(0x6C)))

        // Map list
        val mapNew = np(mapOff)
        for (i in entries.indices) {
            val e = entries[i]
            n.putInt(mapNew + 4 + 12 * i + 8, if (e.type == 0x0000) s else nf(e.off))
        }

        fun fix(oldPos: Int) {
            val v = o.getInt(oldPos)
            if (v != 0) n.putInt(np(oldPos), nf(v))
        }

        for (e in entries) {
            when (e.type) {
                0x0001 -> repeat(e.size) { fix(e.off + 4 * it) }                 // string_id: string_data_off
                0x0003 -> repeat(e.size) { fix(e.off + 12 * it + 8) }            // proto_id: parameters_off
                0x0006 -> repeat(e.size) {                                       // class_def
                    val b = e.off + 32 * it
                    fix(b + 12); fix(b + 20); fix(b + 28)
                    val c = o.getInt(b + 24)
                    if (c != 0) {
                        val rel = cdMap[c] ?: throw IllegalStateException("class_data_off tidak dikenal")
                        n.putInt(np(b + 24), s + rel)
                    }
                }
                0x0007 -> repeat(e.size) { fix(e.off + 4 * it) }                 // call_site_id
                0x1002, 0x1003 -> {                                              // annotation_set_ref_list, annotation_set_item
                    var p = e.off
                    repeat(e.size) {
                        val c = o.getInt(p)
                        repeat(c) { j -> fix(p + 4 + 4 * j) }
                        p += 4 + 4 * c
                    }
                }
                0x2006 -> {                                                      // annotations_directory_item
                    var p = e.off
                    repeat(e.size) {
                        p = (p + 3) and 3.inv()
                        fix(p)
                        val cnt = o.getInt(p + 4) + o.getInt(p + 8) + o.getInt(p + 12)
                        var q = p + 16
                        repeat(cnt) { fix(q + 4); q += 8 }
                        p = q
                    }
                }
                0x2001 -> {                                                      // code_item
                    var p = e.off
                    repeat(e.size) {
                        p = (p + 3) and 3.inv()
                        val tries = o.getShort(p + 6).toInt() and 0xFFFF
                        fix(p + 8) // debug_info_off
                        val insns = o.getInt(p + 12)
                        var q = p + 16 + insns * 2
                        if (tries != 0) {
                            if ((insns and 1) == 1) q += 2
                            q += tries * 8
                            val rd = Rd(old, q)
                            val hs = rd.uleb()
                            repeat(hs) {
                                val sz = rd.sleb()
                                repeat(abs(sz)) { rd.uleb(); rd.uleb() }
                                if (sz <= 0) rd.uleb()
                            }
                            q = rd.p
                        }
                        p = q
                    }
                }
            }
        }
        return nb
    }
}
