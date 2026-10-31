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
import com.jartool.app.ui.theme.JarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PickerDialog { None, Projects }

/** Pemilih sumber JAR: berkas dari penyimpanan atau proyek yang sudah ada. */
class SourcePicker(private val ctx: Context, private val scope: CoroutineScope) {
    var onPicked: (String) -> Unit = {}
    var dialog by mutableStateOf(PickerDialog.None)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var launcher: ManagedActivityResultLauncher<Array<String>, Uri?>? = null

    fun openFile() { launcher?.launch(arrayOf("*/*")) }
    fun openProjects() { dialog = PickerDialog.Projects }
    fun dismiss() { dialog = PickerDialog.None }
    fun choose(name: String) { dialog = PickerDialog.None; onPicked(name) }

    fun importUri(uri: Uri) {
        dialog = PickerDialog.None
        scope.launch {
            busy = true
            try {
                val name = withContext(Dispatchers.IO) { Workspace.importUri(ctx, uri) }
                onPicked(name)
            } catch (e: Exception) {
                error = e.message ?: "Gagal membuka berkas"
            } finally {
                busy = false
            }
        }
    }
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

@Composable
fun SourcePickerDialogs(p: SourcePicker) {
    val ctx = LocalContext.current
    if (p.dialog == PickerDialog.Projects) {
        val names = remember { Workspace.projects(ctx).map { it.name } }
        AlertDialog(
            onDismissRequest = p::dismiss,
            containerColor = JarColors.SurfaceHigh,
            confirmButton = { TextButton(onClick = p::dismiss) { Text("Tutup") } },
            title = { Text("Proyek yang ada") },
            text = {
                if (names.isEmpty()) {
                    Text("Belum ada proyek.", color = JarColors.TextMuted)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(names) { _, n ->
                            ListItem(
                                headlineContent = { Text(n) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { p.choose(n) },
                            )
                        }
                    }
                }
            },
        )
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
