package com.jartool.app.ui.screens

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
import com.jartool.app.core.Workspace
import com.jartool.app.core.formatSize
import com.jartool.app.ui.components.*
import com.jartool.app.ui.theme.JarColors
import java.io.File

@Composable
fun ProjectsScreen(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val projects = remember(tick) { Workspace.projects(ctx) }
    var toDelete by remember { mutableStateOf<File?>(null) }
    val picker = rememberSourcePicker { onOpen(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Proyek", style = MaterialTheme.typography.headlineMedium, color = JarColors.TextPrimary)
            Button(
                onClick = picker::openFile,
                colors = ButtonDefaults.buttonColors(containerColor = JarColors.Blue, contentColor = JarColors.Black),
            ) { Text("Buka JAR") }
        }
        Box(Modifier.padding(horizontal = 16.dp)) {
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
                                Text(dir.name, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
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
            text = { Text("Proyek ${d.name} dan seluruh hasil ekstraknya akan dihapus dari penyimpanan aplikasi.") },
            confirmButton = {
                TextButton(onClick = { d.deleteRecursively(); toDelete = null; tick++ }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } },
        )
    }
}
