package com.devtool.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devtool.app.core.DeepResult
import com.devtool.app.core.JarNames
import com.devtool.app.core.MethodChange
import com.devtool.app.core.SmaliDiff
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.CancellationException

private val GarisEdit = Color(0x33FFFFFF)
private val HijauEdit = Color(0xFF5BD98C)
private val KuningEdit = Color(0xFFFFC857)

/**
 * Daftar class dan method Smali yang sudah Anda ubah di satu proyek.
 * Dibandingkan dengan hasil bongkar asli JAR, jadi selalu sesuai isi folder Smali saat ini.
 */
@Composable
fun EditedScreen(project: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val nama = JarNames.display(project)
    var d by remember { mutableStateOf<DeepResult?>(null) }
    var status by remember { mutableStateOf("Memeriksa Smali yang diubah...") }
    var error by remember { mutableStateOf<String?>(null) }
    var selClass by remember { mutableStateOf<String?>(null) }
    var selMethod by remember { mutableStateOf<MethodChange?>(null) }

    LaunchedEffect(project) {
        try {
            d = SmaliDiff.compareEdited(ctx, project, "$nama (asli)", "$nama (diedit)") { status = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Gagal memeriksa Smali"
        }
    }

    BackHandler(enabled = selClass != null) {
        if (selMethod != null) selMethod = null else selClass = null
    }

    val r = d
    val k = selClass
    val m = selMethod
    if (r != null && k != null && m != null) {
        MethodDiffScreen(k, m, r.oldName, r.newName) { selMethod = null }
    } else if (r != null && k != null) {
        ClassDetailScreen(r, k, onBack = { selClass = null }, onOpen = { selMethod = it })
    } else {
        Column(Modifier.fillMaxSize()) {
            JarTopBar(nama, onBack)
            val err = error
            when {
                err != null -> Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                r == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(status, color = JarColors.TextMuted, textAlign = TextAlign.Center)
                }
                r.changes.isEmpty() -> Text(
                    "Belum ada Smali yang diubah. Bongkar ke Smali, ubah file .smali-nya, lalu buka halaman ini lagi.",
                    color = JarColors.TextMuted,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "Class yang sudah diubah (${r.changes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarColors.TextPrimary,
                        )
                        Text(
                            "Ketuk class untuk melihat method-nya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarColors.TextMuted,
                        )
                    }
                    item {
                        JarCard {
                            Column(Modifier.fillMaxWidth()) {
                                r.changes.forEachIndexed { i, c ->
                                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = GarisEdit)
                                    val ringkas = buildString {
                                        if (c.changed > 0) append("${c.changed} method diubah")
                                        if (c.added > 0) append(if (isEmpty()) "" else ", ").append("${c.added} method baru")
                                        if (c.removed > 0) append(if (isEmpty()) "" else ", ").append("${c.removed} method dihapus")
                                        if (isEmpty()) append("field atau header class diubah")
                                    }
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .clickable { selClass = c.key }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        Text(c.simpleName, style = MaterialTheme.typography.bodyLarge, color = JarColors.TextPrimary)
                                        Text(
                                            c.dotted.substringBeforeLast('.', ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = JarColors.TextMuted,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(ringkas, style = MaterialTheme.typography.bodyMedium, color = KuningEdit)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dipakai tab Proyek: satu blok per proyek yang Smali-nya sudah diubah. */
@Composable
fun EditedSummary(project: String, keys: List<String>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(JarNames.display(project), style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
        Text("${keys.size} class sudah diubah", style = MaterialTheme.typography.bodyMedium, color = HijauEdit)
        Spacer(Modifier.height(6.dp))
        keys.take(8).forEach { k ->
            val penuh = k.removeSuffix(".smali").replace('/', '.')
            Text(penuh.substringAfterLast('.'), style = MaterialTheme.typography.bodyLarge, color = JarColors.TextPrimary)
            val paket = penuh.substringBeforeLast('.', "")
            if (paket.isNotEmpty()) {
                Text(paket, style = MaterialTheme.typography.bodySmall, color = JarColors.TextMuted)
            }
            Spacer(Modifier.height(6.dp))
        }
        if (keys.size > 8) {
            Text("dan ${keys.size - 8} class lainnya", style = MaterialTheme.typography.bodyMedium, color = JarColors.TextMuted)
        }
    }
}
