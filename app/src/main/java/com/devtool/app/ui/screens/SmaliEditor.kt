package com.devtool.app.ui.screens

import com.devtool.app.core.SmaliFiles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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

// Berkas di atas batas ini dibuka per method (tidak sebagai satu teks utuh)
private const val MAX_BYTES = 600_000L

// Pewarnaan sintaks dimatikan untuk teks yang lebih panjang dari ini (supaya tidak lag)
private const val HIGHLIGHT_MAX = 250_000

private val EBg = Color(0xFF1F1E1D)
private val EBar = Color(0xFF2A2927)
private val EStroke = Color(0xFF403E3B)
private val EOrange = Color(0xFFFFB067)

private val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

private data class Chunk(val start: Int, val end: Int, val title: String)
private data class TreeItem(val file: File, val depth: Int)
private class BackHolder { var f: (() -> Unit)? = null }

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
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var openFile by remember { mutableStateOf<File?>(null) }
    val treeState = rememberLazyListState()
    val holder = remember { BackHolder() }

    fun back() {
        if (openFile != null) {
            val h = holder.f
            if (h != null) h() else openFile = null
        } else {
            onClose()
        }
    }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(EBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            val f = openFile
            if (f != null) {
                key(f.absolutePath) {
                    SmaliFileScreen(
                        file = f,
                        root = root,
                        onExit = {
                            holder.f = null
                            openFile = null
                        },
                        holder = holder,
                    )
                }
            } else {
                SmaliBrowser(
                    root = root,
                    expanded = expanded,
                    onToggle = { d ->
                        val p = d.absolutePath
                        expanded = if (p in expanded) expanded - p else expanded + p
                    },
                    onOpen = { file ->
                        val parents = mutableSetOf<String>()
                        var p = file.parentFile
                        while (p != null && p != root) {
                            parents.add(p.absolutePath)
                            p = p.parentFile
                        }
                        expanded = expanded + parents
                        openFile = file
                    },
                    treeState = treeState,
                    onBack = { back() },
                )
            }
        }
    }
}

// ---------- Penjelajah berkas: pohon berakar + cari nama class ----------
@Composable
private fun SmaliBrowser(
    root: File,
    expanded: Set<String>,
    onToggle: (File) -> Unit,
    onOpen: (File) -> Unit,
    treeState: LazyListState,
    onBack: () -> Unit,
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

    val rows = remember(expanded) {
        ArrayList<TreeItem>().also { flatten(root, 0, expanded, it) }
    }

    Column(Modifier.fillMaxSize()) {
        EditorHeader("Editor Smali", "Pilih class untuk diedit", JarColors.TextMuted, onBack)

        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2A2927))
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
                    cursorBrush = SolidColor(JarColors.Accent),
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
        if (found != null) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                item {
                    Text(
                        if (searching) "Mencari..." else "${found.size} hasil" + if (found.size >= 200) " (dibatasi 200)" else "",
                        color = JarColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                items(found, key = { it.absolutePath }) { f ->
                    FileRow(
                        title = f.name.removeSuffix(".smali"),
                        subtitle = f.parentFile?.relativeTo(root)?.path.orEmpty(),
                        onClick = { onOpen(f) },
                    )
                }
            }
        } else {
            LazyColumn(
                state = treeState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(rows, key = { it.file.absolutePath }) { item ->
                    val open = item.file.absolutePath in expanded
                    TreeRow(item, open) {
                        if (item.file.isDirectory) onToggle(item.file) else onOpen(item.file)
                    }
                }
            }
        }
    }
}

private fun children(dir: File): List<File> =
    dir.listFiles()
        ?.filter { !it.name.startsWith("_") && it.name != "layout.txt" }
        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .orEmpty()

private fun flatten(dir: File, depth: Int, expanded: Set<String>, out: MutableList<TreeItem>) {
    for (f in children(dir)) {
        out.add(TreeItem(f, depth))
        if (f.isDirectory && f.absolutePath in expanded) flatten(f, depth + 1, expanded, out)
    }
}

