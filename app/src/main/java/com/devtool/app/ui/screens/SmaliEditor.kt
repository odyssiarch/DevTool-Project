package com.devtool.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtool.app.core.SmaliEngine
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.JarCard
import com.devtool.app.ui.theme.JarColors
import com.devtool.app.ui.viewmodel.SmaliEditorViewModel
import com.devtool.app.ui.viewmodel.vmFactory
import java.io.File

private val EBg = Color(0xFF05070C)
private val EBar = Color(0xFF0E131C)
private val EStroke = Color(0xFF2A3446)
private val EOrange = Color(0xFFFFB067)

private val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

/** ViewModel editor untuk satu proyek. Kartu pembuka dan jendela editor memakai instance yang sama. */
@Composable
private fun rememberEditorVm(project: String): SmaliEditorViewModel {
    val appCtx = LocalContext.current.applicationContext
    return viewModel(
        key = "smali-editor-$project",
        factory = vmFactory { SmaliEditorViewModel(SmaliEngine.smaliRoot(appCtx, project)) },
    )
}

// ---------- Kartu pembuka di halaman proyek ----------
@Composable
fun SmaliEditorEntry(project: String) {
    val ctx = LocalContext.current
    val vm = rememberEditorVm(project)
    var hint by rememberSaveable { mutableStateOf<String?>(null) }

    JarCard {
        Column(Modifier.padding(16.dp)) {
            Text("Editor Smali", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Buka dan edit file .smali langsung di aplikasi, tanpa pengelola berkas lain.",
                style = MaterialTheme.typography.bodyMedium,
                color = JarColors.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (SmaliEngine.hasSmali(ctx, project)) {
                        hint = null
                        vm.open()
                    } else {
                        hint = "Bongkar ke Smali dulu."
                    }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Buka editor") }
            hint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = EOrange, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Jendela editor layar penuh. Dipasang di luar daftar yang bisa di-scroll (lihat ProjectBody),
 * supaya tetap muncul setelah layar diputar walau kartu pembukanya sedang tidak tampil.
 */
@Composable
fun SmaliEditorHost(project: String) {
    val vm = rememberEditorVm(project)
    if (vm.visible) SmaliEditorDialog(vm)
}

// ---------- Jendela editor layar penuh ----------
@Composable
private fun SmaliEditorDialog(vm: SmaliEditorViewModel) {
    Dialog(
        onDismissRequest = { vm.back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(EBg)) {
            val f = vm.openFile
            if (f != null) SmaliFileEditor(vm, f) else SmaliBrowser(vm)
        }
    }

    if (vm.confirmDiscard) {
        AlertDialog(
            onDismissRequest = { vm.cancelDiscard() },
            title = { Text("Buang perubahan?") },
            text = { Text("Perubahan di file ini belum disimpan.") },
            confirmButton = { TextButton(onClick = { vm.discardChanges() }) { Text("Buang") } },
            dismissButton = { TextButton(onClick = { vm.cancelDiscard() }) { Text("Batal") } },
        )
    }
}

// ---------- Penjelajah berkas + cari ----------
@Composable
private fun SmaliBrowser(vm: SmaliEditorViewModel) {
    val query = vm.query

    Row(
        Modifier.fillMaxWidth().background(EBar).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.back() }) { Icon(Icons.Outlined.ArrowBack, null, tint = JarColors.TextPrimary) }
        Column(Modifier.weight(1f)) {
            Text("Editor Smali", color = JarColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                vm.dir.relativeTo(vm.root).path.ifEmpty { "Semua dex" },
                color = JarColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { vm.close() }) { Icon(Icons.Outlined.Close, null, tint = JarColors.TextMuted) }
    }

    // Kolom cari
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF111722))
            .border(1.dp, EStroke, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = JarColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Cari nama class (min. 2 huruf)", color = JarColors.TextMuted, fontSize = 14.sp)
            BasicTextField(
                value = query,
                onValueChange = { vm.onQueryChange(it) },
                singleLine = true,
                textStyle = TextStyle(color = JarColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(JarColors.Blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { vm.onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, null, tint = JarColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }

    val found = vm.results
    val entries = vm.entries
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        if (found != null) {
            item {
                Text(
                    if (vm.searching) "Mencari..." else "${found.size} hasil" + if (found.size >= 200) " (dibatasi 200)" else "",
                    color = JarColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(found, key = { it.absolutePath }) { f ->
                FileRow(
                    folder = false,
                    title = f.name.removeSuffix(".smali"),
                    subtitle = f.parentFile?.relativeTo(vm.root)?.path.orEmpty(),
                    onClick = { vm.openEditor(f) },
                )
            }
        } else {
            if (entries.isEmpty()) {
                item { Text("Folder ini kosong.", color = JarColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp)) }
            }
            items(entries, key = { it.absolutePath }) { f ->
                if (f.isDirectory) {
                    FileRow(folder = true, title = f.name, subtitle = null, onClick = { vm.goDir(f) })
                } else {
                    FileRow(folder = false, title = f.name.removeSuffix(".smali"), subtitle = formatSize(f.length()), onClick = { vm.openEditor(f) })
                }
            }
        }
    }
}

@Composable
private fun FileRow(folder: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (folder) Icons.Outlined.Folder else Icons.Outlined.Description,
            null,
            tint = if (folder) JarColors.Blue else JarColors.TextMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = JarColors.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, color = JarColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- Editor satu berkas ----------
@Composable
private fun SmaliFileEditor(vm: SmaliEditorViewModel, file: File) {
    val dirty = vm.dirty
    val blocked = vm.blocked

    Row(
        Modifier.fillMaxWidth().background(EBar).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.back() }) { Icon(Icons.Outlined.ArrowBack, null, tint = JarColors.TextPrimary) }
        Column(Modifier.weight(1f)) {
            Text(
                file.name.removeSuffix(".smali"),
                color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                vm.msg ?: if (dirty) "Belum disimpan" else "Tidak ada perubahan",
                color = if (dirty) EOrange else JarColors.TextMuted, fontSize = 12.sp,
            )
        }
        if (vm.hasBackup) {
            IconButton(onClick = { vm.restore() }) { Icon(Icons.Outlined.Restore, null, tint = JarColors.TextMuted) }
        }
        IconButton(onClick = { vm.save() }, enabled = dirty) {
            Icon(Icons.Outlined.Save, null, tint = if (dirty) JarColors.Blue else JarColors.TextMuted)
        }
    }

    when {
        !vm.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Membuka...", color = JarColors.TextMuted)
        }
        blocked != null -> Text(
            blocked,
            color = JarColors.TextMuted, modifier = Modifier.padding(20.dp),
        )
        else -> {
            val text = vm.text
            val lineCount = remember(text.text) { text.text.count { it == '\n' } + 1 }
            val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            Row(Modifier.fillMaxSize().verticalScroll(vScroll).padding(top = 8.dp, bottom = 48.dp)) {
                Text(
                    gutter,
                    style = codeStyle.copy(color = JarColors.TextMuted, textAlign = TextAlign.End),
                    modifier = Modifier.padding(horizontal = 8.dp).widthIn(min = 28.dp),
                )
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { vm.onTextChange(it) },
                        textStyle = codeStyle.copy(color = JarColors.TextPrimary),
                        cursorBrush = SolidColor(JarColors.Blue),
                        modifier = Modifier.widthIn(min = 240.dp).padding(end = 24.dp),
                    )
                }
            }
        }
    }
}
