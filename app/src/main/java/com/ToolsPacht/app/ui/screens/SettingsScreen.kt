package com.jartool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jartool.app.ui.theme.JarColors

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Setelan", style = MaterialTheme.typography.headlineMedium, color = JarColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        SettingRow("API level target", "Menentukan versi dex saat smali merakit ulang")
        SettingRow("Folder kerja", "Tempat hasil ekstrak, Smali, dan JAR baru")
        SettingRow("Classpath framework", "Berkas framework untuk recompile services.jar")
    }
}

@Composable
private fun SettingRow(title: String, sub: String) {
    ListItem(
        headlineContent = { Text(title, color = JarColors.TextPrimary) },
        supportingContent = { Text(sub, color = JarColors.TextMuted) },
        colors = ListItemDefaults.colors(containerColor = JarColors.Black),
    )
    HorizontalDivider(color = JarColors.Outline)
}
