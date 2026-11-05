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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.core.Workspace
import com.devtool.app.ui.components.FilePickRow
import com.devtool.app.ui.components.PillCard
import com.devtool.app.ui.theme.JarColors

private val Green = Color(0xFF5BD98C)
private val Amber = Color(0xFFFFC857)

@Composable
fun HomeScreen(
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFile: () -> Unit,
) {
    val ctx = LocalContext.current
    val projectCount = remember { Workspace.projects(ctx).size }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        // Header: tombol menu, judul, tombol setelan
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Icons.Filled.Menu, "Menu", onOpenMenu)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = JarColors.TextPrimary)) { append("Dev") }
                        withStyle(SpanStyle(color = JarColors.Accent)) { append("Tool") }
                    },
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("JAR & DEX Service Tool", color = JarColors.TextMuted, fontSize = 15.sp)
            }
            SquareButton(Icons.Filled.Settings, "Setelan", onOpenSettings)
        }

        Spacer(Modifier.height(18.dp))

        PillCard(title = "STATUS PROYEK") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(JarColors.AccentDim)
                        .border(3.dp, JarColors.Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Code, null, tint = JarColors.Accent, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    StatusLine("Proyek", projectCount.toString())
                    StatusLine("Dex", "035 – 041")
                    StatusLine("Kontainer", "2 dex / 1 file")
                    StatusLine("Mode", "SIAP", Green)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        PillCard(title = "ALUR KERJA") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Step(Icons.Outlined.FolderOpen, "1", "Pilih JAR", Modifier.weight(1f))
                Step(Icons.Outlined.Code, "2", "Bongkar", Modifier.weight(1f))
                Step(Icons.Outlined.Edit, "3", "Edit Smali", Modifier.weight(1f))
                Step(Icons.Outlined.Build, "4", "Susun ulang", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))

        PillCard(title = "YANG BISA DILAKUKAN") {
            AboutLine("Membongkar JAR / classes.dex ke kode Smali dan Java")
            AboutLine("Mengedit Smali lalu merakit ulang jadi JAR atau DEX")
            AboutLine("Mendukung dex 035 – 041, termasuk 2 dex dalam 1 kontainer")
            AboutLine("Membandingkan dua JAR sampai level class dan method")
            AboutLine("Membaca Ramoops untuk mendeteksi penyebab bootloop")
        }

        Spacer(Modifier.height(14.dp))

        PillCard(title = "BERKAS") {
            Text("File JAR / DEX", color = JarColors.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Belum dipilih", color = Amber, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            FilePickRow(Icons.Outlined.FolderOpen, "Pilih File JAR / DEX", onPickFile)
        }
    }
}

@Composable
private fun SquareButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(56.dp)
            .clip(shape)
            .background(JarColors.Surface)
            .border(1.dp, JarColors.Outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = JarColors.TextPrimary)
    }
}

@Composable
private fun StatusLine(label: String, value: String, valueColor: Color = JarColors.TextPrimary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = JarColors.TextMuted, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Step(icon: ImageVector, number: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(2.dp, JarColors.TextMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = JarColors.TextPrimary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(JarColors.AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = JarColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = JarColors.TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun AboutLine(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(JarColors.Accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = JarColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
