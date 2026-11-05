package com.devtool.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.ui.components.JarCard
import com.devtool.app.ui.components.JarTopBarActions
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Warna untuk Log
private val L_FATAL = Color(0xFFFF6B6B) // Merah
private val L_WARN = Color(0xFFFFC857)  // Kuning
private val L_AVC = Color(0xFFFFB067)   // Oranye
private val L_NORMAL = Color(0xFFE6EAF0)

/** Layar pembaca Ramoops / Logcat untuk mendeteksi crash / bootloop. */
@Composable
fun LogcatScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    
    var code by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var fileName by remember { mutableStateOf("Log") }
    var query by remember { mutableStateOf("") }
    var onlyErrors by remember { mutableStateOf(false) }
    var fontSize by remember { mutableIntStateOf(12) }

    // Launcher untuk memilih file log
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    code = text
                    lines = text.lines()
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Log File"
                } catch (e: Exception) {
                    // Abaikan error baca file
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        JarTopBarActions(fileName, onBack) {
            if (code != null) {
                IconButton(onClick = { clip.setText(AnnotatedString(code!!)) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin semua")
                }
            }
        }

        if (code == null) {
            // Tampilan sebelum file dipilih
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Baca log untuk menemukan penyebab bootloop.\nBiasanya berada di folder /sys/fs/pstore/ (console-ramoops / pmsg-ramoops).",
                    color = JarColors.TextMuted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { launcher.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = JarColors.Blue, contentColor = JarColors.Black)
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih File Log")
                }
            }
        } else {
            // Bar Filter & Pencarian
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari teks log...", fontSize = 13.sp) },
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
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = onlyErrors,
                        onCheckedChange = { onlyErrors = it },
                        colors = CheckboxDefaults.colors(checkedColor = L_FATAL)
                    )
                    Text("Hanya Error", fontSize = 12.sp, color = JarColors.TextPrimary)
                }
            }

            // Filter Logika
            val filteredLines = remember(lines, query, onlyErrors) {
                lines.filter { line ->
                    val matchQuery = query.isEmpty() || line.contains(query, ignoreCase = true)
                    val matchError = if (onlyErrors) {
                        val l = line.lowercase()
                        l.contains("fatal") || l.contains("panic") || l.contains("exception") || 
                        l.contains(" error ") || l.contains("avc: denied")
                    } else true
                    matchQuery && matchError
                }
            }

            // Tampilan Teks Log
            val hScroll = rememberScrollState()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filteredLines) { i, line ->
                    Row(Modifier.horizontalScroll(hScroll)) {
                        Text(
                            "${i + 1}",
                            color = JarColors.TextMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(52.dp).padding(end = 12.dp),
                        )
                        Text(
                            highlightLog(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

// Fungsi pewarnaan teks
private fun highlightLog(line: String): AnnotatedString {
    val lower = line.lowercase()
    val color = when {
        lower.contains("fatal") || lower.contains("panic") || lower.contains("exception") || lower.contains(" error ") -> L_FATAL
        lower.contains("avc: denied") -> L_AVC
        lower.contains("warning") || lower.contains("warn") -> L_WARN
        else -> L_NORMAL
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = color)) { append(line) }
    }
}