package com.jartool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jartool.app.core.AppInfo
import com.jartool.app.ui.components.IconTile
import com.jartool.app.ui.components.JarCard
import com.jartool.app.ui.components.SummaryRow
import com.jartool.app.ui.theme.JarColors

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val appName = remember { ctx.applicationInfo.loadLabel(ctx.packageManager).toString() }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Setelan", style = MaterialTheme.typography.headlineMedium, color = JarColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        JarCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.Code)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(appName, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                        Text(AppInfo.TAGLINE, style = MaterialTheme.typography.bodyMedium, color = JarColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                SummaryRow("Pengembang", AppInfo.DEVELOPER)
                SummaryRow("Versi", version)
                SummaryRow("Format dex", "035 sampai 041, termasuk kontainer")
            }
        }
        Spacer(Modifier.height(16.dp))
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
