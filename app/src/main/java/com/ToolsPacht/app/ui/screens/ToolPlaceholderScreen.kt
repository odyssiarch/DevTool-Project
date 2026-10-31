package com.jartool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jartool.app.ui.theme.JarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPlaceholderScreen(id: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(id) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = JarColors.Black, titleContentColor = JarColors.TextPrimary, navigationIconContentColor = JarColors.TextPrimary),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Layar ini belum dibuat", color = JarColors.TextMuted)
        }
    }
}
