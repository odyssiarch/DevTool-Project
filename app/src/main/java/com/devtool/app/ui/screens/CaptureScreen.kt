package com.devtool.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.core.LogCapture
import com.devtool.app.core.LogCaptureService
import com.devtool.app.ui.components.PillCard
import com.devtool.app.ui.components.ScreenHeader
import com.devtool.app.ui.components.TonalAction
import com.devtool.app.ui.theme.JarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF5BD98C)
private val Red = Color(0xFFFF6B6B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by LogCapture.state.collectAsState()

    var selected by remember { mutableStateOf(setOf("fp")) }
    var custom by remember { mutableStateOf("") }
    var allBuf by remember { mutableStateOf(true) }
    var bigBuf by remember { mutableStateOf(true) }
    var kernel by remember { mutableStateOf(true) }
    var dumping by remember { mutableStateOf(false) }
    var dumpStep by remember { mutableStateOf("") }
    var dumpPath by remember { mutableStateOf<String?>(null) }

    fun startNow() {
        val ps = LogCapture.presets.filter { it.id in selected }
        val customOk = custom.isNotBlank()
        val noFilter = ps.any { it.pattern.isEmpty() } || (ps.isEmpty() && !customOk)
        val parts = ps.filter { it.pattern.isNotEmpty() }.map { "(?:${it.pattern})" } +
            (if (customOk) listOf("(?:${custom.trim()})") else emptyList())
        val pattern = if (noFilter) "" else parts.joinToString("|")
        val label = (ps.map { it.id } + (if (customOk) listOf("custom") else emptyList()))
            .joinToString("-")
            .ifEmpty { "all" }
        LogCaptureService.start(ctx, label, pattern, allBuf, bigBuf, kernel)
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startNow() }

    fun onStartClick() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNow()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tangkap Log", "Fingerprint, face unlock, SELinux, crash", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- preset ----------
            PillCard(title = "PRESET FILTER") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LogCapture.presets.forEach { p ->
                        FilterChip(
                            selected = p.id in selected,
                            onClick = { selected = if (p.id in selected) selected - p.id else selected + p.id },
                            label = { Text(p.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarColors.AccentDim,
                                selectedLabelColor = JarColors.Accent,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("Regex tambahan (opsional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarColors.Accent,
                        unfocusedBorderColor = JarColors.Outline,
                    ),
                )
            }

            // ---------- opsi ----------
            PillCard(title = "SUMBER LOG") {
                OptionRow("Semua buffer logcat", "main, system, crash, events, radio, kernel (-b all)", allBuf) { allBuf = it }
                OptionRow("Besarkan buffer jadi 16 MB", "Supaya log lama tidak keburu tertimpa di ROM yang berisik", bigBuf) { bigBuf = it }
                OptionRow("Sertakan dmesg (kernel)", "Driver sidik jari, TEE, dan SELinux denial; ditandai [K]", kernel) { kernel = it }
            }

            // ---------- status ----------
            PillCard(title = "STATUS") {
                Text(
                    if (st.running) "BERJALAN" else "BERHENTI",
                    color = if (st.running) Green else JarColors.TextMuted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (st.note.isNotEmpty()) {
                    Text(st.note, color = JarColors.TextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${st.matched} baris cocok dari ${st.total}",
                    color = JarColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                val path = st.file
                if (path != null) {
                    Text(path, color = JarColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (st.tail.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(JarColors.Black, RoundedCornerShape(16.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            st.tail.takeLast(12).forEach {
                                Text(
                                    it,
                                    color = JarColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { if (st.running) LogCaptureService.stop(ctx) else onStartClick() },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (st.running) Red else JarColors.Accent,
                        contentColor = JarColors.Black,
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(if (st.running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (st.running) "Berhenti" else "Mulai tangkap",
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Setelah mulai, kunci layar lalu tes sidik jari atau wajah. Penangkapan jalan di latar " +
                        "belakang (ada notifikasi) dan tetap hidup walau app ditutup.",
                    color = JarColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            // ---------- diagnostik ----------
            PillCard(title = "DIAGNOSTIK PERANGKAT") {
                Text(
                    "Kumpulkan sekali jalan: service, HAL, proses, library, VINTF, dumpsys fingerprint/face/biometric, " +
                        "dmesg biometrik, crash, dan tombstone. Cocok untuk melihat HAL biometrik apa yang belum jalan di port.",
                    color = JarColors.TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                TonalAction(
                    Icons.Outlined.Fingerprint,
                    if (dumping) "Menjalankan: $dumpStep" else "Dump diagnostik",
                    Modifier.fillMaxWidth(),
                ) {
                    if (!dumping) {
                        dumping = true
                        dumpPath = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val f = LogCapture.diagnose(ctx) { step -> dumpStep = step }
                                withContext(Dispatchers.Main) { dumpPath = f.absolutePath }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { dumpPath = "Gagal: ${e.message}" }
                            } finally {
                                withContext(Dispatchers.Main) { dumping = false }
                            }
                        }
                    }
                }
                val dp = dumpPath
                if (dp != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(dp, color = JarColors.TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OptionRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
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
        Column(Modifier.weight(1f)) {
            Text(label, color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = JarColors.TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
