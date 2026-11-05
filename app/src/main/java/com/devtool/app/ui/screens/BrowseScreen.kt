package com.devtool.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.devtool.app.core.ClassEntry
import com.devtool.app.core.JarAnalyzer
import com.devtool.app.core.JarNames
import com.devtool.app.ui.components.JarTopBar
import com.devtool.app.ui.components.PackageTree
import com.devtool.app.ui.components.TreeNode
import com.devtool.app.ui.components.buildTree
import com.devtool.app.ui.components.filterTree
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Listing(val folders: List<Pair<String, Int>>, val leaves: List<ClassEntry>)

private fun listing(all: List<ClassEntry>, prefix: String): Listing {
    val folders = sortedMapOf<String, Int>()
    val leaves = ArrayList<ClassEntry>()
    val p = if (prefix.isEmpty()) "" else "$prefix."
    for (c in all) {
        if (!c.name.startsWith(p)) continue
        val rest = c.name.substring(p.length)
        val i = rest.indexOf('.')
        if (i < 0) leaves.add(c) else {
            val key = rest.substring(0, i)
            folders[key] = (folders[key] ?: 0) + 1
        }
    }
    return Listing(folders.toList(), leaves)
}

/** Penjelajah paket dan class dari semua dex di dalam JAR (termasuk kontainer dex 041). */
@Composable
fun BrowseScreen(name: String, onOpenCode: (String) -> Unit, onBack: () -> Unit, onOpenSmali: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var classes by remember { mutableStateOf<List<ClassEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var path by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<ClassEntry?>(null) }
    var roots by remember { mutableStateOf<List<TreeNode>?>(null) }

    LaunchedEffect(name) {
        try {
            val list = withContext(Dispatchers.IO) { JarAnalyzer.classEntries(ctx, name) }
            roots = withContext(Dispatchers.Default) { buildTree(list.map { it.name }) }
            classes = list
        } catch (e: Exception) {
            error = e.message ?: "Gagal membaca class"
        }
    }

    fun goBack() {
        if (query.isEmpty() && path.isNotEmpty()) path = path.substringBeforeLast('.', "") else onBack()
    }
    BackHandler(enabled = query.isEmpty() && path.isNotEmpty()) { goBack() }

    Column(Modifier.fillMaxSize()) {
        JarTopBar(JarNames.display(name)) { goBack() }
        val all = classes
        val err = error
        when {
            err != null -> Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val q = query.trim()
                val flat = remember(all, q) { if (q.isEmpty()) emptyList() else all.filter { it.name.contains(q, ignoreCase = true) } }
                val byName = remember(all) { all.associateBy { it.name } }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Cari class") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarColors.Blue,
                        unfocusedBorderColor = JarColors.Outline,
                        cursorColor = JarColors.Blue,
                        focusedTextColor = JarColors.TextPrimary,
                        unfocusedTextColor = JarColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    if (q.isNotEmpty()) "${flat.size} hasil"
                    else (if (path.isEmpty()) "Semua paket" else path) + " (${all.size} class di JAR)",
                    color = JarColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (all.isEmpty()) {
                    Text(
                        "Tidak ada class dex di JAR ini.",
                        color = JarColors.TextMuted,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                val tree = roots
                if (tree == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val searching = q.length >= 2
                    val shown = remember(tree, q) { if (searching) filterTree(tree, q) else tree }
                    PackageTree(shown, expandAll = searching, modifier = Modifier.fillMaxSize()) { cls ->
                        selected = byName[cls]
                    }
                }
            }
        }
    }

    selected?.let { c ->
        val clip = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = JarColors.SurfaceHigh,
            title = { Text(c.name.substringAfterLast('.')) },
            text = {
                Column {
                    Text("Nama lengkap", color = JarColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Text(c.name, color = JarColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text("Paket", color = JarColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Text(c.name.substringBeforeLast('.', "(tanpa paket)"), color = JarColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text("Berkas dex", color = JarColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Text(c.dex, color = JarColors.TextPrimary)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { selected = null; onOpenSmali(c.name) }) { Text("Lihat Smali") }
                    TextButton(onClick = { selected = null; onOpenCode(c.name) }) { Text("Lihat Java") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { clip.setText(AnnotatedString(c.name)); selected = null }) { Text("Salin nama") }
                    TextButton(onClick = { selected = null }) { Text("Tutup") }
                }
            },
        )
    }
}
