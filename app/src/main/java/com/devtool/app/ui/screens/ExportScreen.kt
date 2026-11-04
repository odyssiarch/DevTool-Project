package com.devtool.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devtool.app.core.Exporter
import com.devtool.app.core.Workspace
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Simpan JAR proyek atau semua classes.dex ke penyimpanan. */
@Composable
fun ExportScreen(name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runTask(task: () -> String) {
        scope.launch {
            busy = true
            message = try {
                withContext(Dispatchers.IO) { task() }
            } catch (e: Exception) {
                "Gagal: ${e.message ?: "tidak diketahui"}"
            }
            busy = false
        }
    }

    val saveJar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/java-archive")) { uri ->
        if (uri != null) runTask { Exporter.exportJar(ctx, name, uri) }
    }
    val saveDex = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runTask { Exporter.exportDex(ctx, name, uri) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JarTopBar("Ekspor JAR / DEX", onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(4.dp))
            JarCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Folder)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                        Text(
                            formatSize(Workspace.outputOf(ctx, name).length()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarColors.TextMuted,
                        )
                    }
                }
            }
            JarCard {
                DetailRow(Icons.Outlined.Download, "Simpan sebagai JAR", "Salin JAR proyek ke penyimpanan") {
                    saveJar.launch("$name.jar")
                }
                RowDivider()
                DetailRow(Icons.Outlined.FolderOpen, "Simpan classes.dex", "Tulis semua classes.dex ke folder pilihan") {
                    saveDex.launch(null)
                }
            }
            Text(
                "Semua diproses di perangkat. Tidak perlu internet.",
                color = JarColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 24.dp),
            )
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Menyimpan") },
            text = { CircularProgressIndicator() },
        )
    }
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = JarColors.SurfaceHigh,
            confirmButton = { TextButton(onClick = { message = null }) { Text("Tutup") } },
            text = { Text(msg) },
        )
    }
}
