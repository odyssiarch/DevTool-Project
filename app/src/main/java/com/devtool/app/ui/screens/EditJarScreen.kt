package com.devtool.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.core.JarNames
import com.devtool.app.core.Workspace
import com.devtool.app.ui.components.JarTopBar
import com.devtool.app.ui.components.SourcePickerDialogs
import com.devtool.app.ui.components.rememberSourcePicker
import com.devtool.app.ui.theme.JarColors

// Warna khusus layar ini (selaras dengan layar utama)
private val EdStroke = Color(0xFF403E3B)
private val EdGlassTop = Color(0xFF353432)
private val EdGlassBottom = Color(0xFF2A2927)
private val EdOrange = Color(0xFFFFB067)
private val EdPurple = Color(0xFFB69CFF)

/**
 * Edit JAR: satu halaman.
 * Belum ada JAR: kartu pilih JAR dan alur 3 langkah.
 * Sudah ada JAR: bilah ringkas nama JAR lalu isi proyek (ProjectBody, tidak diubah).
 */
@Composable
fun EditJarScreen(onBack: () -> Unit, onExport: (String) -> Unit = {}) {
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
        if (p != null) {
            CurrentJarBar(
                name = JarNames.display(p),
                onFile = picker::openFile,
                onProjects = picker::openProjects,
            )
            // GANTI BARIS INI: Masukkan parameter onExport
            ProjectBody(p, true, onExport = onExport, modifier = Modifier.weight(1f))
        } else {
            EmptyEdit(
                onFile = picker::openFile,
                onProjects = picker::openProjects,
                modifier = Modifier.weight(1f),
            )
        }
    }
    SourcePickerDialogs(picker)
}

// ---------- Sudah ada JAR: bilah ringkas ----------
@Composable
private fun CurrentJarBar(name: String, onFile: () -> Unit, onProjects: () -> Unit) {
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        EditGlass(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Folder, 44)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("JAR yang diedit", color = JarColors.TextMuted, fontSize = 12.sp)
                        Text(name, color = JarColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Ganti file", Icons.Outlined.FolderOpen, filled = false, compact = true, onClick = onFile, modifier = Modifier.weight(1f))
                    PillButton("Proyek", Icons.Outlined.History, filled = false, compact = true, onClick = onProjects, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------- Belum ada JAR ----------
@Composable
private fun EmptyEdit(onFile: () -> Unit, onProjects: () -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            EditGlass(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp)) {
                    IconBadge(Icons.Outlined.FolderOpen, 56)
                    Spacer(Modifier.height(14.dp))
                    Text("Pilih JAR untuk diedit", color = JarColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ambil dari file di HP, atau lanjutkan proyek yang sudah pernah dibongkar.",
                        color = JarColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    PillButton("File JAR", Icons.Outlined.FolderOpen, filled = true, compact = false, onClick = onFile, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    PillButton("Proyek yang ada", Icons.Outlined.History, filled = false, compact = false, onClick = onProjects, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item {
            EditGlass(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Alur mengedit", color = JarColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Step("1", "Bongkar ke Smali", "JAR diubah jadi folder berisi file .smali", JarColors.Accent)
                    Step("2", "Edit file .smali", "Ubah kode di folder hasil bongkar", EdOrange)
                    Step("3", "Susun ulang JAR", "Hasilnya output.jar, diverifikasi otomatis", EdPurple)
                }
            }
        }
    }
}

@Composable
private fun Step(number: String, title: String, desc: String, tint: Color) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = JarColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ---------- Komponen kecil ----------
@Composable
private fun IconBadge(icon: ImageVector, sizeDp: Int) {
    val shape = RoundedCornerShape((sizeDp * 0.3f).dp)
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3B2D27), Color(0xFF2A2927))))
            .border(1.dp, Color(0xFF403E3B), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = JarColors.Accent, modifier = Modifier.size((sizeDp * 0.5f).dp))
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val h = if (compact) 44.dp else 52.dp
    val shape = RoundedCornerShape(h / 2)
    val bg = if (filled) {
        Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF3B2D27), Color(0xFF3B2D27))))
    } else {
        Modifier.background(Color(0xFF2A2927))
    }
    Row(
        modifier
            .height(h)
            .clip(shape)
            .then(bg)
            .border(1.dp, if (filled) Color(0xFF6B4636) else EdStroke, shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = JarColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = JarColors.TextPrimary, fontSize = if (compact) 14.sp else 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EditGlass(
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(EdGlassTop, EdGlassBottom)))
            .border(1.dp, EdStroke, shape),
    ) { content() }
}