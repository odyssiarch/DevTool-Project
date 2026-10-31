package com.jartool.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jartool.app.core.DECOMPILE_MODES
import com.jartool.app.core.Prefs
import com.jartool.app.ui.components.*
import com.jartool.app.ui.theme.JarColors
import kotlin.math.roundToInt

/** Lihat kode sumber: pilih JAR, atur opsi, lalu buka penjelajah class. */
@Composable
fun DecompileScreen(onBack: () -> Unit, onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberSourcePicker(onPicked)
    var mode by remember { mutableIntStateOf(Prefs.decompileMode(ctx)) }
    var threads by remember { mutableIntStateOf(Prefs.threads(ctx)) }
    var dialog by remember { mutableIntStateOf(0) } // 1 = opsi, 2 = thread

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JarTopBar("Lihat kode sumber", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Sumber file",
                color = JarColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            JarCard {
                SourceRow(Icons.Outlined.Folder, "File JAR", picker::openFile)
                RowDivider()
                SourceRow(Icons.Outlined.History, "Proyek yang ada", picker::openProjects)
            }
            Spacer(Modifier.height(16.dp))
            JarCard {
                DetailRow(Icons.Outlined.Tune, "Opsi dekompilasi", DECOMPILE_MODES[mode]) { dialog = 1 }
                RowDivider()
                DetailRow(Icons.Outlined.Memory, "Thread dekompilasi", "$threads thread, maksimum 8") { dialog = 2 }
                Text(
                    "Lebih banyak thread memakai lebih banyak memori. Memori rendah bisa membuat dekompilasi gagal. Mulai dari 1 thread agar lebih stabil.",
                    color = JarColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                "Semua diproses di perangkat. Tidak perlu internet.",
                color = JarColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 24.dp),
            )
        }
    }

    if (dialog == 1) {
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Opsi dekompilasi") },
            text = {
                Column {
                    DECOMPILE_MODES.forEachIndexed { i, label ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { mode = i; Prefs.setDecompileMode(ctx, i); dialog = 0 }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = mode == i,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = JarColors.Blue,
                                    unselectedColor = JarColors.TextMuted,
                                ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = 0 }) { Text("Tutup") } },
        )
    }
    if (dialog == 2) {
        AlertDialog(
            onDismissRequest = { Prefs.setThreads(ctx, threads); dialog = 0 },
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Thread dekompilasi") },
            text = {
                Column {
                    Text("$threads thread", color = JarColors.TextPrimary)
                    Slider(
                        value = threads.toFloat(),
                        onValueChange = { threads = it.roundToInt() },
                        valueRange = 1f..8f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = JarColors.Blue,
                            activeTrackColor = JarColors.Blue,
                            inactiveTrackColor = JarColors.Outline,
                        ),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { Prefs.setThreads(ctx, threads); dialog = 0 }) { Text("Selesai") } },
        )
    }
    SourcePickerDialogs(picker)
}
