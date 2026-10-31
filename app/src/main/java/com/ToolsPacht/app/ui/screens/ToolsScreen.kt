package com.jartool.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jartool.app.ui.theme.JarColors

data class ToolItem(val id: String, val title: String, val tag: String?, val desc: String, val icon: ImageVector)

private val tools = listOf(
    ToolItem("source", "Lihat kode sumber", "jadx", "Jelajahi paket dan class dari JAR", Icons.Outlined.Code),
    ToolItem("smali", "Edit JAR", "smali", "Edit Smali, lalu buat ulang JAR", Icons.Outlined.Edit),
    ToolItem("export", "Ekspor JAR / DEX", null, "Simpan JAR atau classes.dex dari proyek ke penyimpanan", Icons.Outlined.Download),
    ToolItem("compare", "Bandingkan JAR", null, "Bandingkan dua versi dan lihat class serta berkas yang berubah", Icons.Outlined.SwapVert),
    ToolItem("info", "Info JAR", null, "Daftar classes.dex, versi dex, jumlah class, dan API level", Icons.Outlined.Info),
)

@Composable
fun ToolsScreen(onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Alat JAR", style = MaterialTheme.typography.headlineMedium, color = JarColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
        }
        items(tools, key = { it.id }) { ToolCard(it) { onOpen(it.id) } }
    }
}

@Composable
private fun ToolCard(item: ToolItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JarColors.Surface),
        border = BorderStroke(1.dp, JarColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = JarColors.BlueDim, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = JarColors.Blue)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary, modifier = Modifier.weight(1f, fill = false))
                    item.tag?.let {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = JarColors.SurfaceHigh) {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = JarColors.TextMuted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(item.desc, style = MaterialTheme.typography.bodyMedium, color = JarColors.TextMuted)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = JarColors.TextMuted)
        }
    }
}
