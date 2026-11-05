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
import com.devtool.app.core.Decompiler
import com.devtool.app.core.Prefs
import com.devtool.app.ui.components.JarTopBarActions
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val KEYWORDS = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
    "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
)
private val TOKEN = Regex("""//.*|/\*.*?\*/|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|@\w+|\b\d[\w.]*\b|\b[A-Za-z_]\w*\b""")

private val C_COMMENT = Color(0xFF8A8880)
private val C_STRING = Color(0xFF9DBB8A)
private val C_KEYWORD = Color(0xFF6B93D6)
private val C_ANNOT = Color(0xFFC4A66F)
private val C_NUMBER = Color(0xFFCBA37E)

private fun highlight(lines: List<String>): List<AnnotatedString> {
    var inBlock = false
    val comment = SpanStyle(color = C_COMMENT)
    return lines.map { line ->
        buildAnnotatedString {
            var text = line
            if (inBlock) {
                val end = text.indexOf("*/")
                if (end < 0) {
                    withStyle(comment) { append(text) }
                    return@buildAnnotatedString
                }
                withStyle(comment) { append(text.substring(0, end + 2)) }
                inBlock = false
                text = text.substring(end + 2)
            }
            var tail = ""
            val open = text.lastIndexOf("/*")
            if (open >= 0 && text.indexOf("*/", open) < 0) {
                tail = text.substring(open)
                text = text.substring(0, open)
                inBlock = true
            }
            var last = 0
            for (m in TOKEN.findAll(text)) {
                if (m.range.first > last) append(text.substring(last, m.range.first))
                val t = m.value
                val color = when {
                    t.startsWith("//") || t.startsWith("/*") -> C_COMMENT
                    t.startsWith("\"") || t.startsWith("'") -> C_STRING
                    t.startsWith("@") -> C_ANNOT
                    t[0].isDigit() -> C_NUMBER
                    t in KEYWORDS -> C_KEYWORD
                    else -> null
                }
                if (color == null) append(t) else withStyle(SpanStyle(color = color)) { append(t) }
                last = m.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
            if (tail.isNotEmpty()) withStyle(comment) { append(tail) }
        }
    }
}

/** Menampilkan hasil dekompilasi jadx untuk satu class. */
@Composable
fun CodeScreen(project: String, cls: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var code by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<AnnotatedString>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Menyiapkan...") }
    var fontSize by remember { mutableIntStateOf(12) }

    LaunchedEffect(project, cls) {
        try {
            if (Prefs.decompileMode(ctx) == 2) {
                error("Opsi dekompilasi \"Hanya resource\" tidak menghasilkan kode. Ubah ke \"Kode dan resource\" atau \"Hanya kode\".")
            }
            if (!Decompiler.isLoaded(project)) {
                status = "Memuat jadx. JAR besar bisa memakan waktu dan memori."
                Decompiler.load(ctx, project)
            }
            status = "Mendekompilasi ${cls.substringAfterLast('.')}..."
            val result = Decompiler.decompile(cls)
            code = result
            lines = withContext(Dispatchers.Default) { highlight(result.lines()) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = if (t is OutOfMemoryError) {
                "Memori habis. Turunkan thread dekompilasi ke 1, tutup aplikasi lain, lalu coba lagi."
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