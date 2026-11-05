package com.devtool.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devtool.app.core.SmaliEngine
import com.devtool.app.core.Workspace
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.launch
import java.io.File

/** Kartu "Edit Smali": tombol bongkar ke Smali dan susun ulang JAR. */
@Composable
fun SmaliActions(project: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lines by remember(project) { mutableStateOf(listOf<String>()) }
    var hasSmali by remember(project) { mutableStateOf(SmaliEngine.hasSmali(ctx, project)) }
    var konfirmasiTimpa by remember { mutableStateOf(false) }

    fun outputJar() = File(Workspace.jarOf(ctx, project).parentFile, "output.jar")
    var outputAda by remember(project) { mutableStateOf(outputJar().exists()) }

    fun start(task: suspend ((String) -> Unit) -> Unit) {
        scope.launch {
            busy = true
            lines = emptyList()
            val add: (String) -> Unit = { line -> lines = (lines + line).takeLast(10) }
            try {
                task(add)
            } catch (e: Throwable) {
                add("GAGAL: ${e.message ?: e.javaClass.simpleName}")
            }
            hasSmali = SmaliEngine.hasSmali(ctx, project)
            outputAda = outputJar().exists()
            busy = false
        }
    }

    fun bongkar() = start { log -> SmaliEngine.disassembleProject(ctx, project, log) }

    if (konfirmasiTimpa) {
        AlertDialog(
            onDismissRequest = { konfirmasiTimpa = false },
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Timpa hasil bongkar?") },
            text = {
                Text("Folder Smali untuk proyek ini sudah ada. Membongkar ulang akan menghapus semua edit .smali di dalamnya.")
            },
            confirmButton = {
                TextButton(onClick = { konfirmasiTimpa = false; bongkar() }) { Text("Bongkar ulang") }
            },
            dismissButton = { TextButton(onClick = { konfirmasiTimpa = false }) { Text("Batal") } },
        )
    }

    JarCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Edit Smali", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
            Text(
                "1. Bongkar ke Smali.\n2. Edit file .smali di folder hasilnya.\n3. Susun ulang JAR. Dex 041 yang berisi beberapa dex dikemas lagi jadi 1 kontainer.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarColors.TextMuted,
            )
            Button(
                onClick = { if (hasSmali) konfirmasiTimpa = true else bongkar() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bongkar ke Smali") }
            Button(
                onClick = { start { log -> SmaliEngine.assembleProject(ctx, project, log) } },
                enabled = !busy && hasSmali,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Susun ulang JAR") }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (lines.isNotEmpty()) {
                Text(
                    lines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = JarColors.TextMuted,
                )
            }
            if (outputAda) {
                Text(
                    "output.jar tersimpan di:\n${outputJar().absolutePath}\nAmbil lewat menu Ekspor JAR / DEX.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarColors.TextMuted,
                )
            }
        }
    }
}
