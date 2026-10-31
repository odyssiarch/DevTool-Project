package com.jartool.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Proyek", style = MaterialTheme.typography.headlineMedium, color = JarColors.TextPrimary)
            Button(
                onClick = picker::openFile,
                colors = ButtonDefaults.buttonColors(containerColor = JarColors.Blue, contentColor = JarColors.Black),
            ) { Text("Buka JAR") }
        }
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Belum ada proyek. Buka berkas JAR untuk membuat proyek pertama.", color = JarColors.TextMuted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects, key = { it.name }) { dir ->
                    JarCard {
                        Row(
                            Modifier.clickable { onOpen(dir.name) }.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
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
