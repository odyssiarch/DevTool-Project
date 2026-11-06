package com.devtool.app.ui.screens

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
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devtool.app.core.ClassChange
import com.devtool.app.core.CompareResult
import com.devtool.app.core.DeepResult
import com.devtool.app.core.DiffLine
import com.devtool.app.core.JarNames
import com.devtool.app.core.MethodChange
import com.devtool.app.core.SmaliDiff
import com.devtool.app.core.VerifyCheck
import com.devtool.app.core.VerifyLevel
import com.devtool.app.core.formatSize
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors
import com.devtool.app.ui.viewmodel.CompareViewModel
import com.devtool.app.ui.viewmodel.vmFactory

private val Hijau = Color(0xFF5BD98C)
private val Kuning = Color(0xFFFFC857)
private val Merah = Color(0xFFFF6B6B)

private val Garis = Color(0x33FFFFFF)

@Composable
fun CompareScreen(onBack: () -> Unit) {
    val appCtx = LocalContext.current.applicationContext
    // Hasil dan proses perbandingan hidup di ViewModel, jadi selamat dari layar diputar.
    val vm: CompareViewModel = viewModel(factory = vmFactory { CompareViewModel(appCtx) })

    // Pilihan input tetap di layar (rememberSaveable).
    var oldP by rememberSaveable { mutableStateOf<String?>(null) }
    var newP by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf(false) }
    var res by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hideOther by rememberSaveable { mutableStateOf(true) }

    val pickOld = rememberSourcePicker { oldP = it; vm.reset() }
    val pickNew = rememberSourcePicker { newP = it; vm.reset() }

    val r = vm.result
    val err = vm.error
    val d = vm.deep
    val k = vm.selClass
    val m = vm.selMethod

    BackHandler(enabled = k != null) { vm.backFromDetail() }

    if (d != null && k != null && m != null) {
        MethodDiffScreen(k, m, d.oldName, d.newName) { vm.selectMethod(null) }
    } else if (d != null && k != null) {
        ClassDetailScreen(d, k, onBack = { vm.selectClass(null) }, onOpen = { vm.selectMethod(it) })
    } else {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Bandingkan JAR", "Dua versi, sampai level class dan method", onBack)
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
                                "Sampai daftar class dan method yang berbeda, lengkap dengan isi Smali-nya (butuh beberapa menit).",
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
                        onClick = {
                            val a = oldP
                            val b = newP
                            if (a != null && b != null) vm.start(a, b, code, res)
                        },
                        enabled = oldP != null && newP != null && !vm.running,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarColors.Accent,
                            contentColor = JarColors.Black,
                            disabledContainerColor = JarColors.SurfaceHigh,
                            disabledContentColor = JarColors.TextMuted,
                        ),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(if (vm.running) "Membandingkan..." else "Mulai perbandingan")
                    }
                }
                if (vm.running) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                    if (vm.status.isNotEmpty()) item { Text(vm.status, color = JarColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
                }
                if (err != null) item { Text(err, color = MaterialTheme.colorScheme.error) }
                if (vm.checks.isNotEmpty()) checksItems(vm.checks, vm.nameOld, vm.nameNew)
                if (r != null) resultItems(r, vm.nameOld, vm.nameNew)
                if (d != null) {
                    deepItems(d, query, { query = it }, hideOther, { hideOther = it }, { vm.selectClass(it) })
                }
            }
        }
    }
    SourcePickerDialogs(pickOld)
    SourcePickerDialogs(pickNew)
}

