package com.devtool.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devtool.app.core.SmaliEngine
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.JarCard
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_BYTES = 600_000L

private val EBg = Color(0xFF05070C)
private val EBar = Color(0xFF0E131C)
private val EStroke = Color(0xFF2A3446)
private val EOrange = Color(0xFFFFB067)

private val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

// ---------- Kartu pembuka di halaman proyek ----------
@Composable
fun SmaliEditorEntry(project: String) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    JarCard {
        Column(Modifier.padding(16.dp)) {
            Text("Editor Smali", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Buka dan edit file .smali langsung di aplikasi, tanpa pengelola berkas lain.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = JarColors.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (SmaliEngine.hasSmali(ctx, project)) {
                        hint = null
                        show = true
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
    if (show) SmaliEditorDialog(project) { show = false }
}

// ---------- Jendela editor layar penuh ----------
@Composable
private fun SmaliEditorDialog(project: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val root = remember(project) { SmaliEngine.smaliRoot(ctx, project) }
    var dir by remember { mutableStateOf(root) }
    var openFile by remember { mutableStateOf<File?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun back() {
        val f = openFile
        when {
            f != null -> if (dirty) confirmDiscard = true else openFile = null
            dir != root -> dir = dir.parentFile ?: root
            else -> onClose()
        }
    }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxSize().background(EBg)) {
            val f = openFile
            if (f != null) {
                SmaliFileEditor(
                    file = f,
                    root = root,
                    onBack = { back() },
                    onDirtyChange = { dirty = it },
                )
            } else {
                SmaliBrowser(
                    root = root,
                    dir = dir,
                    onDir = { dir = it },
                    onOpen = { file ->
                        dir = file.parentFile ?: root
                        openFile = file
                    },
                    onBack = { back() },
                    onClose = onClose,
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Perubahan di file ini belum disimpan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    dirty = false
                    openFile = null
                }) { Text("Buang") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Batal") } },
        )
    }
}

// ---------- Penjelajah berkas + cari ----------
@Composable
private fun SmaliBrowser(
    root: File,
    dir: File,
    onDir: (File) -> Unit,
    onOpen: (File) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<File>?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        results = withContext(Dispatchers.IO) {
            root.walkTopDown()
                .onEnter { it == root || !it.name.startsWith("_") }
                .filter { it.isFile && it.name.endsWith(".smali") && it.name.contains(q, ignoreCase = true) }
                .take(200)
                .toList()
        }
        searching = false
    }

    val entries = remember(dir) {
        dir.listFiles()
            ?.filter { !it.name.startsWith("_") && it.name != "layout.txt" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .orEmpty()
    }

    Row(
        Modifier.fillMaxWidth().background(EBar).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = JarColors.TextPrimary) }
        Column(Modifier.weight(1f)) {
            Text("Editor Smali", color = JarColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                dir.relativeTo(root).path.ifEmpty { "Semua dex" },
                color = JarColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null, tint = JarColors.TextMuted) }
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
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = JarColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(JarColors.Blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, null, tint = JarColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }

    val found = results
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        if (found != null) {
            item {
                Text(
                    if (searching) "Mencari..." else "${found.size} hasil" + if (found.size >= 200) " (dibatasi 200)" else "",
                    color = JarColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(found, key = { it.absolutePath }) { f ->
                FileRow(
                    folder = false,
                    title = f.name.removeSuffix(".smali"),
                    subtitle = f.parentFile?.relativeTo(root)?.path.orEmpty(),
                    onClick = { onOpen(f) },
                )
            }
        } else {
            if (entries.isEmpty()) {
                item { Text("Folder ini kosong.", color = JarColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp)) }
            }
            items(entries, key = { it.absolutePath }) { f ->
                if (f.isDirectory) {
                    FileRow(folder = true, title = f.name, subtitle = null, onClick = { onDir(f) })
                } else {
                    FileRow(folder = false, title = f.name.removeSuffix(".smali"), subtitle = formatSize(f.length()), onClick = { onOpen(f) })
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
private fun SmaliFileEditor(
    file: File,
    root: File,
    onBack: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember(file) { mutableStateOf(false) }
    var tooBig by remember(file) { mutableStateOf(false) }
    var original by remember(file) { mutableStateOf("") }
    var text by remember(file) { mutableStateOf(TextFieldValue("")) }
    var msg by remember(file) { mutableStateOf<String?>(null) }
    val backup = remember(file) { File(root, "_backup/" + file.relativeTo(root).path) }
    var hasBackup by remember(file) { mutableStateOf(backup.exists()) }

    LaunchedEffect(file) {
        if (file.length() > MAX_BYTES) {
            tooBig = true
        } else {
            val s = withContext(Dispatchers.IO) { file.readText() }
            original = s
            text = TextFieldValue(s)
        }
        loaded = true
    }

    val dirty = loaded && !tooBig && text.text != original
    LaunchedEffect(dirty) {
        onDirtyChange(dirty)
        if (dirty) msg = null
    }

    fun save() {
        val content = text.text
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // cadangkan versi asli sekali saja, sebelum tersimpan pertama kali
                    if (!backup.exists()) {
                        backup.parentFile?.mkdirs()
                        file.copyTo(backup)
                    }
                    file.writeText(content)
                }
                original = content
                hasBackup = true
                msg = "Tersimpan"
            } catch (e: Exception) {
                msg = "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun restore() {
        scope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    backup.copyTo(file, overwrite = true)
                    file.readText()
                }
                original = s
                text = TextFieldValue(s)
                msg = "Dikembalikan ke versi asli"
            } catch (e: Exception) {
                msg = "Gagal mengembalikan: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().background(EBar).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = JarColors.TextPrimary) }
        Column(Modifier.weight(1f)) {
            Text(
                file.name.removeSuffix(".smali"),
                color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                msg ?: if (dirty) "Belum disimpan" else "Tidak ada perubahan",
                color = if (dirty) EOrange else JarColors.TextMuted, fontSize = 12.sp,
            )
        }
        if (hasBackup) {
            IconButton(onClick = { restore() }) { Icon(Icons.Outlined.Restore, null, tint = JarColors.TextMuted) }
        }
        IconButton(onClick = { save() }, enabled = dirty) {
            Icon(Icons.Outlined.Save, null, tint = if (dirty) JarColors.Blue else JarColors.TextMuted)
        }
    }

    when {
        !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Membuka...", color = JarColors.TextMuted)
        }
        tooBig -> Text(
            "Berkas ini ${formatSize(file.length())}, terlalu besar untuk editor di aplikasi (batas ${formatSize(MAX_BYTES)}).",
            color = JarColors.TextMuted, modifier = Modifier.padding(20.dp),
        )
        else -> {
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
                        onValueChange = { text = it },
                        textStyle = codeStyle.copy(color = JarColors.TextPrimary),
                        cursorBrush = SolidColor(JarColors.Blue),
                        modifier = Modifier.widthIn(min = 240.dp).padding(end = 24.dp),
                    )
                }
            }
        }
    }
}
