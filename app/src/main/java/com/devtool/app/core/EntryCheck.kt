package com.devtool.app.core

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * Verifikasi tingkat entry JAR hasil rebuild terhadap JAR asli:
 * urutan, metode kompresi, tanggal, dan isi (CRC). Entry dex yang diganti hanya
 * dicek metode kompresinya dan keutuhan datanya.
 */
object EntryCheck {

    enum class Level { OK, WARN, FAIL }

    class Item(val level: Level, val text: String) {
        fun render(): String = when (level) {
            Level.OK -> "✅ $text"
            Level.WARN -> "⚠️ $text"
            Level.FAIL -> "❌ $text"
        }
    }

    private fun methodName(m: Int) = if (m == 0) "Stored" else "Deflated"

    fun run(original: File, output: File, replaced: Set<String>): List<Item> {
        val items = ArrayList<Item>()
        ZipFile(original).use { a ->
            ZipFile(output).use { b ->
                val ea = a.entries().toList().map { it.name }
                val eb = b.entries().toList().map { it.name }

                // 1. nama dan urutan
                val missing = ea.filter { it !in eb }
                val extra = eb.filter { it !in ea }
                if (missing.isNotEmpty()) {
                    items.add(Item(Level.FAIL, "Entry hilang di output: ${missing.take(5).joinToString()}"))
                }
                if (extra.isNotEmpty()) {
                    items.add(Item(Level.WARN, "Entry tambahan di output: ${extra.take(5).joinToString()}"))
                }
                if (missing.isEmpty() && eb.take(ea.size) == ea) {
                    items.add(Item(Level.OK, "Urutan ${ea.size} entry sama dengan JAR asli"))
                } else if (missing.isEmpty()) {
                    items.add(Item(Level.WARN, "Urutan entry berbeda dari JAR asli"))
                }

                // 2. per entry
                var methodBad = 0
                var timeBad = 0
                var crcBad = 0
                for (name in ea) {
                    val x = a.getEntry(name) ?: continue
                    val y = b.getEntry(name) ?: continue
                    if (x.isDirectory) continue
                    val isDex = name in replaced

                    if (x.method != y.method) {
                        methodBad++
                        val tag = if (isDex) "(dex)" else ""
                        items.add(
                            Item(
                                Level.FAIL,
                                "$name $tag: kompresi asli ${methodName(x.method)}, output ${methodName(y.method)}",
                            ),
                        )
                    }
                    if (x.time != y.time) {
                        timeBad++
                        if (timeBad <= 3) items.add(Item(Level.WARN, "$name: tanggal entry berbeda dari asli"))
                    }

                    if (!isDex) {
                        if (x.crc != y.crc || x.size != y.size) {
                            crcBad++
                            if (crcBad <= 5) items.add(Item(Level.FAIL, "$name: isi berubah (CRC/ukuran beda)"))
                        }
                    } else {
                        // data dex di output harus utuh: CRC yang tercatat = CRC isi sebenarnya
                        val crc = CRC32()
                        b.getInputStream(y).use { s ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = s.read(buf)
                                if (n < 0) break
                                crc.update(buf, 0, n)
                            }
                        }
                        if (crc.value != y.crc) {
                            items.add(Item(Level.FAIL, "$name: CRC tercatat tidak cocok dengan isi"))
                        } else {
                            items.add(
                                Item(
                                    Level.OK,
                                    "$name: ${methodName(y.method)}, tanggal " +
                                        (if (x.time == y.time) "sama dengan asli" else "berbeda dari asli"),
                                ),
                            )
                        }
                    }
                }
                if (methodBad == 0) items.add(Item(Level.OK, "Metode kompresi semua entry sama dengan asli"))
                if (crcBad == 0) items.add(Item(Level.OK, "Isi entry non-dex identik dengan asli"))
            }
        }
        return items
    }
}
