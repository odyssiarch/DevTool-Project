package com.devtool.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    Tools(Routes.TOOLS, "Alat", Icons.Outlined.Code),
    Projects(Routes.PROJECTS, "Proyek", Icons.Outlined.Folder),
    Settings(Routes.SETTINGS, "Setelan", Icons.Outlined.Tune),
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val back: () -> Unit = { nav.popBackStack() }

    Scaffold(
        containerColor = JarColors.Black,
        bottomBar = {
            if (Dest.entries.any { it.route == route }) {
                NavigationBar(containerColor = JarColors.Surface) {
                    Dest.entries.forEach { d ->
                        NavigationBarItem(
                            selected = route == d.route,
                            onClick = {
                                nav.navigate(d.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(d.icon, contentDescription = d.label) },
                            label = { Text(d.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = JarColors.Blue,
                                selectedTextColor = JarColors.Blue,
                                indicatorColor = JarColors.BlueDim,
                                unselectedIconColor = JarColors.TextMuted,
                                unselectedTextColor = JarColors.TextMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Routes.TOOLS, modifier = Modifier.padding(pad)) {

            // ----- tab bawah -----
            composable(Routes.TOOLS) {
                HomeScreen(
                    onOpenTool = { nav.navigate(it) },
                    onPickFile = { nav.navigate(Routes.EDIT) },
                )
            }
            composable(Routes.PROJECTS) {
                ProjectsScreen(
                    onOpen = { nav.navigate(Routes.project(it, edit = true)) },
                    onOpenEdited = { nav.navigate(Routes.edited(it)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }

            // ----- edit JAR -----
            composable(Routes.EDIT) { EditJarScreen(onBack = back) }
            composable(Routes.EDITED) { e ->
                EditedScreen(e.arguments?.getString(Routes.ARG_NAME).orEmpty()) { nav.popBackStack() }
            }

            // ----- info & bandingkan -----
            composable(Routes.INFO) {
                SourceScreen("INFORMATION-JAR", onBack = back, onPicked = { nav.navigate(Routes.project(it)) })
            }
            composable(Routes.COMPARE) { CompareScreen(onBack = back) }
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
                )
            }

            // ----- lihat kode sumber -----
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

            // ----- ekspor -----
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
