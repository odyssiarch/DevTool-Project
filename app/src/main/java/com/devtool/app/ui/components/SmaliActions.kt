package com.devtool.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtool.app.ui.theme.JarColors
import com.devtool.app.ui.viewmodel.SmaliJobViewModel
import com.devtool.app.ui.viewmodel.vmFactory

/** Kartu "Edit Smali": tombol bongkar ke Smali dan susun ulang JAR. */
@Composable
fun SmaliActions(project: String, onExport: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    // Proses bongkar/rakit hidup di ViewModel (satu per proyek), bukan di composable.
    val vm: SmaliJobViewModel = viewModel(
        key = "smali-job-$project",
        factory = vmFactory { SmaliJobViewModel(appCtx, project) },
    )
    var konfirmasiTimpa by rememberSaveable { mutableStateOf(false) }

    if (konfirmasiTimpa) {
        AlertDialog(
            onDismissRequest = { konfirmasiTimpa = false },
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Timpa hasil bongkar?") },
            text = {
                Text("Folder Smali untuk proyek ini sudah ada. Membongkar ulang akan menghapus semua edit .smali di dalamnya.")
            },
            confirmButton = {
                TextButton(onClick = { konfirmasiTimpa = false; vm.bongkar() }) { Text("Bongkar ulang") }
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
                onClick = { if (vm.hasSmali) konfirmasiTimpa = true else vm.bongkar() },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bongkar ke Smali") }
            Button(
                onClick = { vm.susunUlang() },
                enabled = !vm.busy && vm.hasSmali,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Susun ulang JAR") }
            
            if (vm.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            if (vm.lines.isNotEmpty()) {
                Text(
                    vm.lines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = JarColors.TextMuted,
                )
            }
            
            // TOMBOL EKSPOR HANYA MUNCUL JIKA BUILD BARU SAJA SUKSES
            if (vm.buildSukses && vm.outputAda) {
                Text(
                    "output.jar berhasil dibuat di:\n${vm.outputPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarColors.TextPrimary, // Lebih terang biar user sadar build sukses
                )
                Button(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarColors.Blue,
                        contentColor = JarColors.Black
                    ),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ekspor JAR / DEX Sekarang")
                }
            }
        }
    }
}