package com.devtool.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devtool.app.ui.Routes
import com.devtool.app.ui.components.MenuCard
import com.devtool.app.ui.theme.JarColors

@Composable
fun MenuScreen(onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 24.dp),
    ) {
        Text("Menu", color = JarColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
        Text("Alat JAR dan fitur servis", color = JarColors.TextMuted, fontSize = 15.sp)
        Spacer(Modifier.height(22.dp))

        MenuCard(
            Icons.Outlined.Code, "LIHAT KODE JAVA",
            "Buka JAR atau DEX lalu baca hasil dekompilasinya.",
        ) { onOpen(Routes.SOURCE) }
        Spacer(Modifier.height(14.dp))

        MenuCard(
            Icons.Outlined.Edit, "EDIT SMALI",
            "Bongkar ke Smali, edit, lalu susun ulang jadi JAR atau DEX.",
        ) { onOpen(Routes.EDIT) }
        Spacer(Modifier.height(14.dp))

        MenuCard(
            Icons.Outlined.SwapVert, "BANDINGKAN JAR",
            "Bandingkan dua versi JAR sampai level class dan method.",
        ) { onOpen(Routes.COMPARE) }
        Spacer(Modifier.height(14.dp))

        MenuCard(
            Icons.Outlined.Folder, "PROYEK",
            "Daftar proyek, Smali yang sudah diubah, dan ekspor.",
        ) { onOpen(Routes.PROJECTS) }
        Spacer(Modifier.height(14.dp))

        MenuCard(
            Icons.Outlined.Fingerprint, "TANGKAP LOG",
            "Tangkap log fingerprint, face unlock, dan SELinux di latar belakang.",
        ) { onOpen(Routes.CAPTURE) }
        Spacer(Modifier.height(14.dp))

        MenuCard(
            Icons.Outlined.Build, "BINARY / .SO",
            "Baca file ELF dan .so, lihat simbol dan disassembly, lalu patch.",
        ) { onOpen(Routes.SO) }
        Spacer(Modifier.height(14.dp))

        MenuCard(
            Icons.Outlined.Warning, "LOGCAT / RAMOOPS",
            "Baca log lama dan Ramoops untuk mencari penyebab bootloop.",
        ) { onOpen(Routes.LOGCAT) }
    }
}
