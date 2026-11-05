package com.devtool.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devtool.app.core.JarNames
import com.devtool.app.core.SmaliDiff
import com.devtool.app.core.Workspace
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ProjectsScreen(onOpen: (String) -> Unit, onOpenEdited: (String) -> Unit = {}, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val projects = remember(tick) { Workspace.projects(ctx) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    var edited by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val picker = rememberSourcePicker { onOpen(it) }

    LaunchedEffect(tick, projects) {
        edited = withContext(Dispatchers.IO) {
            val hasil = LinkedHashMap<String, List<String>>()
            for (p in projects) {
                val keys = SmaliDiff.editedCandidates(ctx, p.name)
                if (keys.isNotEmpty()) hasil[p.name] = keys.sorted()
            }
            hasil
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "Kembali")
                    }
                }
                Text("Proyek", style = MaterialTheme.typography.headlineMedium, color = JarColors.TextPrimary)
            }
            Button(
                onClick = picker::openFile,
                colors = ButtonDefaults.buttonColors(containerColor = JarColors.Accent, contentColor = JarColors.Black),
            ) { Text("Buka JAR") }
        }

        // ----- Smali yang sudah diubah -----
        Text(
            "Smali yang sudah diubah",
            style = MaterialTheme.typography.titleMedium,
            color = JarColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            JarCard {
                if (edited.isEmpty()) {
                    Text(
                        "Belum ada. Bongkar JAR ke Smali di Edit JAR, ubah file .smali-nya, lalu class dan method yang diubah muncul di sini.",
                        color = JarColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    edited.entries.forEachIndexed { i, (name, keys) ->
                        if (i > 0) RowDivider()
                        EditedSummary(name, keys) { onOpenEdited(name) }
                    }
                }
            }
        }

        // ----- Daftar proyek -----
        Text(
            "Semua proyek",
            style = MaterialTheme.typography.titleMedium,
            color = JarColors.TextPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            JarCard {
                Text(
                    "Proyek: ${projects.size}",
                    color = JarColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider(color = JarColors.Outline, modifier = Modifier.padding(horizontal = 16.dp))
                if (projects.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconTile(Icons.Outlined.Folder, size = 64.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Belum ada file terbaru. Pilih alat untuk memulai.",
                            color = JarColors.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    projects.forEachIndexed { i, dir ->
                        if (i > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(dir.name) }
                                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTile(Icons.Outlined.Folder)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    JarNames.display(dir.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarColors.TextPrimary,
                                )
                                Text(
                                    formatSize(File(dir, "original.jar").length()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarColors.TextMuted,
                                )
                            }
                            IconButton(onClick = { toDelete = dir }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = JarColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    SourcePickerDialogs(picker)
    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Hapus proyek?") },
            text = { Text("Proyek ${JarNames.display(d.name)} dan seluruh hasil ekstraknya akan dihapus dari penyimpanan aplikasi.") },
            confirmButton = {
                TextButton(onClick = { d.deleteRecursively(); toDelete = null; tick++ }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } },
        )
    }
}