@Composable
private fun PackageCard(title: String, project: String?, picker: SourcePicker) {
    PillCard(title = title.uppercase()) {
        Text(
            if (project != null) JarNames.display(project) else "Belum dipilih",
            color = if (project != null) JarColors.TextPrimary else Kuning,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            TonalAction(Icons.Outlined.Folder, "File JAR", Modifier.weight(1f), picker::openFile)
            Spacer(Modifier.width(10.dp))
            TonalAction(Icons.Outlined.History, "Proyek", Modifier.weight(1f), picker::openProjects)
        }
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
                checkedColor = JarColors.Accent,
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

private fun LazyListScope.checksItems(list: List<VerifyCheck>, nameOld: String, nameNew: String) {
    item {
        JarCard {
            Column(Modifier.padding(16.dp)) {
                Text("Pemeriksaan struktur JAR", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cara $nameNew disimpan, dibanding $nameOld.",
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

@Composable
private fun JarSummary(name: String, warna: Color, size: String, entries: String, dex: String, versi: String, classes: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall, color = warna)
        Text("Ukuran $size, $entries berkas", style = MaterialTheme.typography.bodyMedium, color = JarColors.TextPrimary)
        Text("$dex berkas dex (versi $versi), $classes class", style = MaterialTheme.typography.bodyMedium, color = JarColors.TextPrimary)
    }
}

private fun LazyListScope.resultItems(r: CompareResult, nameOld: String, nameNew: String) {
    item {
        JarCard {
            Column(Modifier.padding(16.dp)) {
                Text("Ringkasan", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
                JarSummary(
                    nameOld, Merah,
                    formatSize(r.oldInfo.size), "${r.oldInfo.entries}",
                    "${r.oldInfo.dex.size}", r.oldInfo.dex.map { it.version }.distinct().joinToString(),
                    "${r.oldInfo.totalClasses}",
                )
                JarSummary(
                    nameNew, Hijau,
                    formatSize(r.newInfo.size), "${r.newInfo.entries}",
                    "${r.newInfo.dex.size}", r.newInfo.dex.map { it.version }.distinct().joinToString(),
                    "${r.newInfo.totalClasses}",
                )
            }
        }
    }
    section("Class hanya ada di $nameNew", r.addedClasses, true)
    section("Class hanya ada di $nameOld", r.removedClasses, true)
    section("Berkas hanya ada di $nameNew", r.addedFiles)
    section("Berkas hanya ada di $nameOld", r.removedFiles)
    section("Berkas yang isinya berbeda", r.changedFiles)
}

@Composable
private fun NamaRow(nama: String, isClass: Boolean) {
    val pisah = if (isClass) maxOf(nama.lastIndexOf('.'), nama.lastIndexOf('/')) else nama.lastIndexOf('/')
    val pendek = if (pisah >= 0) nama.substring(pisah + 1) else nama
    val induk = if (pisah >= 0) nama.substring(0, pisah) else ""
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(pendek, style = MaterialTheme.typography.bodyLarge, color = JarColors.TextPrimary)
        if (induk.isNotEmpty()) {
            Text(induk, style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
        }
    }
}

private fun LazyListScope.section(title: String, list: List<String>?, isClass: Boolean = false) {
    if (list == null) return
    item {
        Text("$title (${list.size})", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
    }
    item {
        JarCard {
            Column(Modifier.fillMaxWidth()) {
                if (list.isEmpty()) {
                    Text("Tidak ada.", color = JarColors.TextMuted, modifier = Modifier.padding(16.dp))
                } else {
                    val tampil = list.take(300)
                    tampil.forEachIndexed { i, nama ->
                        if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = Garis)
                        NamaRow(nama, isClass)
                    }
                    if (list.size > 300) {
                        HorizontalDivider(thickness = 0.5.dp, color = Garis)
                        Text("dan ${list.size - 300} lainnya", color = JarColors.TextMuted, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

// ---------- daftar class yang berbeda ----------

private fun LazyListScope.deepItems(
    d: DeepResult,
    query: String,
    onQuery: (String) -> Unit,
    hideOther: Boolean,
    onHideOther: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    val dasar = d.changes.filter { !hideOther || it.added + it.removed + it.changed > 0 }
    val shown = dasar.filter { query.isBlank() || it.key.contains(query.trim(), ignoreCase = true) }
    item {
        Column {
            Text(
                "Class dan method yang berbeda (${dasar.size})",
                style = MaterialTheme.typography.titleMedium,
                color = JarColors.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Diperiksa ${d.total} class dari ${d.oldName}.",
                style = MaterialTheme.typography.bodySmall,
                color = JarColors.TextMuted,
            )
        }
    }
    item {
        Row(
            Modifier.fillMaxWidth().clickable { onHideOther(!hideOther) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = hideOther,
                onCheckedChange = onHideOther,
                colors = CheckboxDefaults.colors(
                    checkedColor = JarColors.Accent,
                    checkmarkColor = JarColors.Black,
                    uncheckedColor = JarColors.TextMuted,
                ),
            )
            Text(
                "Sembunyikan class yang hanya beda field atau header",
                color = JarColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
    if (shown.isEmpty()) item { Text("Tidak ada class yang cocok.", color = JarColors.TextMuted) }
    items(shown.take(200)) { c -> ClassRow(c, d.oldName, d.newName) { onOpen(c.key) } }
    if (shown.size > 200) {
        item { Text("dan ${shown.size - 200} lainnya. Ketik nama class untuk mempersempit.", color = JarColors.TextMuted) }
    }
}

@Composable
private fun ClassRow(c: ClassChange, nameOld: String, nameNew: String, onClick: () -> Unit) {
    // di JAR lama: method yang berubah dan yang hanya ada di lama (dihapus)
    val lama = buildString {
        if (c.changed > 0) append("${c.changed} method berubah")
        if (c.removed > 0) append(if (isEmpty()) "" else ", ").append("${c.removed} method hanya di sini")
        if (isEmpty()) append("tidak ada method yang berbeda")
    }
    // di JAR baru: method yang berubah dan yang hanya ada di baru
    val baru = buildString {
        if (c.changed > 0) append("${c.changed} method berubah")
        if (c.added > 0) append(if (isEmpty()) "" else ", ").append("${c.added} method hanya di sini")
        if (isEmpty()) append("tidak ada method yang berbeda")
    }
    JarCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            Text(c.simpleName, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
            Text(c.dotted, style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
            Spacer(Modifier.height(6.dp))
            Text(nameOld, style = MaterialTheme.typography.labelLarge, color = Merah)
            Text(lama, style = MaterialTheme.typography.bodyMedium, color = JarColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(nameNew, style = MaterialTheme.typography.labelLarge, color = Hijau)
            Text(baru, style = MaterialTheme.typography.bodyMedium, color = JarColors.TextPrimary)
            if (c.other) {
                Spacer(Modifier.height(4.dp))
                Text("Field atau header class juga berbeda", style = MaterialTheme.typography.bodySmall, color = Kuning)
            }
        }
    }
}

// ---------- daftar method di satu class, dipisah per JAR ----------

@Composable
internal fun ClassDetailScreen(d: DeepResult, key: String, onBack: () -> Unit, onOpen: (MethodChange) -> Unit) {
    val methods = remember(key) { SmaliDiff.methodsOf(d, key) }
    val title = key.removeSuffix(".smali").substringAfterLast('/')
    val dilama = methods.filter { it.kind != "BARU" }
    val dibaru = methods.filter { it.kind != "DIHAPUS" }
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
                Text(d.oldName, style = MaterialTheme.typography.titleMedium, color = Merah)
                Text(
                    "Class ini, method yang berbeda (${dilama.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarColors.TextMuted,
                )
            }
            if (dilama.isEmpty()) item { Text("Tidak ada.", color = JarColors.TextMuted) }
            items(dilama) { m -> MethodRow(m, lamaSisi = true) { onOpen(m) } }
            item {
                Spacer(Modifier.height(8.dp))
                Text(d.newName, style = MaterialTheme.typography.titleMedium, color = Hijau)
                Text(
                    "Class ini, method yang berbeda (${dibaru.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarColors.TextMuted,
                )
            }
            if (dibaru.isEmpty()) item { Text("Tidak ada.", color = JarColors.TextMuted) }
            items(dibaru) { m -> MethodRow(m, lamaSisi = false) { onOpen(m) } }
        }
    }
}

private fun kindColor(kind: String): Color = when (kind) {
    "BARU" -> Hijau
    "DIHAPUS" -> Merah
    else -> Kuning
}

private fun kindText(kind: String, lamaSisi: Boolean): String = when (kind) {
    "BARU" -> "Hanya ada di sini"
    "DIHAPUS" -> if (lamaSisi) "Hanya ada di sini" else "Dihapus"
    else -> "Isinya berbeda"
}

@Composable
private fun MethodRow(m: MethodChange, lamaSisi: Boolean, onClick: () -> Unit) {
    JarCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
            Text(kindText(m.kind, lamaSisi), style = MaterialTheme.typography.labelMedium, color = kindColor(m.kind))
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

// ---------- isi Smali: dua JAR dipisah ----------

@Composable
private fun CodeBlock(lines: List<DiffLine>) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        lines.forEach { l ->
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

@Composable
internal fun MethodDiffScreen(classKey: String, m: MethodChange, nameOld: String, nameNew: String, onBack: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    val lines = remember(m) { SmaliDiff.lineDiff(m.oldText, m.newText) }
    val sisiLama = remember(m, full) {
        val s = SmaliDiff.sisiLama(lines)
        if (full) s else SmaliDiff.compact(s, 2)
    }
    val sisiBaru = remember(m, full) {
        val s = SmaliDiff.sisiBaru(lines)
        if (full) s else SmaliDiff.compact(s, 2)
    }
    val clipboard = LocalClipboardManager.current
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
                TextButton(onClick = { full = !full }) {
                    Text(if (full) "Hanya baris yang berbeda" else "Tampilkan semua baris")
                }
            }
            // ----- JAR lama -----
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(nameOld, style = MaterialTheme.typography.titleMedium, color = Merah, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(m.oldText)) },
                        enabled = m.oldText.isNotEmpty(),
                    ) { Text("Salin") }
                }
            }
            item {
                if (m.oldText.isEmpty()) {
                    Text("Method ini tidak ada di $nameOld.", color = JarColors.TextMuted)
                } else {
                    CodeBlock(sisiLama)
                }
            }
            // ----- JAR baru -----
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(nameNew, style = MaterialTheme.typography.titleMedium, color = Hijau, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(m.newText)) },
                        enabled = m.newText.isNotEmpty(),
                    ) { Text("Salin") }
                }
            }
            item {
                if (m.newText.isEmpty()) {
                    Text("Method ini tidak ada di $nameNew.", color = JarColors.TextMuted)
                } else {
                    CodeBlock(sisiBaru)
                }
            }
        }
    }
}
