package com.jartool.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jartool.app.core.ClassDiffEngine
import com.jartool.app.core.CompareResult
import com.jartool.app.core.DiffSession
import com.jartool.app.core.JarComparer
import com.jartool.app.core.formatSize
import com.jartool.app.ui.components.*
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CompareScreen(onBack: () -> Unit, onOpenDiff: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var oldP by rememberSaveable { mutableStateOf<String?>(null) }
    var newP by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf(false) }
    var res by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CompareResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickOld = rememberSourcePicker { oldP = it; result = null }
    val pickNew = rememberSourcePicker { newP = it; result = null }

    fun start() {
        val a = oldP ?: return
        val b = newP ?: return
        running = true
        error = null
        result = null
        scope.launch {
            try {
                result = withContext(Dispatchers.IO) { JarComparer.compare(ctx, a, b, code, res) }
            } catch (e: Exception) {
                error = e.message ?: "Perbandingan gagal"
            } finally {
                running = false
            }
        }
    }

    var diffBusy by remember { mutableStateOf(false) }
    var diffMsg by remember { mutableStateOf<String?>(null) }
    var diffErr by remember { mutableStateOf<String?>(null) }

    fun startDiff() {
        val a = oldP ?: return
        val b = newP ?: return
        diffBusy = true
        diffErr = null
        diffMsg = "Membaca JAR lama..."
        scope.launch {
            try {
                val oi = ClassDiffEngine.loadIndex(ctx, a)
                diffMsg = "Membaca JAR baru..."
                val ni = ClassDiffEngine.loadIndex(ctx, b)
                val d = ClassDiffEngine.diff(oi, ni) { n, t -> diffMsg = "Membandingkan class $n / $t" }
                DiffSession.set(a, b, oi, ni, d)
                diffMsg = null
                onOpenDiff()
            } catch (e: OutOfMemoryError) {
                diffErr = "Memori tidak cukup untuk membandingkan JAR sebesar ini"
                diffMsg = null
            } catch (e: Exception) {
                diffErr = e.message ?: "Perbandingan class gagal"
                diffMsg = null
            } finally {
                diffBusy = false
            }
        }
    }

    val r = result
    val err = error
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
            item {
                OutlinedButton(
                    onClick = { startDiff() },
                    enabled = oldP != null && newP != null && !running && !diffBusy,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarColors.Blue),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(diffMsg ?: "Lihat class dan method yang berbeda")
                }
            }
            diffErr?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
            if (err != null) item { Text(err, color = MaterialTheme.colorScheme.error) }
            if (r != null) resultItems(r)
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
