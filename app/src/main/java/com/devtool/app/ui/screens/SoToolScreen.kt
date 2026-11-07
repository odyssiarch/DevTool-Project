package com.devtool.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.core.Arm64
import com.devtool.app.core.BinUtil
import com.devtool.app.core.ElfFile
import com.devtool.app.core.ElfInfo
import com.devtool.app.core.ElfSymbol
import com.devtool.app.core.PatchPresets
import com.devtool.app.ui.components.FilePickRow
import com.devtool.app.ui.components.PillCard
import com.devtool.app.ui.components.ScreenHeader
import com.devtool.app.ui.components.TonalAction
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SoAmber = Color(0xFFFFC857)
private val SoRed = Color(0xFFFF6B6B)

private class PatchRec(val offset: Int, val old: ByteArray, val new: ByteArray, val label: String)

/** Satu berkas yang sedang dikerjakan: salinan data yang dipatch di memori, asli tidak disentuh. */
private class SoSession(val name: String, val orig: ByteArray, val info: ElfInfo?) {
    val data: ByteArray = orig.copyOf()
    val patches = mutableStateListOf<PatchRec>()
    var rev by mutableStateOf(0)

    fun apply(offset: Int, bytes: ByteArray, label: String): String? {
        if (offset < 0 || offset + bytes.size > data.size) return "Melewati ujung berkas"
        val old = data.copyOfRange(offset, offset + bytes.size)
        System.arraycopy(bytes, 0, data, offset, bytes.size)
        patches.add(PatchRec(offset, old, bytes, label))
        rev++
        return null
    }

    fun undo() {
        if (patches.isEmpty()) return
        val p = patches.removeAt(patches.lastIndex)
        System.arraycopy(p.old, 0, data, p.offset, p.old.size)
        rev++
    }
}

private class PatchTarget(val title: String, val offset: Int, val vaddr: Long?, val size: Long?, val thumb: Boolean)

private fun queryName(ctx: Context, uri: Uri): String? =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    }

@Composable
fun SoToolScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SoSession?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rootPath by remember { mutableStateOf("") }

    fun load(name: String, bytes: ByteArray) {
        scope.launch {
            busy = true
            val info = withContext(Dispatchers.Default) { ElfFile.parse(bytes) }
            session = SoSession(name, bytes, info)
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                error = null
                val res = withContext(Dispatchers.IO) {
                    try {
                        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes == null) null else (queryName(ctx, uri) ?: "berkas.bin") to bytes
                    } catch (e: Exception) {
                        null
                    }
                }
                if (res == null) {
                    error = "Gagal membaca berkas"
                    busy = false
                } else {
                    load(res.first, res.second)
                }
            }
        }
    }

    fun readRoot() {
        val path = rootPath.trim()
        if (path.isEmpty()) return
        scope.launch {
            busy = true
            error = null
            val bytes = withContext(Dispatchers.IO) {
                try {
                    val p = ProcessBuilder("su", "-c", "cat '" + path.replace("'", "'\\''") + "'").start()
                    val b = p.inputStream.readBytes()
                    p.waitFor()
                    b
                } catch (e: Exception) {
                    null
                }
            }
            if (bytes == null || bytes.isEmpty()) {
                error = "Gagal membaca (root ditolak atau path salah)"
                busy = false
            } else {
                load(path.substringAfterLast('/'), bytes)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Binary / .so", session?.name ?: "Baca dan patch file ELF", onBack)
        val s = session
        if (s == null) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PillCard(title = "BERKAS") {
                    Text("Belum dipilih", color = SoAmber, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(14.dp))
                    FilePickRow(Icons.Outlined.FolderOpen, "Pilih file (.so, .bin, ELF)") {
                        picker.launch(arrayOf("*/*"))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "File asli tidak diubah. Patch dikerjakan di salinan, hasilnya disimpan sebagai file baru.",
                        color = JarColors.TextMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
                PillCard(title = "BACA DENGAN ROOT") {
                    Text(
                        "Untuk file di /system, /vendor, dan folder lain yang tidak bisa dipilih biasa.",
                        color = JarColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rootPath,
                        onValueChange = { rootPath = it },
                        placeholder = { Text("/vendor/lib64/libfoo.so") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarColors.Accent,
                            unfocusedBorderColor = JarColors.Outline,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    TonalAction(Icons.Outlined.Lock, "Ambil dengan root", Modifier.fillMaxWidth()) { readRoot() }
                }
                if (busy) Text("Memuat...", color = JarColors.TextMuted, fontSize = 13.sp)
                val e = error
                if (e != null) Text(e, color = SoRed, fontSize = 13.sp)
            }
        } else {
            SoWorkspace(s)
        }
    }
}

@Composable
private fun SoWorkspace(s: SoSession) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf<PatchTarget?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Info", "Simbol", "Cari hex", "String").forEachIndexed { i, t ->
                SoChip(t, tab == i) { tab = i }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> InfoTab(s)
                1 -> SymbolTab(s) { sym ->
                    if (sym.offset < 0) {
                        msg = "Alamat fungsi tidak terpetakan ke berkas"
                    } else {
                        target = PatchTarget(sym.name, sym.offset.toInt(), sym.vaddr, sym.size, sym.thumb)
                    }
                }
                2 -> HexTab(s) { off -> target = PatchTarget(String.format("Offset 0x%x", off), off, null, null, false) }
                else -> StringTab(s) { off -> target = PatchTarget(String.format("Offset 0x%x", off), off, null, null, false) }
            }
        }
        val m = msg
        if (m != null) {
            Text(
                m,
                color = JarColors.TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TonalAction(Icons.Outlined.Undo, "Undo patch", Modifier.weight(1f)) {
                if (s.patches.isEmpty()) {
                    msg = "Belum ada patch"
                } else {
                    s.undo()
                    msg = "Patch terakhir dibatalkan"
                }
            }
            TonalAction(Icons.Outlined.Save, "Simpan hasil", Modifier.weight(1f)) {
                val snapshot = s.data.copyOf()
                scope.launch(Dispatchers.IO) {
                    val text = try {
                        val dir = File(ctx.getExternalFilesDir(null), "so_out").apply { mkdirs() }
                        val f = File(dir, s.name)
                        f.writeBytes(snapshot)
                        "Tersimpan: " + f.absolutePath
                    } catch (e: Exception) {
                        "Gagal menyimpan: " + e.message
                    }
                    withContext(Dispatchers.Main) { msg = text }
                }
            }
        }
        Spacer(Modifier.height(88.dp))
    }

    val t = target
    if (t != null) {
        PatchDialog(
            s = s,
            t = t,
            onDismiss = { target = null },
            onDone = {
                msg = it
                target = null
            },
        )
    }
}

