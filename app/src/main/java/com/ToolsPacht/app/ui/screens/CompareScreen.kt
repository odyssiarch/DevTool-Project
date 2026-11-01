package com.jartool.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jartool.app.core.ClassChange
import com.jartool.app.core.CompareResult
import com.jartool.app.core.DeepResult
import com.jartool.app.core.JarComparer
import com.jartool.app.core.JarVerifier
import com.jartool.app.core.MethodChange
import com.jartool.app.core.SmaliDiff
import com.jartool.app.core.VerifyCheck
import com.jartool.app.core.VerifyLevel
import com.jartool.app.core.Workspace
import com.jartool.app.core.formatSize
import com.jartool.app.ui.components.*
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Hijau = Color(0xFF5BD98C)
private val Kuning = Color(0xFFFFC857)
private val Merah = Color(0xFFFF6B6B)

@Composable
fun CompareScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var oldP by rememberSaveable { mutableStateOf<String?>(null) }
    var newP by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf(false) }
    var res by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CompareResult?>(null) }
    var checks by remember { mutableStateOf<List<VerifyCheck>>(emptyList()) }
    var deep by remember { mutableStateOf<DeepResult?>(null) }
    var selClass by remember { mutableStateOf<String?>(null) }
    var selMethod by remember { mutableStateOf<MethodChange?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetResult() {
        result = null
        checks = emptyList()
        deep = null
        selClass = null
        selMethod = null
    }

    val pickOld = rememberSourcePicker { oldP = it; resetResult() }
    val pickNew = rememberSourcePicker { newP = it; resetResult() }

    fun start() {
        val a = oldP ?: return
        val b = newP ?: return
        running = true
        error = null
        status = ""
        resetResult()
        scope.launch {
            try {
                checks = withContext(Dispatchers.IO) {
                    try {
                        JarVerifier.structure(Workspace.jarOf(ctx, a), Workspace.jarOf(ctx, b))
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                result = withContext(Dispatchers.IO) { JarComparer.compare(ctx, a, b, code, res) }
                if (code) deep = SmaliDiff.compare(ctx, a, b) { status = it }
            } catch (e: Exception) {
                error = e.message ?: "Perbandingan gagal"
            } finally {
                running = false
                status = ""
            }
        }
    }

    val r = result
    val err = error
    val d = deep
    val k = selClass
    val m = selMethod

    BackHandler(enabled = k != null) {
        if (selMethod != null) selMethod = null else selClass = null
    }

    if (d != null && k != null && m != null) {
        MethodDiffScreen(k, m) { selMethod = null }
    } else if (d != null && k != null) {
        ClassDetailScreen(d, k, onBack = { selClass = null }, onOpen = { selMethod = it })
    } else {
        Column(Modifier.fillMaxSize()) {
            JarTopBar("Bandingkan JAR", onBack)
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PackageCard("Versi lama", oldP, pickOld) }
                item { PackageCard("Versi baru", newP, pickNew) }
                item {
                    JarCard {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Opsional: perbandingan kode dan sumber daya dapat memakan waktu lebih lama.",
                                color = JarColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            OptionRow("Bandingkan kode", code) { code = it }
                            Text(
                                "Sampai daftar class dan method yang berubah, lengkap dengan isi Smali-nya (butuh beberapa menit).",
                                color = JarColors.TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                            )
                            OptionRow("Bandingkan sumber daya", res) { res = it }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { start() },
                        enabled = oldP != null && newP != null && !running,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarColors.Blue,
                            contentColor = JarColors.Black,
                            disabledContainerColor = JarColors.SurfaceHigh,
                            disabledContentColor = JarColors.TextMuted,
                        ),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(if (running) "Membandingkan..." else "Mulai perbandingan")
                    }
                }
                if (running) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    if (status.isNotEmpty()) item { Text(status, color = JarColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                }
                if (err != null) item { Text(err, color = MaterialTheme.colorScheme.error) }
                if (checks.isNotEmpty()) checksItems(checks)
                if (r != null) resultItems(r)
                if (d != null) deepItems(d, query, { query = it }, { selClass = it })
            }
        }
    }
    SourcePickerDialogs(pickOld)
    SourcePickerDialogs(pickNew)
}

