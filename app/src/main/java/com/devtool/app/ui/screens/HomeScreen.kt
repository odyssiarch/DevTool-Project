package com.devtool.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.ui.Routes

private val Bg = Color(0xFF05070C)
private val GlassTop = Color(0xFF1B2332)
private val GlassBottom = Color(0xFF0E131C)
private val Stroke = Color(0xFF2A3446)
private val TextMain = Color(0xFFE8ECF4)
private val TextSub = Color(0xFF8E98AB)
private val Blue = Color(0xFF6C9BFF)
private val Orange = Color(0xFFFFB067)

@Composable
fun HomeScreen(
    onOpenTool: (String) -> Unit,
    onPickFile: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 110.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeroLogo(64.dp)
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("DevTool", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Bongkar, edit, dan susun ulang JAR langsung di HP",
                        color = TextSub, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            AboutCard()

            Spacer(Modifier.height(14.dp))
            QuickChip(Icons.Outlined.Code, "Lihat kode Java dari JAR", Blue) { onOpenTool(Routes.SOURCE) }
            Spacer(Modifier.height(10.dp))
            QuickChip(Icons.Outlined.Edit, "Edit Smali lalu susun ulang", Orange) { onOpenTool(Routes.EDIT) }
            Spacer(Modifier.height(10.dp))
            QuickChip(Icons.Outlined.SwapVert, "Bandingkan dua versi JAR", Blue) { onOpenTool(Routes.COMPARE) }
            
            // TOMBOL RAMOOPS BARU
            Spacer(Modifier.height(10.dp))
            QuickChip(Icons.Outlined.Warning, "Baca Logcat / Ramoops", Orange) { onOpenTool(Routes.LOGCAT) }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF2A3B63), Color(0xFF1C2742))))
                .border(1.dp, Color(0xFF3A4D78), RoundedCornerShape(28.dp))
                .clickable(onClick = onPickFile),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Add, null, tint = Blue)
            Spacer(Modifier.size(8.dp))
            Text("Pilih file JAR / DEX", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HeroLogo(size: Dp) {
    val shape = RoundedCornerShape(size * 0.31f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF223052), Color(0xFF111827))))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFF4B6199), Color(0xFF1E2638))), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Code, null, tint = Blue, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun AboutCard() {
    GlassBox(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Yang bisa dilakukan", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(8.dp))
                Text(
                    "v0.3", color = Blue, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF16213A))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            AboutLine("Membongkar JAR / classes.dex ke kode Smali dan Java")
            AboutLine("Mengedit Smali lalu merakit ulang jadi JAR atau DEX")
            AboutLine("Mendukung dex 035 – 041, termasuk 2 dex dalam 1 kontainer")
            AboutLine("Membandingkan dua JAR sampai level class dan method")
            AboutLine("Membaca Ramoops untuk mendeteksi penyebab bootloop")
        }
    }
}

@Composable
private fun AboutLine(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Blue),
        )
        Spacer(Modifier.size(10.dp))
        Text(text, color = TextSub, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun QuickChip(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF111722))
            .border(1.dp, Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Text(label, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = TextSub, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GlassBox(
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GlassTop, GlassBottom)))
            .border(1.dp, Stroke, shape),
    ) { content() }
}