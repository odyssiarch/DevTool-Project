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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.io.InputStreamReader

@Composable
fun LiveLogcatScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    var isRunning by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf<String>()) }
    var query by remember { mutableStateOf("") }
    var logcatJob by remember { mutableStateOf<Job?>(null) }

    // Fungsi untuk memulai logcat
    fun startLogcat() {
        if (isRunning) return
        isRunning = true
        logs = listOf("Menunggu izin Root...", "Menjalankan logcat...")
        
        logcatJob = scope.launch(Dispatchers.IO) {
            try {
                // Perintah: clear log dulu, lalu mulai logcat
                val process = ProcessBuilder("su", "-c", "logcat -c && logcat -v time").start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var line: String?
                val buffer = mutableListOf<String>()
                var lastUpdate = System.currentTimeMillis()

                while (isActive) {
                    line = reader.readLine()
                    if (line == null) break
                    
                    buffer.add(line)
                    
                    // Update UI tiap 500ms biar nggak ngelag parah
                    if (System.currentTimeMillis() - lastUpdate > 500) {
                        withContext(Dispatchers.Main) {
                            logs = (logs + buffer).takeLast(2000) // Batasi 2000 baris biar RAM aman
                            if (query.isEmpty()) {
                                listState.animateScrollToItem(logs.size.coerceAtLeast(0))
                            }
                        }
                        buffer.clear()
                        lastUpdate = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    logs = logs + "Gagal menjalankan logcat: ${e.message}. Pastikan HP sudah di-root dan izin diberikan."
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

    Column(Modifier.fillMaxSize()) {
        JarTopBarActions("Live Logcat (Root)", onBack) {
            IconButton(onClick = { logs = emptyList() }) {
                Icon(Icons.Outlined.Clear, contentDescription = "Bersihkan Log")
            }
            IconButton(onClick = { if (isRunning) stopLogcat() else startLogcat() }) {
                Icon(
                    if (isRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    contentDescription = if (isRunning) "Stop" else "Start",
                    tint = if (isRunning) Color.Red else JarColors.Blue
                )
            }
        }

        // Filter bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter log (misal: Face, Biometric)", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(50.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarColors.Blue,
                    unfocusedBorderColor = JarColors.Outline
                )
            )
        }

        val filteredLogs = remember(logs, query) {
            if (query.isEmpty()) logs else logs.filter { it.contains(query, ignoreCase = true) }
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(filteredLogs) { i, line ->
                val isError = line.contains(" E ") || line.contains("FATAL") || line.contains("Exception")
                val textColor = if (isError) Color(0xFFFF6B6B) else JarColors.TextPrimary

                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text(
                        "${i + 1}",
                        color = JarColors.TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = line,
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}