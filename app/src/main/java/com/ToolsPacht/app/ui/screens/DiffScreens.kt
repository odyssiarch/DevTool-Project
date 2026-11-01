package com.jartool.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jartool.app.core.ClassDiffEngine
import com.jartool.app.core.DiffLine
import com.jartool.app.core.DiffSession
import com.jartool.app.core.DiffStatus
import com.jartool.app.core.LineTag
import com.jartool.app.core.MethodDiff
import com.jartool.app.core.MethodRef
import com.jartool.app.ui.components.*
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Green = Color(0xFF3FB950)
private val Red = Color(0xFFF85149)

@Composable
private fun Tabs(selected: Int, titles: List<String>, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected, containerColor = JarColors.Black, contentColor = JarColors.Blue) {
        titles.forEachIndexed { i, t ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                selectedContentColor = JarColors.Blue,
                unselectedContentColor = JarColors.TextMuted,
                text = { Text(t, maxLines = 1) },
            )
        }
    }
}

/** Layar 1: daftar class yang berbeda (berubah / baru / hilang). */
@Composable
fun ClassDiffScreen(onBack: () -> Unit, onOpenClass: (String) -> Unit, onOpenMethod: (String, Int) -> Unit) {
    val r = DiffSession.result
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        JarTopBar("Perbedaan JAR", onBack)
        if (r == null) {
            Text(
                "Data perbandingan sudah tidak ada. Jalankan perbandingan lagi.",
                color = JarColors.TextMuted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Text(
                "${DiffSession.oldName} → ${DiffSession.newName}\nDibaca ${r.oldCount} class (lama), ${r.newCount} class (baru)",
                color = JarColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Tabs(
                tab,
                listOf("Method (${r.methods.size})", "Class (${r.changed.size})", "Baru (${r.added.size})", "Hilang (${r.removed.size})"),
            ) { tab = it }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Cari path class atau method") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val q = query.trim()
            if (tab == 0) {
                val shown = remember(query, r) {
                    if (q.isEmpty()) r.methods
                    else r.methods.filter { it.cls.contains(q, true) || it.label.contains(q, true) }
                }
                if (shown.isEmpty()) {
                    Text("Tidak ada.", color = JarColors.TextMuted, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(shown) { _, m -> MethodRefRow(m) { onOpenMethod(m.cls, m.index) } }
                }
            } else {
                val source = when (tab) {
                    1 -> r.changed
                    2 -> r.added
                    else -> r.removed
                }
                val shown = remember(tab, query, r) {
                    if (q.isEmpty()) source else source.filter { it.contains(q, ignoreCase = true) }
                }
                if (shown.isEmpty()) {
                    Text("Tidak ada.", color = JarColors.TextMuted, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it }) { n -> ClassRow(n) { onOpenClass(n) } }
                }
            }
        }
    }
}

@Composable
private fun MethodRefRow(m: MethodRef, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarColors.Surface),
        border = BorderStroke(1.dp, JarColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(statusLabel(m.status), style = MaterialTheme.typography.labelMedium, color = statusColor(m.status))
            Spacer(Modifier.height(2.dp))
            Text(m.cls, style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(m.label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = JarColors.TextPrimary)
        }
    }
}

@Composable
private fun ClassRow(name: String, onClick: () -> Unit) {
    val simple = name.substringAfterLast('.')
    val pkg = name.substringBeforeLast('.', "")
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarColors.Surface),
        border = BorderStroke(1.dp, JarColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(simple, style = MaterialTheme.typography.titleSmall, color = JarColors.TextPrimary)
            if (pkg.isNotEmpty()) Text(pkg, style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
        }
    }
}

private fun statusLabel(s: DiffStatus) = when (s) {
    DiffStatus.CHANGED -> "Berubah"
    DiffStatus.ADDED -> "Baru"
    DiffStatus.REMOVED -> "Hilang"
}

private fun statusColor(s: DiffStatus) = when (s) {
    DiffStatus.CHANGED -> JarColors.Blue
    DiffStatus.ADDED -> Green
    DiffStatus.REMOVED -> Red
}

/** Layar 2: daftar method yang berbeda dalam satu class. */
@Composable
fun MethodListScreen(cls: String, onBack: () -> Unit, onOpenMethod: (Int) -> Unit) {
    var list by remember(cls) { mutableStateOf<List<MethodDiff>?>(null) }
    LaunchedEffect(cls) { list = withContext(Dispatchers.Default) { DiffSession.methods(cls) } }
    Column(Modifier.fillMaxSize()) {
        JarTopBar(cls.substringAfterLast('.'), onBack)
        Text(
            cls,
            color = JarColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val l = list
        when {
            l == null -> Text("Membongkar class...", color = JarColors.TextMuted, modifier = Modifier.padding(16.dp))
            l.isEmpty() -> Text("Tidak ada perbedaan method.", color = JarColors.TextMuted, modifier = Modifier.padding(16.dp))
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(l) { i, m -> MethodRow(m) { onOpenMethod(i) } }
            }
        }
    }
}

@Composable
private fun MethodRow(m: MethodDiff, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = JarColors.Surface),
        border = BorderStroke(1.dp, JarColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(statusLabel(m.status), style = MaterialTheme.typography.labelMedium, color = statusColor(m.status))
            Spacer(Modifier.height(4.dp))
            Text(
                m.label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = JarColors.TextPrimary,
            )
        }
    }
}

/** Layar 3: isi smali method, tab Perbedaan / Lama / Baru. */
@Composable
fun MethodDetailScreen(cls: String, index: Int, onBack: () -> Unit) {
    val m = remember(cls, index) { DiffSession.methods(cls).getOrNull(index) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        JarTopBar("Method", onBack)
        if (m == null) {
            Text(
                "Data perbandingan sudah tidak ada. Jalankan perbandingan lagi.",
                color = JarColors.TextMuted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Text(
                m.label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = JarColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Tabs(tab, listOf("Perbedaan", "Lama", "Baru")) { tab = it }
            val lines = remember(m, tab) {
                val a = if (m.oldText.isEmpty()) emptyList() else m.oldText.lines()
                val b = if (m.newText.isEmpty()) emptyList() else m.newText.lines()
                when (tab) {
                    0 -> ClassDiffEngine.lineDiff(a, b)
                    1 -> a.map { DiffLine(LineTag.SAME, it) }
                    else -> b.map { DiffLine(LineTag.SAME, it) }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
            ) {
                itemsIndexed(lines) { _, l -> CodeLine(l) }
            }
        }
    }
}

@Composable
private fun CodeLine(l: DiffLine) {
    val (bg, prefix) = when (l.tag) {
        LineTag.ADD -> Green.copy(alpha = 0.18f) to "+ "
        LineTag.DEL -> Red.copy(alpha = 0.18f) to "- "
        LineTag.SAME -> Color.Transparent to "  "
    }
    Text(
        prefix + l.text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = JarColors.TextPrimary,
        modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 4.dp),
    )
}
