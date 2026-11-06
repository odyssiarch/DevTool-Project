package com.devtool.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.ui.theme.JarColors

/** Kartu besar berujung bulat dengan judul huruf kapital kecil di atasnya. */
@Composable
fun PillCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = JarColors.Surface,
        border = BorderStroke(1.dp, JarColors.Outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            if (title != null) {
                Text(
                    title,
                    color = JarColors.TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

/** Kartu menu: kotak ikon, judul kapital, keterangan, tombol panah bulat. */
@Composable
fun MenuCard(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JarColors.Surface)
            .border(1.dp, JarColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(JarColors.AccentDim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = JarColors.Accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = JarColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(desc, color = JarColors.TextMuted, fontSize = 14.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(JarColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ChevronRight, null, tint = JarColors.TextPrimary)
        }
    }
}

/** Tombol tonal bulat dengan ikon di kiri teks (Salin Log, Simpan Log, dst). */
@Composable
fun TonalAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(JarColors.SurfaceHigh)
            .border(1.dp, JarColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = JarColors.Accent)
        Spacer(Modifier.width(10.dp))
        Text(label, color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Baris lebar "Pilih File ..." dengan panah di kanan. */
@Composable
fun FilePickRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(JarColors.SurfaceHigh)
            .border(1.dp, JarColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = JarColors.Accent)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = JarColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Outlined.ChevronRight, null, tint = JarColors.TextMuted)
    }
}

/** Judul layar besar dengan tombol kembali persegi bulat (opsional). */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .size(56.dp)
                    .clip(shape)
                    .background(JarColors.Surface)
                    .border(1.dp, JarColors.Outline, shape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali", tint = JarColors.TextPrimary)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(title, color = JarColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) {
                Text(subtitle, color = JarColors.TextMuted, fontSize = 14.sp)
            }
        }
    }
}
