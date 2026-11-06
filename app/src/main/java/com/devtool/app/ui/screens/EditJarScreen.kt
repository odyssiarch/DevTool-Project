package com.devtool.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.core.JarNames
import com.devtool.app.core.Workspace
import com.devtool.app.ui.components.FilePickRow
import com.devtool.app.ui.components.PillCard
import com.devtool.app.ui.components.ScreenHeader
import com.devtool.app.ui.components.SourcePickerDialogs
import com.devtool.app.ui.components.TonalAction
import com.devtool.app.ui.components.rememberSourcePicker
import com.devtool.app.ui.theme.JarColors

private val Amber = Color(0xFFFFC857)

/**
 * Edit JAR: satu halaman.
 * Belum ada JAR: kartu pilih berkas dan alur 3 langkah.
 * Sudah ada JAR: kartu ringkas nama JAR lalu isi proyek (ProjectBody, tidak diubah).
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
        val p = project
        ScreenHeader(
            "Edit JAR",
            if (p != null) "Bongkar, edit, lalu susun ulang" else "Pilih JAR untuk diedit",
            onBack,
        )
        if (p != null) {
            CurrentJarCard(
                name = JarNames.display(p),
                onFile = picker::openFile,
                onProjects = picker::openProjects,
            )
            Spacer(Modifier.height(8.dp))
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

// ---------- Sudah ada JAR ----------
@Composable
private fun CurrentJarCard(name: String, onFile: () -> Unit, onProjects: () -> Unit) {
    PillCard(modifier = Modifier.padding(horizontal = 16.dp), title = "JAR YANG DIEDIT") {
        Text(name, color = JarColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        Row {
            TonalAction(Icons.Outlined.FolderOpen, "Ganti file", Modifier.weight(1f), onFile)
            Spacer(Modifier.width(10.dp))
            TonalAction(Icons.Outlined.History, "Proyek", Modifier.weight(1f), onProjects)
        }
    }
}

// ---------- Belum ada JAR ----------
@Composable
private fun EmptyEdit(onFile: () -> Unit, onProjects: () -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PillCard(title = "BERKAS") {
                Text("File JAR", color = JarColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Belum dipilih", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(14.dp))
                FilePickRow(Icons.Outlined.FolderOpen, "Pilih File JAR", onFile)
                Spacer(Modifier.height(10.dp))
                FilePickRow(Icons.Outlined.History, "Proyek yang ada", onProjects)
            }
        }
        item {
            PillCard(title = "ALUR MENGEDIT") {
                Step("1", "Bongkar ke Smali", "JAR diubah jadi folder berisi file .smali")
                Step("2", "Edit file .smali", "Ubah kode di folder hasil bongkar")
                Step("3", "Susun ulang JAR", "Hasilnya output.jar, diverifikasi otomatis")
            }
        }
    }
}

@Composable
private fun Step(number: String, title: String, desc: String) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(JarColors.AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = JarColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = JarColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
