package com.devtool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devtool.app.core.JarAnalyzer
import com.devtool.app.core.JarInfo
import com.devtool.app.core.JarNames
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProjectScreen(name: String, edit: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        JarTopBar(JarNames.display(name), onBack)
        ProjectBody(name, edit, Modifier.weight(1f))
    }
}

/** Isi halaman proyek: ringkasan, daftar dex, dan (kalau edit) kartu Edit Smali. Dipakai ProjectScreen dan EditJarScreen. */
@Composable
fun ProjectBody(name: String, edit: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var info by remember(name) { mutableStateOf<JarInfo?>(null) }
    var error by remember(name) { mutableStateOf<String?>(null) }

    LaunchedEffect(name) {
        try {
            info = withContext(Dispatchers.IO) { JarAnalyzer.analyze(ctx, name) }
        } catch (e: Exception) {
            error = e.message ?: "Gagal membaca JAR"
        }
    }

    Box(modifier.fillMaxWidth()) {
        val i = info
        val err = error
        when {
            err != null -> Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            i == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    JarCard {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ringkasan", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                            SummaryRow("Ukuran", formatSize(i.size))
                            SummaryRow("Jumlah berkas", i.entries.toString())
                            SummaryRow("Berkas dex", i.dex.size.toString())
                            SummaryRow("Total class", i.totalClasses.toString())
                        }
                    }
                }
                items(i.dex, key = { it.name }) { d ->
                    JarCard {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Code)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(d.name, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                                Text(
                                    d.describe(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarColors.TextMuted,
                                )
                            }
                        }
                    }
                }
                if (i.dex.isEmpty()) {
                    item {
                        Text(
                            "JAR ini tidak berisi classes.dex. Kemungkinan isinya class Java biasa.",
                            color = JarColors.TextMuted,
                        )
                    }
                }
                if (edit) {
                    item { SmaliActions(name) }
                    item { SmaliEditorEntry(name) }
                }
            }
        }
        if (edit) SmaliEditorHost(name)
    }
}