@Composable
private fun PackageCard(title: String, project: String?, picker: SourcePicker) {
    JarCard {
        Row(Modifier.padding(16.dp)) {
            IconTile(Icons.Outlined.Folder)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    project ?: "Pilih JAR yang ingin dibandingkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarColors.TextMuted,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = picker::openFile) { Text("File JAR") }
            TextButton(onClick = picker::openProjects) { Text("Proyek yang ada") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = JarColors.Blue,
                checkmarkColor = JarColors.Black,
                uncheckedColor = JarColors.TextMuted,
            ),
        )
        Text(label, color = JarColors.TextPrimary)
    }
}

private fun levelColor(l: VerifyLevel): Color = when (l) {
    VerifyLevel.OK -> Hijau
    VerifyLevel.WARN -> Kuning
    VerifyLevel.FAIL -> Merah
}

private fun LazyListScope.checksItems(list: List<VerifyCheck>) {
    item {
        JarCard {
            Column(Modifier.padding(16.dp)) {
                Text("Pemeriksaan struktur JAR", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cara JAR baru disimpan, dibanding JAR lama.",
                    style = MaterialTheme.typography.bodySmall,
                    color = JarColors.TextMuted,
                )
                Spacer(Modifier.height(8.dp))
                list.forEach { c ->
                    Text(
                        c.render(),
                        color = levelColor(c.level),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.resultItems(r: CompareResult) {
    item {
        JarCard {
            Column(Modifier.padding(16.dp)) {
                Text("Ringkasan", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                SummaryRow("Ukuran", "${formatSize(r.oldInfo.size)} → ${formatSize(r.newInfo.size)}")
                SummaryRow("Jumlah berkas", "${r.oldInfo.entries} → ${r.newInfo.entries}")
                SummaryRow("Berkas dex", "${r.oldInfo.dex.size} → ${r.newInfo.dex.size}")
                SummaryRow(
                    "Versi dex",
                    "${r.oldInfo.dex.map { it.version }.distinct().joinToString()} → " +
                        r.newInfo.dex.map { it.version }.distinct().joinToString(),
                )
                SummaryRow("Jumlah class", "${r.oldInfo.totalClasses} → ${r.newInfo.totalClasses}")
            }
        }
    }
    section("Class baru", r.addedClasses)
    section("Class dihapus", r.removedClasses)
    section("Berkas baru", r.addedFiles)
    section("Berkas dihapus", r.removedFiles)
    section("Berkas berubah", r.changedFiles)
}

private fun LazyListScope.section(title: String, list: List<String>?) {
    if (list == null) return
    item {
        Text("$title (${list.size})", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
    }
    if (list.isEmpty()) item { Text("Tidak ada.", color = JarColors.TextMuted) }
    items(list.take(300)) {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = JarColors.TextPrimary)
    }
    if (list.size > 300) item { Text("dan ${list.size - 300} lainnya", color = JarColors.TextMuted) }
}

// ---------- daftar class yang berubah ----------

private fun LazyListScope.deepItems(
    d: DeepResult,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    item {
        Text(
            "Class berubah (${d.changes.size} dari ${d.total})",
            style = MaterialTheme.typography.titleMedium,
            color = JarColors.TextPrimary,
        )
    }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Cari nama class") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val shown = d.changes.filter { query.isBlank() || it.key.contains(query.trim(), ignoreCase = true) }
    if (shown.isEmpty()) item { Text("Tidak ada class yang cocok.", color = JarColors.TextMuted) }
    items(shown.take(200)) { c -> ClassRow(c) { onOpen(c.key) } }
    if (shown.size > 200) {
        item { Text("dan ${shown.size - 200} lainnya. Ketik nama class untuk mempersempit.", color = JarColors.TextMuted) }
    }
}

@Composable
private fun ClassRow(c: ClassChange, onClick: () -> Unit) {
    val ringkas = buildString {
        if (c.changed > 0) append("${c.changed} method berubah")
        if (c.added > 0) append(if (isEmpty()) "" else ", ").append("${c.added} baru")
        if (c.removed > 0) append(if (isEmpty()) "" else ", ").append("${c.removed} dihapus")
        if (c.other) append(if (isEmpty()) "" else ", ").append("bagian lain berubah")
        if (isEmpty()) append("berubah")
    }
    JarCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            Text(c.simpleName, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
            Text(c.dotted, style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(ringkas, style = MaterialTheme.typography.bodyMedium, color = Kuning)
        }
    }
}

// ---------- daftar method di satu class ----------

@Composable
private fun ClassDetailScreen(d: DeepResult, key: String, onBack: () -> Unit, onOpen: (MethodChange) -> Unit) {
    val methods = remember(key) { SmaliDiff.methodsOf(d, key) }
    val title = key.removeSuffix(".smali").substringAfterLast('/')
    Column(Modifier.fillMaxSize()) {
        JarTopBar(title, onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    key.removeSuffix(".smali").replace('/', '.'),
                    style = MaterialTheme.typography.bodySmall,
                    color = JarColors.TextMuted,
                )
            }
            item {
                Text(
                    "Method yang berbeda (${methods.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarColors.TextPrimary,
                )
            }
            if (methods.isEmpty()) item { Text("Tidak ada method yang berbeda.", color = JarColors.TextMuted) }
            items(methods) { m -> MethodRow(m) { onOpen(m) } }
        }
    }
}

private fun kindColor(kind: String): Color = when (kind) {
    "BARU" -> Hijau
    "DIHAPUS" -> Merah
    else -> Kuning
}

@Composable
private fun MethodRow(m: MethodChange, onClick: () -> Unit) {
    JarCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            Text(m.kind, style = MaterialTheme.typography.labelMedium, color = kindColor(m.kind))
            Spacer(Modifier.height(2.dp))
            Text(
                m.header,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = JarColors.TextPrimary,
            )
        }
    }
}

// ---------- isi Smali: selisih per baris ----------

@Composable
private fun MethodDiffScreen(classKey: String, m: MethodChange, onBack: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    val lines = remember(m) { SmaliDiff.lineDiff(m.oldText, m.newText) }
    val shown = remember(m, full) { if (full) lines else SmaliDiff.compact(lines, 2) }
    val clipboard = LocalClipboardManager.current
    val plus = lines.count { it.kind == '+' }
    val minus = lines.count { it.kind == '-' }
    val title = classKey.removeSuffix(".smali").substringAfterLast('/')
    Column(Modifier.fillMaxSize()) {
        JarTopBar(title, onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(m.header, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarColors.TextPrimary)
            }
            item {
                Text(
                    "${m.kind}: $plus baris ditambah (hijau, +), $minus baris dihapus (merah, -)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarColors.TextMuted,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { full = !full }) {
                        Text(if (full) "Hanya yang berubah" else "Tampilkan semua baris")
                    }
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(m.newText)) },
                        enabled = m.newText.isNotEmpty(),
                    ) { Text("Salin versi baru") }
                }
            }
            item {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    shown.forEach { l ->
                        val awal = when (l.kind) {
                            '+' -> "+ "
                            '-' -> "- "
                            else -> "  "
                        }
                        val warna = when (l.kind) {
                            '+' -> Hijau
                            '-' -> Merah
                            else -> JarColors.TextMuted
                        }
                        Text(
                            awal + l.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            softWrap = false,
                            color = warna,
                        )
                    }
                }
            }
        }
    }
}
