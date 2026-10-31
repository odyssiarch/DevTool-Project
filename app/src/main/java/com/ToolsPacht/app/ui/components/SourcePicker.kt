package com.jartool.app.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jartool.app.core.Workspace
import com.jartool.app.core.formatSize
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PickerDialog { None, Framework, Projects }

/** Pemilih sumber JAR: framework sistem, berkas dari penyimpanan, atau proyek yang sudah ada. */
class SourcePicker(private val ctx: Context, private val scope: CoroutineScope) {
    var onPicked: (String) -> Unit = {}
    var dialog by mutableStateOf(PickerDialog.None)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var launcher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null

    fun openFile() { launcher?.launch(arrayOf("*/*")) }
    fun openFramework() { dialog = PickerDialog.Framework }
    fun openProjects() { dialog = PickerDialog.Projects }
    fun dismiss() { dialog = PickerDialog.None }
    fun choose(name: String) { dialog = PickerDialog.None; onPicked(name) }

    private fun import(block: () -> String) {
        dialog = PickerDialog.None
        scope.launch {
            busy = true
            try {
                val name = withContext(Dispatchers.IO) { block() }
                onPicked(name)
            } catch (e: Exception) {
                error = e.message ?: "Gagal membuka berkas"
            } finally {
                busy = false
            }
        }
    }

    fun importUri(uri: Uri) = import { Workspace.importUri(ctx, uri) }
    fun importFile(f: File) = import { Workspace.importFile(ctx, f) }
}

@Composable
fun rememberSourcePicker(onPicked: (String) -> Unit): SourcePicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = remember { SourcePicker(ctx, scope) }
    picker.onPicked = onPicked
    picker.launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picker.importUri(uri)
    }
    return picker
}

private fun frameworkJars(): List<File> =
    listOf("/system/framework", "/system_ext/framework")
        .flatMap { dir ->
            File(dir).listFiles { f -> f.isFile && f.name.endsWith(".jar") && f.canRead() }?.toList().orEmpty()
        }
        .sortedBy { it.name }

@Composable
fun SourcePickerDialogs(p: SourcePicker) {
    val ctx = LocalContext.current
    when (p.dialog) {
        PickerDialog.Framework -> {
            val files = remember { frameworkJars() }
            ListDialog(
                title = "Framework sistem",
                empty = "Tidak ada JAR yang bisa dibaca di /system/framework.",
                items = files.map { it.name to formatSize(it.length()) },
                onDismiss = p::dismiss,
            ) { i -> p.importFile(files[i]) }
        }
        PickerDialog.Projects -> {
            val names = remember { Workspace.projects(ctx).map { it.name } }
            ListDialog(
                title = "Proyek yang ada",
                empty = "Belum ada proyek.",
                items = names.map { it to "" },
                onDismiss = p::dismiss,
            ) { i -> p.choose(names[i]) }
        }
        PickerDialog.None -> {}
    }

    if (p.busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            containerColor = JarColors.SurfaceHigh,
            title = { Text("Menyalin berkas") },
            text = { CircularProgressIndicator() },
        )
    }
    p.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { p.error = null },
            containerColor = JarColors.SurfaceHigh,
            confirmButton = { TextButton(onClick = { p.error = null }) { Text("Tutup") } },
            title = { Text("Gagal") },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun ListDialog(
    title: String,
    empty: String,
    items: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onClick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarColors.SurfaceHigh,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(empty, color = JarColors.TextMuted)
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(items) { i, item ->
                        ListItem(
                            headlineContent = { Text(item.first) },
                            supportingContent = { if (item.second.isNotEmpty()) Text(item.second) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onClick(i) },
                        )
                    }
                }
            }
        },
    )
}
