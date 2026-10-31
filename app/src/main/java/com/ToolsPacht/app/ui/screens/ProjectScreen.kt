package com.jartool.app.ui.screens

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
import com.jartool.app.core.JarAnalyzer
import com.jartool.app.core.JarInfo
import com.jartool.app.core.formatSize
import com.jartool.app.ui.components.*
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProjectScreen(name: String, edit: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<JarInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name) {
        try {
            info = withContext(Dispatchers.IO) { JarAnalyzer.analyze(ctx, name) }
        } catch (e: Exception) {
            error = e.message ?: "Gagal membaca JAR"
        }
    }

    Column(Modifier.fillMaxSize()) {
        JarTopBar(name, onBack)
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
                    item {
                        JarCard {
                            Text(
                                "Langkah berikutnya: bongkar ke Smali, edit, lalu susun ulang JAR. Fitur ini menyusul.",
                                color = JarColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
