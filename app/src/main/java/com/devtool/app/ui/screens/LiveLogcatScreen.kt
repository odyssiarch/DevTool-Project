package com.devtool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import com.devtool.app.ui.components.TonalAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.ui.components.JarTopBarActions
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveLogcatScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    var isRunning by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var query by remember { mutableStateOf("") }
    var logcatJob by remember { mutableStateOf<Job?>(null) }
    var autoSave by remember { mutableStateOf(true) } // Default nyala
    var savedPath by remember { mutableStateOf<String?>(null) }

    // Fungsi untuk memulai logcat
    fun startLogcat() {
        if (isRunning) return
        isRunning = true
        logs = listOf("Menunggu izin Root...", "Menjalankan logcat...")
        
        logcatJob = scope.launch(Dispatchers.IO) {
            try {
                // Siapkan file untuk Auto-Save
                var writer: FileWriter? = null
                if (autoSave) {
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val logDir = File(ctx.getExternalFilesDir(null), "logs")
                    logDir.mkdirs()
                    val file = File(logDir, "logcat_$timeStamp.txt")
                    writer = FileWriter(file, true)
                    withContext(Dispatchers.Main) {
                        savedPath = file.absolutePath
                    }
                }

                // Perintah: clear log dulu, lalu mulai logcat
                val process = ProcessBuilder("su", "-c", "logcat -b all -c && logcat -b all -v threadtime").start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var line: String?
                val buffer = mutableListOf<String>()
                var lastUpdate = System.currentTimeMillis()

                while (isActive) {
                    line = reader.readLine()
                    if (line == null) break
                    
                    buffer.add(line)
                    // Tulis ke file otomatis jika autoSave nyala
                    writer?.appendLine(line)
                    
                    // Update UI tiap 500ms biar nggak ngelag parah
                    if (System.currentTimeMillis() - lastUpdate > 500) {
                        writer?.flush() // Paksa simpan ke disk
                        withContext(Dispatchers.Main) {
                            logs = (logs + buffer).takeLast(2000) // Batasi 2000 baris di layar
                            if (query.isEmpty()) {
                                listState.animateScrollToItem(logs.size.coerceAtLeast(0))
                            }
                        }
                        buffer.clear()
                        lastUpdate = System.currentTimeMillis()
                    }
                }
                writer?.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs = logs + "Gagal: ${e.message}. Pastikan HP sudah di-root."
                    isRunning = false
                }
            }
        }
    }

    // Fungsi untuk berhenti
    fun stopLogcat() {
        logcatJob?.cancel()
        logcatJob = null
        isRunning = false
        scope.launch(Dispatchers.IO) {
            Runtime.getRuntime().exec("su -c pkill logcat")
        }
    }

    // Matikan logcat otomatis kalau user keluar layar
    DisposableEffect(Unit) {
        onDispose { stopLogcat() }
    }

    val clipboard = LocalClipboardManager.current
    val filteredLogs = remember(logs, query) {
        if (query.isEmpty()) logs else logs.filter { it.contains(query, ignoreCase = true) }
    }
    val green = Color(0xFF5BD98C)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Terminal", color = JarColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text("Live log \u2022 Logcat (Root)", color = JarColors.TextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            TonalAction(Icons.Outlined.ContentCopy, "Salin Log", Modifier.weight(1f)) {
                clipboard.setText(AnnotatedString(filteredLogs.joinToString("\n")))
                Toast.makeText(ctx, "Log disalin", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.width(10.dp))
            TonalAction(Icons.Outlined.Save, "Simpan Log", Modifier.weight(1f)) {
                val text = filteredLogs.joinToString("\n")
                scope.launch(Dispatchers.IO) {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val dir = File(ctx.getExternalFilesDir(null), "logs").apply { mkdirs() }
                    val f = File(dir, "logcat_manual_$stamp.txt")
                    f.writeText(text)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Tersimpan: ${f.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        val chipShape = RoundedCornerShape(20.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(chipShape)
                .background(JarColors.Surface)
                .border(1.dp, JarColors.Outline, chipShape)
                .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isRunning) "LOGCAT = ROOT | BERJALAN" else "LOGCAT = ROOT | BERHENTI",
                color = JarColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (isRunning) stopLogcat() else startLogcat() }) {
                Text(
                    if (isRunning) "BERHENTI" else "MULAI",
                    color = if (isRunning) Color(0xFFFF6B6B) else JarColors.Accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (savedPath != null && isRunning) {
            Text(
                "Menyimpan ke: $savedPath",
                color = JarColors.TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        val boxShape = RoundedCornerShape(28.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(boxShape)
                .background(JarColors.Surface)
                .border(1.dp, JarColors.Outline, boxShape),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                itemsIndexed(filteredLogs) { i, line ->
                    val isError = line.contains(" E ") || line.contains("FATAL") || line.contains("Exception")
                    val textColor = if (isError) Color(0xFFFF6B6B) else JarColors.TextPrimary
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(
                            "${i + 1}",
                            color = JarColors.TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.width(40.dp),
                        )
                        Text(
                            text = line,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("filter") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Text("$", color = green, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarColors.Accent,
                    unfocusedBorderColor = JarColors.Outline,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Checkbox(
                checked = autoSave,
                onCheckedChange = { autoSave = it },
                enabled = !isRunning,
                colors = CheckboxDefaults.colors(checkedColor = JarColors.Accent),
            )
            Text("Auto-Save", fontSize = 12.sp, color = JarColors.TextPrimary)
        }
        Spacer(Modifier.height(12.dp))
    }
}
