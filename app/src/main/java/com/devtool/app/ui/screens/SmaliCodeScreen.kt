package com.devtool.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.core.SmaliCode
import com.devtool.app.ui.components.JarTopBarActions
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val S_COMMENT = Color(0xFF6B7788)
private val S_STRING = Color(0xFF9DBB8A)
private val S_DIRECTIVE = Color(0xFF6B93D6)
private val S_OPCODE = Color(0xFFD08FD6)
private val S_TYPE = Color(0xFF7FC8C8)
private val S_REGISTER = Color(0xFFC4A66F)
private val S_LABEL = Color(0xFFE0A070)
private val S_NUMBER = Color(0xFFCBA37E)

private val SMALI_TOKEN = Regex("""#.*|"(?:[^"\\]|\\.)*"|L[\w/$]+;|\b[vp]\d+\b|:[\w]+|-?\b0x[0-9a-fA-F]+[a-zA-Z]?\b""")

private fun highlightSmali(lines: List<String>): List<AnnotatedString> = lines.map { line ->
    buildAnnotatedString {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        if (indent > 0) append(line.substring(0, indent))
        if (trimmed.startsWith("#")) {
            withStyle(SpanStyle(color = S_COMMENT)) { append(trimmed) }
            return@buildAnnotatedString
        }
        var rest = trimmed
        if (trimmed.isNotEmpty() && (trimmed[0] == '.' || trimmed[0].isLetter())) {
            val sp = trimmed.indexOfFirst { it == ' ' || it == '\t' }
            val head = if (sp < 0) trimmed else trimmed.substring(0, sp)
            val warna = if (trimmed[0] == '.') S_DIRECTIVE else S_OPCODE
            withStyle(SpanStyle(color = warna)) { append(head) }
            rest = if (sp < 0) "" else trimmed.substring(sp)
        }
        var last = 0
        for (m in SMALI_TOKEN.findAll(rest)) {
            if (m.range.first > last) append(rest.substring(last, m.range.first))
            val t = m.value
            val warna = when {
                t.startsWith("#") -> S_COMMENT
                t.startsWith("\"") -> S_STRING
                t.startsWith("L") && t.endsWith(";") -> S_TYPE
                t[0] == ':' -> S_LABEL
                t[0] == 'v' || t[0] == 'p' -> S_REGISTER
                else -> S_NUMBER
            }
            withStyle(SpanStyle(color = warna)) { append(t) }
            last = m.range.last + 1
        }
        if (last < rest.length) append(rest.substring(last))
    }
}

/** Menampilkan kode Smali satu class, tanpa perlu diubah ke Java. */
@Composable
fun SmaliCodeScreen(project: String, cls: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var code by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<AnnotatedString>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Menyiapkan Smali...") }
    var fontSize by remember { mutableIntStateOf(12) }

    LaunchedEffect(project, cls) {
        try {
            val text = SmaliCode.load(ctx, project, cls) { status = it }
            code = text
            lines = withContext(Dispatchers.Default) { highlightSmali(text.trimEnd('\n').split("\n")) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = if (t is OutOfMemoryError) {
                "Memori habis. Tutup aplikasi lain lalu coba lagi."
            } else {
                t.message ?: t.javaClass.simpleName
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        JarTopBarActions(cls.substringAfterLast('.'), onBack) {
            IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(8) }) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = "Perkecil")
            }
            IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(22) }) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = "Perbesar")
            }
            IconButton(onClick = { code?.let { clip.setText(AnnotatedString(it)) } }, enabled = code != null) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin semua")
            }
        }
        val err = error
        val ls = lines
        when {
            err != null -> Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            ls == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(status, color = JarColors.TextMuted, textAlign = TextAlign.Center)
            }
            else -> {
                val hScroll = rememberScrollState()
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(ls) { i, line ->
                        Row(Modifier.horizontalScroll(hScroll)) {
                            Text(
                                "${i + 1}",
                                color = JarColors.TextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(52.dp).padding(end = 12.dp),
                            )
                            Text(
                                line,
                                color = JarColors.TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}