@Composable
private fun TreeRow(item: TreeItem, open: Boolean, onClick: () -> Unit) {
    val f = item.file
    val dir = f.isDirectory
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (8 + item.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dir) {
            Icon(
                if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                null, tint = JarColors.TextMuted, modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
        Spacer(Modifier.size(6.dp))
        Icon(
            if (dir) (if (open) Icons.Outlined.FolderOpen else Icons.Outlined.Folder) else Icons.Outlined.Description,
            null,
            tint = if (dir) JarColors.Accent else JarColors.TextMuted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            f.name.removeSuffix(".smali"),
            color = JarColors.TextPrimary, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!dir) {
            val len = f.length()
            Text(formatSize(len), color = if (len > MAX_BYTES) EOrange else JarColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FileRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Description, null, tint = JarColors.TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = JarColors.TextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, color = JarColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- Header dua baris: judul, lalu deretan ikon aksi ----------
@Composable
private fun EditorHeader(
    title: String,
    status: String?,
    statusColor: Color,
    onBack: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().background(EBar)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = JarColors.TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = JarColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (status != null) {
                    Text(status, color = statusColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(EStroke))
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    enabled: Boolean,
    tint: Color = JarColors.TextPrimary,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, tint = if (enabled) tint else JarColors.TextMuted.copy(alpha = 0.35f))
    }
}

// ---------- Satu berkas: utuh, atau per method kalau besar ----------
@Composable
private fun SmaliFileScreen(file: File, root: File, onExit: () -> Unit, holder: BackHolder) {
    val scope = rememberCoroutineScope()
    val big = remember(file) { file.length() > MAX_BYTES }
    val backup = remember(file) { File(root, "_backup/" + file.relativeTo(root).path) }
    var hasBackup by remember(file) { mutableStateOf(backup.exists()) }
    var dirty by remember(file) { mutableStateOf(false) }
    var confirmDiscard by remember(file) { mutableStateOf(false) }
    var confirmRestoreAll by remember(file) { mutableStateOf(false) }
    var text0 by remember(file) { mutableStateOf<String?>(null) }
    var chunk by remember(file) { mutableStateOf<Chunk?>(null) }
    var listMsg by remember(file) { mutableStateOf<String?>(null) }
    val name = file.name.removeSuffix(".smali")

    LaunchedEffect(file) {
        text0 = withContext(Dispatchers.IO) { file.readText() }
    }

    fun goBack() {
        when {
            chunk != null -> if (dirty) confirmDiscard = true else chunk = null
            !big && dirty -> confirmDiscard = true
            else -> onExit()
        }
    }
    SideEffect { holder.f = { goBack() } }

    val saveWhole: suspend (String) -> String? = { s ->
    try {
        withContext(Dispatchers.IO) { SmaliFiles.save(root, file, s) }
        hasBackup = true
        null
    } catch (e: Exception) {
        "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}"
    }
}

    val restoreWhole: suspend () -> String? = {
    try {
        withContext(Dispatchers.IO) { SmaliFiles.restore(root, file) } // <--- Ganti bagian ini
    } catch (e: Exception) {
        null
    }
}

    fun saveChunk(c: Chunk): suspend (String) -> String? = { newText ->
    try {
        val cur = (text0 ?: "").split('\n')
        val newLines = newText.split('\n')
        val joined = (cur.subList(0, c.start) + newLines + cur.subList(c.end + 1, cur.size)).joinToString("\n")
        withContext(Dispatchers.IO) { SmaliFiles.save(root, file, joined) } // <--- Ganti bagian ini
        text0 = joined
        hasBackup = true
        chunk = Chunk(c.start, c.start + newLines.size - 1, c.title)
        null
    } catch (e: Exception) {
        "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}"
    }
}

    val t0 = text0
    val c = chunk
    when {
        t0 == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Membuka...", color = JarColors.TextMuted)
        }
        !big -> key("whole") {
            CodeEditor(
                title = name,
                subtitle = null,
                firstLine = 1,
                initial = t0,
                onSave = saveWhole,
                onRestore = if (hasBackup) restoreWhole else null,
                onDirtyChange = { dirty = it },
                onBack = { goBack() },
            )
        }
        c == null -> {
            val chunks = remember(t0) { parseChunks(t0.split('\n')) }
            MethodList(
                name = name,
                sizeText = formatSize(file.length()),
                chunks = chunks,
                message = listMsg,
                canRestore = hasBackup,
                onRestore = { confirmRestoreAll = true },
                onOpen = { listMsg = null; chunk = it },
                onBack = { goBack() },
            )
        }
        else -> key(c.start) {
            val initial = remember { t0.split('\n').subList(c.start, c.end + 1).joinToString("\n") }
            CodeEditor(
                title = name,
                subtitle = c.title,
                firstLine = c.start + 1,
                initial = initial,
                onSave = saveChunk(c),
                onRestore = null,
                onDirtyChange = { dirty = it },
                onBack = { goBack() },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Perubahan belum disimpan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    dirty = false
                    if (chunk != null) chunk = null else onExit()
                }) { Text("Buang") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Batal") } },
        )
    }

    if (confirmRestoreAll) {
        AlertDialog(
            onDismissRequest = { confirmRestoreAll = false },
            title = { Text("Kembalikan ke versi asli?") },
            text = { Text("Semua perubahan pada berkas ini akan hilang.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestoreAll = false
                    scope.launch {
                        val s = restoreWhole()
                        if (s != null) {
                            text0 = s
                            listMsg = "Dikembalikan ke versi asli"
                        } else {
                            listMsg = "Gagal mengembalikan"
                        }
                    }
                }) { Text("Kembalikan") }
            },
            dismissButton = { TextButton(onClick = { confirmRestoreAll = false }) { Text("Batal") } },
        )
    }
}

/** Pecah berkas smali jadi bagian yang bisa diedit: kepala class + tiap method. */
private fun parseChunks(lines: List<String>): List<Chunk> {
    val out = ArrayList<Chunk>()
    var firstStart = -1
    var i = 0
    while (i < lines.size) {
        val l = lines[i]
        if (l.startsWith(".method ")) {
            if (firstStart < 0) firstStart = i
            var j = i + 1
            while (j < lines.size && !lines[j].startsWith(".end method")) j++
            out.add(Chunk(i, minOf(j, lines.size - 1), l.removePrefix(".method ").trim()))
            i = j + 1
        } else {
            i++
        }
    }
    if (firstStart > 0) out.add(0, Chunk(0, firstStart - 1, "Kepala class dan field"))
    return out
}

// ---------- Daftar method (berkas besar) ----------
@Composable
private fun MethodList(
    name: String,
    sizeText: String,
    chunks: List<Chunk>,
    message: String?,
    canRestore: Boolean,
    onRestore: () -> Unit,
    onOpen: (Chunk) -> Unit,
    onBack: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val shown = remember(chunks, q) {
        val t = q.trim()
        if (t.isEmpty()) chunks else chunks.filter { it.title.contains(t, ignoreCase = true) }
    }
    val status = message ?: "Berkas besar ($sizeText), dibuka per method"
    val statusColor = if (message != null) JarColors.Accent else JarColors.TextMuted

    Column(Modifier.fillMaxSize()) {
        if (canRestore) {
            EditorHeader(name, status, statusColor, onBack) {
                ActionIcon(Icons.Outlined.Restore, true) { onRestore() }
            }
        } else {
            EditorHeader(name, status, statusColor, onBack)
        }

        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2A2927))
                .border(1.dp, EStroke, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = JarColors.TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Cari method (${chunks.size})", color = JarColors.TextMuted, fontSize = 14.sp)
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = JarColors.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(JarColors.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(shown, key = { it.start }) { m ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen(m) }
                        .padding(horizontal = 6.dp, vertical = 11.dp),
                ) {
                    Text(
                        m.title,
                        color = JarColors.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text("baris ${m.start + 1} - ${m.end + 1}", color = JarColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- Editor kode ----------
private val SYMBOLS = listOf(
    "⇥" to "    ", "->" to "->", "{" to "{", "}" to "}", "(" to "(", ")" to ")",
    "," to ",", "." to ".", ";" to ";", ":" to ":", "\"" to "\"", "[" to "[", "]" to "]",
    "/" to "/", "=" to "=", "_" to "_", "<" to "<", ">" to ">", "$" to "$",
    "v" to "v", "p" to "p", "L" to "L",
)

@Composable
private fun CodeEditor(
    title: String,
    subtitle: String?,
    firstLine: Int,
    initial: String,
    onSave: suspend (String) -> String?,
    onRestore: (suspend () -> String?)?,
    onDirtyChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val focus = remember { FocusRequester() }

    var original by remember { mutableStateOf(initial) }
    var text by remember { mutableStateOf(TextFieldValue(initial)) }
    val undo = remember { mutableStateListOf<TextFieldValue>() }
    val redo = remember { mutableStateListOf<TextFieldValue>() }
    var lastPush by remember { mutableStateOf(0L) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var matchIdx by remember { mutableStateOf(0) }

    val dirty = text.text != original
    LaunchedEffect(dirty) {
        onDirtyChange(dirty)
        if (dirty) msg = null
    }

    fun onEdit(v: TextFieldValue) {
        if (v.text != text.text) {
            val now = System.currentTimeMillis()
            if (undo.isEmpty() || now - lastPush > 700) {
                undo.add(text)
                if (undo.size > 40) undo.removeAt(0)
            }
            lastPush = now
            redo.clear()
        }
        text = v
    }

    fun doUndo() {
        if (undo.isNotEmpty()) {
            redo.add(text)
            text = undo.removeAt(undo.lastIndex)
            lastPush = 0L
        }
    }

    fun doRedo() {
        if (redo.isNotEmpty()) {
            undo.add(text)
            text = redo.removeAt(redo.lastIndex)
            lastPush = 0L
        }
    }

    fun insert(s: String) {
        val sel = text.selection
        val a = minOf(sel.start, sel.end)
        val b = maxOf(sel.start, sel.end)
        onEdit(TextFieldValue(text.text.replaceRange(a, b, s), TextRange(a + s.length)))
    }

    fun save() {
        val content = text.text
        scope.launch {
            val err = onSave(content)
            if (err == null) {
                original = content
                msg = "Tersimpan"
            } else {
                msg = err
            }
        }
    }

    // ----- pencarian di dalam berkas -----
    val matches = remember(text.text, findQuery, caseSensitive, findOpen) {
        val out = ArrayList<Int>()
        if (findOpen && findQuery.isNotEmpty()) {
            val src = text.text
            var i = src.indexOf(findQuery, 0, !caseSensitive)
            while (i >= 0 && out.size < 5000) {
                out.add(i)
                i = src.indexOf(findQuery, i + findQuery.length, !caseSensitive)
            }
        }
        out as List<Int>
    }

    fun goTo(idx: Int) {
        if (matches.isEmpty()) return
        val i = ((idx % matches.size) + matches.size) % matches.size
        matchIdx = i
        val s = matches[i]
        text = text.copy(selection = TextRange(s, s + findQuery.length))
        val line = text.text.take(s).count { it == '\n' }
        val px = with(density) { codeStyle.lineHeight.toPx() }
        scope.launch { vScroll.animateScrollTo(((line - 4).coerceAtLeast(0) * px).toInt()) }
    }

    LaunchedEffect(findQuery, caseSensitive, findOpen) {
        matchIdx = 0
        if (findOpen && matches.isNotEmpty()) goTo(0)
    }
    LaunchedEffect(findOpen) {
        if (findOpen) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
            }
        }
    }

    val curMatch = if (matches.isEmpty()) 0 else matchIdx.coerceIn(0, matches.size - 1)
    val qlen = findQuery.length
    val vt = remember(text.text, matches, curMatch, qlen) {
        object : VisualTransformation {
            override fun filter(text: AnnotatedString): TransformedText =
                TransformedText(buildCode(text.text, matches, qlen, curMatch), OffsetMapping.Identity)
        }
    }

    val status = msg ?: if (dirty) "Belum disimpan" else (subtitle ?: "Tidak ada perubahan")
    val statusColor = when {
        msg != null -> JarColors.Accent
        dirty -> EOrange
        else -> JarColors.TextMuted
    }

    Column(Modifier.fillMaxSize()) {
        EditorHeader(title, status, statusColor, onBack) {
            ActionIcon(Icons.Outlined.Undo, undo.isNotEmpty()) { doUndo() }
            ActionIcon(Icons.Outlined.Redo, redo.isNotEmpty()) { doRedo() }
            ActionIcon(Icons.Outlined.Search, true, tint = if (findOpen) JarColors.Accent else JarColors.TextPrimary) {
                findOpen = !findOpen
            }
            if (onRestore != null) {
                ActionIcon(Icons.Outlined.Restore, true) { confirmRestore = true }
            }
            Spacer(Modifier.weight(1f))
            ActionIcon(Icons.Outlined.Save, dirty, tint = JarColors.Accent) { save() }
        }

        if (findOpen) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF2A2927)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF2A2927))
                        .border(1.dp, EStroke, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    if (findQuery.isEmpty()) Text("Cari di berkas...", color = JarColors.TextMuted, fontSize = 14.sp)
                    BasicTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = JarColors.TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(JarColors.Accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(onSearch = { goTo(curMatch + 1) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    if (matches.isEmpty()) "0/0" else "${curMatch + 1}/${matches.size}",
                    color = JarColors.TextMuted, fontSize = 12.sp,
                )
                IconButton(onClick = { goTo(curMatch - 1) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, null, tint = JarColors.TextPrimary)
                }
                IconButton(onClick = { goTo(curMatch + 1) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, null, tint = JarColors.TextPrimary)
                }
                Text(
                    "Aa",
                    color = if (caseSensitive) JarColors.Accent else JarColors.TextMuted,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { caseSensitive = !caseSensitive }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        val lineCount = remember(text.text) { text.text.count { it == '\n' } + 1 }
        val gutter = remember(lineCount, firstLine) { (firstLine until firstLine + lineCount).joinToString("\n") }
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(vScroll)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                gutter,
                style = codeStyle.copy(color = JarColors.TextMuted, textAlign = TextAlign.End),
                modifier = Modifier.padding(horizontal = 8.dp).widthIn(min = 28.dp),
            )
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                BasicTextField(
                    value = text,
                    onValueChange = { onEdit(it) },
                    textStyle = codeStyle.copy(color = JarColors.TextPrimary),
                    cursorBrush = SolidColor(JarColors.Accent),
                    visualTransformation = vt,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.widthIn(min = 240.dp).padding(end = 24.dp),
                )
            }
        }

        // Bar simbol di atas keyboard
        Row(
            Modifier
                .fillMaxWidth()
                .background(EBar)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((label, ins) in SYMBOLS) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF353432))
                        .clickable { insert(ins) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = JarColors.TextPrimary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    if (confirmRestore && onRestore != null) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Kembalikan ke versi asli?") },
            text = { Text("Semua perubahan pada berkas ini akan hilang.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    scope.launch {
                        val s = onRestore()
                        if (s != null) {
                            original = s
                            text = TextFieldValue(s)
                            undo.clear()
                            redo.clear()
                            msg = "Dikembalikan ke versi asli"
                        } else {
                            msg = "Gagal mengembalikan"
                        }
                    }
                }) { Text("Kembalikan") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Batal") } },
        )
    }
}

// ---------- Pewarnaan sintaks Smali ----------
private val spDirective = SpanStyle(color = Color(0xFF6CA8FF))
private val spModifier = SpanStyle(color = Color(0xFFC792EA))
private val spOpcode = SpanStyle(color = Color(0xFF5FD6C4))
private val spRegister = SpanStyle(color = Color(0xFFE5C07B))
private val spLabel = SpanStyle(color = Color(0xFFF78C6C))
private val spString = SpanStyle(color = Color(0xFFA5D6A7))
private val spNumber = SpanStyle(color = Color(0xFFFFB067))
private val spType = SpanStyle(color = Color(0xFF89DDFF))
private val spComment = SpanStyle(color = Color(0xFF8A8880), fontStyle = FontStyle.Italic)
private val spMatchAll = SpanStyle(background = Color(0x55FFCB6B))
private val spMatchCur = SpanStyle(background = Color(0xAAFFB067))

private val MODIFIERS = setOf(
    "public", "private", "protected", "static", "final", "abstract", "synthetic", "constructor",
    "interface", "enum", "annotation", "bridge", "varargs", "native", "synchronized", "transient",
    "volatile", "strictfp", "declared-synchronized",
)

private fun buildCode(src: String, matches: List<Int>, qlen: Int, cur: Int): AnnotatedString =
    buildAnnotatedString {
        append(src)
        if (src.length <= HIGHLIGHT_MAX) styleSmali(src, this)
        if (qlen > 0) {
            for (k in matches.indices) {
                val s = matches[k]
                val e = minOf(s + qlen, src.length)
                if (s < e) addStyle(if (k == cur) spMatchCur else spMatchAll, s, e)
            }
        }
    }

private fun styleSmali(src: String, b: AnnotatedString.Builder) {
    val n = src.length
    var i = 0
    while (i < n) {
        var eol = src.indexOf('\n', i)
        if (eol < 0) eol = n
        styleLine(src, i, eol, b)
        i = eol + 1
    }
}

private fun isTokStart(p: Char) = p == ' ' || p == '\t' || p == '{' || p == ','

private fun isTypeStart(p: Char) =
    p == ' ' || p == '\t' || p == '{' || p == ',' || p == '(' || p == '[' || p == ')' || p == '>' ||
        p == ':' || p == '=' || p == ';'

private fun isRegister(w: String): Boolean =
    w.length >= 2 && (w[0] == 'v' || w[0] == 'p') && w.substring(1).all { it.isDigit() }

private fun styleLine(src: String, ls: Int, le: Int, b: AnnotatedString.Builder) {
    var s = ls
    while (s < le && (src[s] == ' ' || src[s] == '\t')) s++
    if (s >= le) return
    val c = src[s]
    if (c == '#') {
        b.addStyle(spComment, s, le)
        return
    }
    var te = s
    while (te < le && src[te] != ' ' && src[te] != '\t') te++
    val directive = c == '.'
    when {
        directive -> b.addStyle(spDirective, s, te)
        c == ':' -> b.addStyle(spLabel, s, te)
        c.isDigit() || c == '-' -> b.addStyle(spNumber, s, te)
        else -> b.addStyle(spOpcode, s, te)
    }
    val isEnd = directive && te - s == 4 && src.startsWith(".end", s)
    scanRest(src, te, le, directive, isEnd, b)
}

private fun scanRest(
    src: String,
    from: Int,
    le: Int,
    directive: Boolean,
    endDir: Boolean,
    b: AnnotatedString.Builder,
) {
    var i = from
    var firstWord = true
    while (i < le) {
        val ch = src[i]
        val prev = if (i > 0) src[i - 1] else ' '

        if (ch == '"') {
            var j = i + 1
            while (j < le && src[j] != '"') {
                if (src[j] == '\\') j++
                j++
            }
            val e = minOf(j + 1, le)
            b.addStyle(spString, i, e)
            i = e
            continue
        }
        if (ch == '#' && (prev == ' ' || prev == '\t')) {
            b.addStyle(spComment, i, le)
            return
        }
        if (ch == 'L' && isTypeStart(prev)) {
            var j = i + 1
            var ok = false
            while (j < le) {
                val d = src[j]
                if (d == ';') {
                    ok = true
                    break
                }
                if (d == ' ' || d == ':' || d == ',' || d == '{' || d == '}' || d == '(' || d == ')' || d == '=' || d == '\t') break
                j++
            }
            if (ok) {
                b.addStyle(spType, i, j + 1)
                i = j + 1
                continue
            }
        }
        if (ch == ':' && isTokStart(prev)) {
            var j = i + 1
            while (j < le && src[j] != ' ' && src[j] != ',' && src[j] != '}' && src[j] != '\t') j++
            b.addStyle(spLabel, i, j)
            i = j
            continue
        }
        if ((ch.isDigit() || (ch == '-' && i + 1 < le && src[i + 1].isDigit())) && isTokStart(prev)) {
            var j = i + 1
            while (j < le && (src[j].isLetterOrDigit() || src[j] == '.' || src[j] == '-')) j++
            b.addStyle(spNumber, i, j)
            i = j
            continue
        }
        if (ch.isLetter() && isTokStart(prev)) {
            var j = i
            while (j < le && (src[j].isLetterOrDigit() || src[j] == '-' || src[j] == '_')) j++
            val w = src.substring(i, j)
            when {
                isRegister(w) -> b.addStyle(spRegister, i, j)
                endDir && firstWord -> b.addStyle(spDirective, i, j)
                directive && w in MODIFIERS -> b.addStyle(spModifier, i, j)
            }
            firstWord = false
            i = j
            continue
        }
        i++
    }
}
