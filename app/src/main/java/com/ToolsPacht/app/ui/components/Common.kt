package com.jartool.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jartool.app.ui.theme.JarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali") } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = JarColors.Black,
            titleContentColor = JarColors.TextPrimary,
            navigationIconContentColor = JarColors.TextPrimary,
        ),
    )
}

@Composable
fun IconTile(icon: ImageVector, size: Dp = 48.dp) {
    Surface(shape = RoundedCornerShape(14.dp), color = JarColors.BlueDim, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = JarColors.Blue) }
    }
}

@Composable
fun JarCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JarColors.Surface),
        border = BorderStroke(1.dp, JarColors.Outline),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = JarColors.TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = JarColors.TextMuted)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = JarColors.Outline, modifier = Modifier.padding(start = 78.dp))
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = JarColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, color = JarColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
