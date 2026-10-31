package com.jartool.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jartool.app.core.SmaliEngine
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.launch

/** Kartu "Edit Smali": tombol bongkar ke Smali dan susun ulang JAR. */
@Composable
fun SmaliActions(project: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf(listOf<String>()) }
    var hasSmali by remember { mutableStateOf(SmaliEngine.hasSmali(ctx, project)) }

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
            busy = false
        }
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
                onClick = { start { log -> SmaliEngine.disassembleProject(ctx, project, log) } },
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
        }
    }
}
