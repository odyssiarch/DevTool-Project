package com.devtool.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devtool.app.ui.components.*
import com.devtool.app.ui.theme.JarColors

/** Halaman pilih sumber file, dipakai Edit JAR, Info JAR, dan Ekspor. */
@Composable
fun SourceScreen(
    title: String,
    onBack: () -> Unit,
    onPicked: (String) -> Unit,
    description: String? = null,
) {
    val picker = rememberSourcePicker(onPicked)
    Column(Modifier.fillMaxSize()) {
        JarTopBar(title, onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            if (description != null) {
                Text(
                    description,
                    color = JarColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                )
            }
            Text(
                "Sumber file",
                color = JarColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            JarCard {
                SourceRow(Icons.Outlined.Folder, "File JAR", picker::openFile)
                RowDivider()
                SourceRow(Icons.Outlined.History, "Proyek yang ada", picker::openProjects)
            }
            Text(
                "Semua diproses di perangkat. Tidak perlu internet.",
                color = JarColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
    }
    SourcePickerDialogs(picker)
}
