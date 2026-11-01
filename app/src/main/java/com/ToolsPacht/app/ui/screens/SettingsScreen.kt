package com.jartool.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jartool.app.core.AppInfo
import com.jartool.app.ui.components.JarCard
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
            Column(Modifier.padding(20.dp)) {
                // Header: ikon besar + nama + chip versi + tagline
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(JarColors.BlueDim)
                            .border(1.dp, JarColors.Outline, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = null,
                            tint = JarColors.Blue,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                appName,
                                style = MaterialTheme.typography.titleMedium,
                                color = JarColors.TextPrimary,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            InfoChip("v$version", accent = true)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            AppInfo.TAGLINE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarColors.TextMuted,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = JarColors.Outline)
                Spacer(Modifier.height(14.dp))

                // Info: lebar label tetap, nilai rata kiri sejajar
                InfoRow("Pengembang") {
                    Text(
                        AppInfo.DEVELOPER,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = JarColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("Format dex") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip("035 – 041")
                        InfoChip("Kontainer")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "KONFIGURASI",
            style = MaterialTheme.typography.labelSmall,
            color = JarColors.TextMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        SettingRow("API level target", "Menentukan versi dex saat smali merakit ulang")
        SettingRow("Folder kerja", "Tempat hasil ekstrak, Smali, dan JAR baru")
        SettingRow("Classpath framework", "Berkas framework untuk recompile services.jar")
    }
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = JarColors.TextMuted,
            modifier = Modifier.width(104.dp),
        )
        value()
    }
}

@Composable
private fun InfoChip(text: String, accent: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (accent) JarColors.BlueDim else JarColors.SurfaceHigh)
            .border(1.dp, JarColors.Outline, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) JarColors.Blue else JarColors.TextPrimary,
        )
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
