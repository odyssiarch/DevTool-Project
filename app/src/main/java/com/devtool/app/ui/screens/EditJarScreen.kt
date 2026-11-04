package com.devtool.app.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devtool.app.core.JarNames
import com.devtool.app.core.Workspace
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors

/**
 * Edit JAR: satu halaman. Atas: JAR yang sedang diedit beserta tombol memilih JAR.
 * Bawah: ringkasan, daftar dex, dan kartu Edit Smali (bongkar, edit, susun ulang).
 */
@Composable
fun EditJarScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("edit_jar", Context.MODE_PRIVATE) }
    var project by rememberSaveable {
        mutableStateOf(prefs.getString("terakhir", null)?.takeIf { Workspace.jarOf(ctx, it).exists() })
    }
    val picker = rememberSourcePicker {
        project = it
        prefs.edit().putString("terakhir", it).apply()
    }

    Column(Modifier.fillMaxSize()) {
        JarTopBar("Edit JAR", onBack)
        val p = project
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            JarCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Folder)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("JAR yang diedit", style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
                        Text(
                            if (p != null) JarNames.display(p) else "Belum ada JAR dipilih",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarColors.TextPrimary,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = picker::openFile) { Text("File JAR") }
                    TextButton(onClick = picker::openProjects) { Text("Proyek yang ada") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (p != null) {
            ProjectBody(p, true, Modifier.weight(1f))
        } else {
            KosongEdit(Modifier.weight(1f))
        }
    }
    SourcePickerDialogs(picker)
}

/** Tampilan yang sama dengan halaman proyek, tetapi belum ada JAR: isinya strip dan tombol nonaktif. */
@Composable
private fun KosongEdit(modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            JarCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Ringkasan", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                    SummaryRow("Ukuran", "-")
                    SummaryRow("Jumlah berkas", "-")
                    SummaryRow("Berkas dex", "-")
                    SummaryRow("Total class", "-")
                }
            }
        }
        item {
            JarCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Edit Smali", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. Bongkar ke Smali.\n2. Edit file .smali di folder hasilnya.\n3. Susun ulang JAR.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarColors.TextMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Pilih JAR dulu lewat File JAR atau Proyek yang ada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarColors.TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Bongkar ke Smali") }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Susun ulang JAR") }
                }
            }
        }
    }
}
