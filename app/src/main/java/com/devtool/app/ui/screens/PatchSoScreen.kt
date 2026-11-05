package com.devtool.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devtool.app.core.ElfFile
import com.devtool.app.core.SoInfo
import com.devtool.app.core.StubElf
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.JarCard
import com.devtool.app.ui.components.JarTopBar
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAKS_UKURAN = 256L * 1024 * 1024

private enum class Mode(val label: String) {
    FUNGSI("Fungsi"),
    HEX("Hex"),
    DEP("Dependency"),
}

private class Loaded(val name: String, val bytes: ByteArray, val info: SoInfo)

/**
 * Patch library .so (arm64 / x86_64) yang sudah ada: timpa fungsi supaya mengembalikan nilai tetap,
 * ganti byte lewat pola hex, atau ganti/hapus dependency. Berkas asli tidak diubah; hasil disimpan sebagai berkas baru.
 */
@Composable
fun PatchSoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf<ByteArray?>(null) }
    var info by remember { mutableStateOf<SoInfo?>(null) }

    var mode by rememberSaveable { mutableStateOf(Mode.FUNGSI.name) }
    var funcSpec by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var hexPat by rememberSaveable { mutableStateOf("") }
    var hexRep by rememberSaveable { mutableStateOf("") }
    var hexAll by rememberSaveable { mutableStateOf(false) }
    var depOld by rememberSaveable { mutableStateOf("") }
    var depNew by rememberSaveable { mutableStateOf("") }

    var patched by remember { mutableStateOf<ByteArray?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            patched = null
            report = try {
                val l = withContext(Dispatchers.IO) { muat(ctx, uri) }
                fileName = l.name
                original = l.bytes
                info = l.info
                null
            } catch (e: Exception) {
                original = null
                info = null
                "Gagal membuka: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val data = patched
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            report = try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data) }
                        ?: throw IllegalStateException("Tidak bisa menulis ke tujuan")
                }
                "Tersimpan (${formatSize(data.size.toLong())})."
            } catch (e: Exception) {
                "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun terapkan() {
        val src = original ?: return
        val nama = fileName ?: "hasil.so"
        scope.launch {
            busy = true
            try {
                val hasil = withContext(Dispatchers.IO) {
                    val work = src.copyOf()
                    val elf = ElfFile(work)
                    val teks = when (Mode.valueOf(mode)) {
                        Mode.FUNGSI -> patchFungsi(elf, funcSpec)
                        Mode.HEX -> patchHex(elf, hexPat, hexRep, hexAll)
                        Mode.DEP -> patchDependency(elf, depOld.trim(), depNew.trim())
                    }
                    // pastikan hasilnya masih bisa dibaca ulang sebelum ditawarkan untuk disimpan
                    ElfFile(work).info()
                    val f = simpan(ctx, nama, work)
                    Triple(work, teks, f)
                }
                patched = hasil.first
                report = hasil.second + "\n\nUkuran ${formatSize(hasil.first.size.toLong())}, sama dengan aslinya.\nSalinan: ${hasil.third.absolutePath}"
            } catch (e: Exception) {
                patched = null
                report = "Gagal: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JarTopBar("Patch .so", onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = { open.launch(arrayOf("*/*")) },
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (original == null) "Pilih berkas .so" else "Ganti berkas") }

            info?.let { i ->
                JarCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(fileName.orEmpty(), style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                        Text(
                            "${i.arch.abi} · ${formatSize(original?.size?.toLong() ?: 0L)} · ${i.functions.size} fungsi diekspor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarColors.TextMuted,
                        )
                        i.soname?.let { Text("soname: $it", style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted) }
                        if (i.needed.isNotEmpty()) {
                            Text(
                                "butuh: ${i.needed.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarColors.TextMuted,
                            )
                        }
                    }
                }

                JarCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Jenis patch", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mode.entries.forEach { m ->
                                FilterChip(selected = mode == m.name, onClick = { mode = m.name }, label = { Text(m.label) })
                            }
                        }
                    }
                }

                when (Mode.valueOf(mode)) {
                    Mode.FUNGSI -> JarCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Cari fungsi") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val q = query.trim()
                            if (q.length >= 2) {
                                val cocok = remember(i, q) {
                                    i.functions.filter { it.name.contains(q, ignoreCase = true) }.take(30)
                                }
                                if (cocok.isEmpty()) {
                                    Text("Tidak ada yang cocok.", style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
                                } else {
                                    cocok.forEach { f ->
                                        Text(
                                            "${f.name}  ·  ${f.size} byte",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarColors.TextPrimary,
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = funcSpec,
                                onValueChange = { funcSpec = it },
                                label = { Text("Fungsi yang ditimpa (satu per baris)") },
                                placeholder = { Text("nama_fungsi=0\nfungsi_lain=1") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Format: nama atau nama=nilai (angka, 0x..., true, false, null, -1). " +
                                    "Fungsi dikembalikan nilai tetap. Fungsi yang lebih kecil dari kode pengganti dilewati.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarColors.TextMuted,
                            )
                        }
                    }

                    Mode.HEX -> JarCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = hexPat,
                                onValueChange = { hexPat = it },
                                label = { Text("Cari (hex, ?? = bebas)") },
                                placeholder = { Text("1F 20 03 D5 ?? 00") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = hexRep,
                                onValueChange = { hexRep = it },
                                label = { Text("Ganti dengan (hex, ?? = tetap)") },
                                placeholder = { Text("00 00 80 D2 ?? ??") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = hexAll, onCheckedChange = { hexAll = it })
                                Text("Ganti semua kecocokan", color = JarColors.TextPrimary)
                            }
                            Text(
                                "Panjang pengganti harus sama dengan pola. Tanpa centang, pola harus cocok tepat satu tempat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarColors.TextMuted,
                            )
                        }
                    }

                    Mode.DEP -> JarCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Ketuk untuk memilih dependency", style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
                            i.needed.forEach { n ->
                                FilterChip(selected = depOld == n, onClick = { depOld = n }, label = { Text(n) })
                            }
                            OutlinedTextField(
                                value = depOld,
                                onValueChange = { depOld = it },
                                label = { Text("Dependency lama") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = depNew,
                                onValueChange = { depNew = it },
                                label = { Text("Nama baru (kosong = hapus)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Nama baru tidak boleh lebih panjang dari nama lama. Menghapus dependency hanya aman kalau library itu memang tidak dipakai.",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarColors.TextMuted,
                            )
                        }
                    }
                }

                Button(
                    onClick = { terapkan() },
                    enabled = !busy,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Terapkan patch") }

                if (patched != null) {
                    OutlinedButton(
                        onClick = { save.launch(fileName ?: "hasil.so") },
                        enabled = !busy,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Simpan sebagai…") }
                }
            }

            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            report?.let { msg ->
                JarCard {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarColors.TextPrimary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------- logika ----------

private fun muat(ctx: Context, uri: Uri): Loaded {
    val nama = ctx.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: "berkas.so"
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Tidak bisa membaca berkas")
    if (bytes.size > MAKS_UKURAN) throw IllegalArgumentException("Berkas terlalu besar (maksimal 256 MB)")
    return Loaded(nama, bytes, ElfFile(bytes).info())
}

private fun patchFungsi(elf: ElfFile, spec: String): String {
    val daftar = StubElf.parseFunctions(spec)
    val hasil = elf.patchFunctions(daftar)
    val ok = hasil.count { it.ok }
    if (ok == 0) {
        throw IllegalArgumentException("Tidak ada fungsi yang berhasil di-patch:\n" + hasil.joinToString("\n") { "✗ ${it.name}: ${it.detail}" })
    }
    val baris = hasil.joinToString("\n") { (if (it.ok) "✓ " else "✗ ") + it.name + ": " + it.detail }
    return "$ok dari ${hasil.size} fungsi di-patch.\n$baris"
}

private fun patchHex(elf: ElfFile, pola: String, ganti: String, semua: Boolean): String {
    val p = ElfFile.parseHex(pola, true)
    val r = ElfFile.parseHex(ganti, true)
    val hits = elf.applyHex(p, r, semua)
    val daftar = hits.take(10).joinToString("\n") { "• 0x${it.toString(16)}" }
    val lebih = if (hits.size > 10) "\n… dan ${hits.size - 10} lainnya" else ""
    return "${hits.size} tempat diganti (${p.size} byte).\n$daftar$lebih"
}

private fun patchDependency(elf: ElfFile, lama: String, baru: String): String {
    if (lama.isEmpty()) throw IllegalArgumentException("Isi nama dependency lama")
    return if (baru.isEmpty()) {
        elf.removeNeeded(lama)
        "Dependency $lama dihapus dari tabel DYNAMIC."
    } else {
        elf.renameNeeded(lama, baru)
        "Dependency $lama diganti menjadi $baru."
    }
}

/** Salinan hasil: files/patch_so/<nama> di folder app. Berkas asli tidak disentuh. */
private fun simpan(ctx: Context, nama: String, data: ByteArray): File {
    val aman = nama.replace(Regex("[^A-Za-z0-9._+-]"), "_").ifBlank { "hasil.so" }
    val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "patch_so").apply { mkdirs() }
    val f = File(dir, aman)
    f.writeBytes(data)
    return f
}
