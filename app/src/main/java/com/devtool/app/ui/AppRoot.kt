package com.devtool.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devtool.app.ui.screens.*
import com.devtool.app.ui.theme.JarColors

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "Beranda", Icons.Filled.Home),
    Menu(Routes.MENU, "Menu", Icons.Filled.GridView),
    Terminal(Routes.TERMINAL, "Terminal", Icons.Filled.Terminal),
    Settings(Routes.SETTINGS, "Setelan", Icons.Filled.Settings),
}

/** Nav bawah model pil melayang: tab aktif menampilkan ikon + label di dalam pil. */
@Composable
private fun PillNav(route: String?, onSelect: (Dest) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = JarColors.Surface,
            border = BorderStroke(1.dp, JarColors.Outline),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dest.entries.forEach { d ->
                    val sel = route == d.route
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (sel) JarColors.SurfaceHigh else Color.Transparent)
                            .clickable { onSelect(d) }
                            .padding(horizontal = if (sel) 20.dp else 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(d.icon, d.label, tint = if (sel) JarColors.Accent else JarColors.TextMuted)
                        if (sel) {
                            Spacer(Modifier.width(10.dp))
                            Text(d.label, color = JarColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    val goTab: (String) -> Unit = { r ->
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val back: () -> Unit = {
        if (!nav.popBackStack()) {
            nav.navigate(Routes.HOME) {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = JarColors.Black,
        bottomBar = {
            if (Dest.entries.any { it.route == route }) {
                PillNav(route) { goTab(it.route) }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenMenu = { goTab(Routes.MENU) },
                    onOpenSettings = { goTab(Routes.SETTINGS) },
                    onPickFile = { nav.navigate(Routes.EDIT) },
                )
            }
            composable(Routes.MENU) { MenuScreen(onOpen = { nav.navigate(it) }) }
            composable(Routes.TERMINAL) { LiveLogcatScreen() }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    onOpen = { nav.navigate(Routes.project(it, edit = true)) },
                    onOpenEdited = { nav.navigate(Routes.edited(it)) },
                    onBack = back,
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }

            composable(Routes.EDIT) { 
                EditJarScreen(
                    onBack = back,
                    onExport = { nav.navigate(Routes.exportProject(it)) }
                ) 
            }
            composable(Routes.EDITED) { e ->
                EditedScreen(e.arguments?.getString(Routes.ARG_NAME).orEmpty()) { nav.popBackStack() }
            }

            composable(Routes.INFO) {
                SourceScreen("INFORMATION-JAR", onBack = back, onPicked = { nav.navigate(Routes.project(it)) })
            }
            composable(Routes.COMPARE) { CompareScreen(onBack = back) }
            
            // --- FITUR BARU: LOGCAT ---
            composable(Routes.LOGCAT) { LogcatScreen(onBack = back) }
            
            composable(
                Routes.PROJECT,
                arguments = listOf(
                    navArgument(Routes.ARG_NAME) { type = NavType.StringType },
                    navArgument(Routes.ARG_EDIT) { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                ProjectScreen(
                    name = e.arguments?.getString(Routes.ARG_NAME).orEmpty(),
                    edit = e.arguments?.getBoolean(Routes.ARG_EDIT) ?: false,
                    onBack = back,
                    onExport = { nav.navigate(Routes.exportProject(it)) }
                )
            }

            composable(Routes.SOURCE) {
                DecompileScreen(onBack = back, onPicked = { nav.navigate(Routes.browse(it)) })
            }
            composable(Routes.BROWSE) { e ->
                val project = e.arguments?.getString(Routes.ARG_NAME).orEmpty()
                BrowseScreen(
                    project,
                    onOpenCode = { nav.navigate(Routes.code(project, it)) },
                    onBack = back,
                    onOpenSmali = { nav.navigate(Routes.smali(project, it)) },
                )
            }
            composable(
                Routes.SMALI,
                arguments = listOf(
                    navArgument(Routes.ARG_PROJECT) { type = NavType.StringType },
                    navArgument(Routes.ARG_CLS) { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                SmaliCodeScreen(
                    e.arguments?.getString(Routes.ARG_PROJECT).orEmpty(),
                    e.arguments?.getString(Routes.ARG_CLS).orEmpty(),
                ) { nav.popBackStack() }
            }
            composable(
                Routes.CODE,
                arguments = listOf(
                    navArgument(Routes.ARG_PROJECT) { type = NavType.StringType },
                    navArgument(Routes.ARG_CLS) { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                CodeScreen(
                    e.arguments?.getString(Routes.ARG_PROJECT).orEmpty(),
                    e.arguments?.getString(Routes.ARG_CLS).orEmpty(),
                ) { nav.popBackStack() }
            }

            composable(Routes.EXPORT) {
                SourceScreen(
                    "EXPORT-JAR/DEX",
                    onBack = back,
                    onPicked = { nav.navigate(Routes.exportProject(it)) },
                    description = "Simpan JAR atau classes.dex dari proyek ke penyimpanan",
                )
            }
            composable(Routes.EXPORT_PROJECT) { e ->
                ExportScreen(e.arguments?.getString(Routes.ARG_NAME).orEmpty()) { nav.popBackStack() }
            }
        }
    }
}