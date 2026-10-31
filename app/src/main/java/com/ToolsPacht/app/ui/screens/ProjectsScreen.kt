package com.jartool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jartool.app.ui.theme.JarColors

@Composable
fun ProjectsScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Belum ada proyek", style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Buka berkas JAR untuk membuat proyek pertama.", color = JarColors.TextMuted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { /* TODO: pilih berkas lewat SAF */ }) { Text("Buka JAR") }
    }
}