@Composable
private fun SoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) JarColors.Accent else JarColors.TextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) JarColors.AccentDim else JarColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

// ---------- Info ----------
@Composable
private fun InfoTab(s: SoSession) {
    val info = s.info
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PillCard(title = "BERKAS") {
            InfoRow("Nama", s.name)
            InfoRow("Ukuran", s.orig.size.toString() + " byte")
            if (info == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bukan ELF. Hanya mode hex yang tersedia (cari pola dan patch di offset).",
                    color = SoAmber, fontSize = 13.sp, lineHeight = 18.sp,
                )
            } else {
                InfoRow("Arsitektur", info.machineName)
                InfoRow("Tipe", info.typeName)
                InfoRow("Kelas", if (info.is64) "64-bit" else "32-bit")
                InfoRow("Endian", if (info.little) "little" else "big")
                InfoRow("SONAME", info.soname ?: "-")
                InfoRow("Section", info.sections.size.toString())
                InfoRow("Fungsi terdaftar", info.funcs.size.toString())
            }
        }
        if (info != null && info.needed.isNotEmpty()) {
            PillCard(title = "DEPENDENSI (NEEDED)") {
                info.needed.forEach {
                    Text(it, color = JarColors.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        PillCard(title = "PATCH DITERAPKAN (${s.patches.size})") {
            if (s.patches.isEmpty()) {
                Text("Belum ada.", color = JarColors.TextMuted, fontSize = 13.sp)
            } else {
                s.patches.forEach { p ->
                    Text(
                        String.format("0x%x  %s", p.offset, p.label),
                        color = JarColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        BinUtil.hex(p.old, 0, 16) + "  ->  " + BinUtil.hex(p.new, 0, 16),
                        color = JarColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = JarColors.TextMuted, fontSize = 14.sp)
        Text(value, color = JarColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- Simbol ----------
@Composable
private fun SymbolTab(s: SoSession, onPick: (ElfSymbol) -> Unit) {
    val funcs = s.info?.funcs.orEmpty()
    var q by remember { mutableStateOf("") }
    val shown = remember(q, funcs) {
        val t = q.trim()
        (if (t.isEmpty()) funcs else funcs.filter { it.name.contains(t, ignoreCase = true) }).take(300)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Cari fungsi (${funcs.size})") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarColors.Accent,
                unfocusedBorderColor = JarColors.Outline,
            ),
        )
        Spacer(Modifier.height(8.dp))
        if (funcs.isEmpty()) {
            Text("Tidak ada simbol fungsi (berkas di-strip atau bukan ELF).", color = JarColors.TextMuted, fontSize = 13.sp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.name + "@" + it.vaddr }) { sym ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(sym) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text(
                        sym.name,
                        color = JarColors.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                    )
                    Text(
                        String.format("vaddr 0x%x   ukuran %d   offset 0x%x", sym.vaddr, sym.size, sym.offset),
                        color = JarColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

// ---------- Cari hex ----------
@Composable
private fun HexTab(s: SoSession, onPick: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var pat by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Int>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = pat,
            onValueChange = { pat = it },
            label = { Text("Pola hex (?? = bebas)") },
            placeholder = { Text("20 00 80 52 ?? ?? 5F D6") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarColors.Accent,
                unfocusedBorderColor = JarColors.Outline,
            ),
        )
        Spacer(Modifier.height(8.dp))
        TonalAction(Icons.Outlined.Search, if (busy) "Mencari..." else "Cari", Modifier.fillMaxWidth()) {
            val p = BinUtil.parsePattern(pat)
            if (p == null) {
                err = "Pola tidak valid"
            } else if (!busy) {
                err = null
                busy = true
                scope.launch {
                    results = withContext(Dispatchers.Default) { BinUtil.find(s.data, p) }
                    busy = false
                }
            }
        }
        val e = err
        if (e != null) Text(e, color = SoRed, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        val list = results
        if (list != null) {
            Text(
                "${list.size} hasil" + (if (list.size >= 300) " (dibatasi 300)" else ""),
                color = JarColors.TextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { off ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(off) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        Text(
                            String.format("0x%x", off),
                            color = JarColors.Accent, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            BinUtil.hex(s.data, off, 16),
                            color = JarColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

// ---------- String ----------
@Composable
private fun StringTab(s: SoSession, onPick: (Int) -> Unit) {
    val all by produceState<List<Pair<Int, String>>>(emptyList(), s) {
        value = withContext(Dispatchers.Default) { BinUtil.strings(s.orig) }
    }
    var q by remember { mutableStateOf("") }
    val shown = remember(q, all) {
        val t = q.trim()
        (if (t.isEmpty()) all else all.filter { it.second.contains(t, ignoreCase = true) }).take(300)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Cari string (${all.size})") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarColors.Accent,
                unfocusedBorderColor = JarColors.Outline,
            ),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown) { (off, text) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(off) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text(
                        String.format("0x%x", off),
                        color = JarColors.Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                    Text(text, color = JarColors.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
                }
            }
        }
    }
}

// ---------- Dialog patch ----------
@Composable
private fun PatchDialog(s: SoSession, t: PatchTarget, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val machine = s.info?.machine ?: 0
    val presets = remember(machine, t.thumb) { PatchPresets.forMachine(machine, t.thumb) }
    var custom by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val rev = s.rev
    val lines = remember(t.offset, rev) {
        if (machine == ElfFile.EM_AARCH64) {
            Arm64.listing(s.data, t.offset, 10, t.vaddr ?: t.offset.toLong())
        } else {
            (0 until 4).map { i -> String.format("%x:  %s", t.offset + i * 16, BinUtil.hex(s.data, t.offset + i * 16, 16)) }
        }
    }

    fun doApply(label: String, bytes: ByteArray) {
        val limit = t.size
        if (limit != null && bytes.size > limit) {
            err = "Patch ${bytes.size} byte melebihi ukuran fungsi ($limit byte)"
            return
        }
        val e = s.apply(t.offset, bytes, label)
        if (e != null) err = e else onDone("Patch diterapkan di " + String.format("0x%x", t.offset) + ": " + label)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.title, maxLines = 2, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    String.format("offset 0x%x", t.offset) +
                        (if (t.vaddr != null) String.format("  vaddr 0x%x", t.vaddr) else "") +
                        (if (t.size != null) "  ukuran ${t.size}" else ""),
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))
                Text(lines.joinToString("\n"), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(12.dp))
                presets.forEach { p ->
                    TonalAction(Icons.Outlined.Build, p.label, Modifier.fillMaxWidth()) { doApply(p.label, p.bytes) }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("Hex kustom") },
                    placeholder = { Text("1F 20 03 D5") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                TonalAction(Icons.Outlined.Build, "Terapkan hex kustom", Modifier.fillMaxWidth()) {
                    val b = BinUtil.parseHex(custom)
                    if (b == null) err = "Hex tidak valid" else doApply("hex kustom (${b.size} byte)", b)
                }
                val e = err
                if (e != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(e, color = SoRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
