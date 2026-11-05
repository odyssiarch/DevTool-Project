package com.devtool.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.devtool.app.core.StubArch
import com.devtool.app.core.StubElf
import com.devtool.app.ui.components.JarCard
import com.devtool.app.ui.components.JarTopBar
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val NAMA_BERKAS = Regex("^[A-Za-z0-9._+-]+$")

/** Buat stub tanpa compiler: library .so (fungsi/JNI yang mengembalikan nilai tetap) atau binary statis. */
@Composable
fun StubScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLib by rememberSaveable { mutableStateOf(true) }
    var arch by rememberSaveable { mutableStateOf(StubArch.ARM64.name) }
    var soname by rememberSaveable { mutableStateOf("libstub.so") }
    var funcs by rememberSaveable { mutableStateOf("") }
    var jniOnLoad by rememberSaveable { mutableStateOf(false) }
    var exeName by rememberSaveable { mutableStateOf("stub_bin") }
    var exitCode by rememberSaveable { mutableStateOf("0") }
    var message by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun buat() {
        scope.launch {
            busy = true
            result = try {
                withContext(Dispatchers.IO) {
                    val a = StubArch.valueOf(arch)
                    if (isLib) buatLibrary(ctx, a, soname.trim(), funcs, jniOnLoad)
                    else buatBinary(ctx, a, exeName.trim(), exitCode.trim(), message)
                }
            } catch (e: Exception) {
                "Gagal: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JarTopBar("Buat stub .so", onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))

            JarCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Jenis", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isLib, onClick = { isLib = true }, label = { Text("Library .so") })
                        FilterChip(selected = !isLib, onClick = { isLib = false }, label = { Text("Binary") })
                    }
                    Text("Arsitektur", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StubArch.entries.forEach { a ->
                            FilterChip(selected = arch == a.name, onClick = { arch = a.name }, label = { Text(a.abi) })
                        }
                    }
                }
            }

            JarCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isLib) {
                        Text("Library", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                        OutlinedTextField(
                            value = soname,
                            onValueChange = { soname = it },
                            label = { Text("Nama berkas (soname)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = funcs,
                            onValueChange = { funcs = it },
                            label = { Text("Fungsi (satu per baris)") },
                            placeholder = { Text("nama_fungsi\nfungsi_lain=1\nJava_com_contoh_Kelas_metode=0") },
                            minLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Format: nama atau nama=nilai. Nilai yang dikembalikan: angka, 0x..., true, false, null, atau -1. Baris berawalan # diabaikan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarColors.TextMuted,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = jniOnLoad, onCheckedChange = { jniOnLoad = it })
                            Text("Tambah JNI_OnLoad (JNI 1.6)", color = JarColors.TextPrimary)
                        }
                    } else {
                        Text("Binary", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                        OutlinedTextField(
                            value = exeName,
                            onValueChange = { exeName = it },
                            label = { Text("Nama berkas") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = exitCode,
                            onValueChange = { exitCode = it },
                            label = { Text("Kode keluar (0 sampai 255)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Pesan yang dicetak (opsional)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Button(
                onClick = { buat() },
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Buat stub") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            result?.let { msg ->
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

/** Folder hasil: files/stub/<abi>/ di folder app (bisa dibuka lewat pengelola berkas). */
private fun simpan(ctx: Context, abi: String, nama: String, data: ByteArray, bisaDijalankan: Boolean): File {
    val root = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "stub")
    val dir = File(root, abi).apply { mkdirs() }
    val f = File(dir, nama)
    f.writeBytes(data)
    if (bisaDijalankan) f.setExecutable(true, false)
    return f
}

private fun periksaNama(nama: String) {
    if (!NAMA_BERKAS.matches(nama)) {
        throw IllegalArgumentException("Nama berkas hanya boleh huruf, angka, titik, garis bawah, tanda + dan -")
    }
}

private fun buatLibrary(ctx: Context, arch: StubArch, soname: String, funcs: String, jniOnLoad: Boolean): String {
    periksaNama(soname)
    var spec = funcs
    if (jniOnLoad && Regex("(?m)^\\s*JNI_OnLoad\\s*(=|$)").find(spec) == null) spec += "\nJNI_OnLoad=0x10006"
    val daftar = StubElf.parseFunctions(spec)
    val data = StubElf.library(arch, soname, daftar)
    val f = simpan(ctx, arch.abi, soname, data, false)
    return "Selesai: ${daftar.size} fungsi, ${data.size} byte (${arch.abi}).\n${f.absolutePath}"
}

private fun buatBinary(ctx: Context, arch: StubArch, nama: String, kode: String, pesan: String): String {
    periksaNama(nama)
    val k = kode.toIntOrNull()
    if (k == null || k < 0 || k > 255) throw IllegalArgumentException("Kode keluar harus 0 sampai 255")
    val teks = if (pesan.isBlank()) null else (if (pesan.endsWith("\n")) pesan else pesan + "\n")
    val data = StubElf.executable(arch, k, teks)
    val f = simpan(ctx, arch.abi, nama, data, true)
    return "Selesai: ${data.size} byte (${arch.abi}), keluar dengan kode $k.\n${f.absolutePath}\n" +
        "Folder app tidak bisa dieksekusi langsung. Salin ke /data/local/tmp lalu chmod +x."
}
