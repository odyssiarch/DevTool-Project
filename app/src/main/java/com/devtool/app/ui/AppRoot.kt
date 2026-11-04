package com.devtool.app.ui

import android.net.Uri
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
    Tools("tools", "Alat", Icons.Outlined.Code),
    Projects("projects", "Proyek", Icons.Outlined.Folder),
    Settings("settings", "Setelan", Icons.Outlined.Tune),
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

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
        NavHost(nav, startDestination = Dest.Tools.route, modifier = Modifier.padding(pad)) {
            composable(Dest.Tools.route) {
                ToolsScreen(onOpen = { id ->
                    nav.navigate(
                        when (id) {
                            "smali" -> "edit"
                            "info" -> "info"
                            "compare" -> "compare"
                            "source" -> "source"
                            "export" -> "export"
                            else -> "tool/$id"
                        },
                    )
                })
            }
            composable(Dest.Projects.route) {
                ProjectsScreen(
                    onOpen = { nav.navigate("project/$it?edit=true") },
                    onOpenEdited = { nav.navigate("edited/$it") },
                )
            }
            composable(Dest.Settings.route) { SettingsScreen() }
            composable("edit") { EditJarScreen(onBack = { nav.popBackStack() }) }
            composable("edited/{name}") { e ->
                EditedScreen(e.arguments?.getString("name").orEmpty()) { nav.popBackStack() }
            }
            composable("info") {
                SourceScreen("INFORMATION-JAR", onBack = { nav.popBackStack() }, onPicked = { nav.navigate("project/$it") })
            }
            composable("compare") { CompareScreen(onBack = { nav.popBackStack() }) }
            composable(
                "project/{name}?edit={edit}",
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("edit") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                ProjectScreen(
                    name = e.arguments?.getString("name").orEmpty(),
                    edit = e.arguments?.getBoolean("edit") ?: false,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("source") {
                DecompileScreen(onBack = { nav.popBackStack() }, onPicked = { nav.navigate("browse/$it") })
            }
            composable("browse/{name}") { e ->
                val project = e.arguments?.getString("name").orEmpty()
                BrowseScreen(
                    project,
                    onOpenCode = { nav.navigate("code/$project?cls=${Uri.encode(it)}") },
                    onBack = { nav.popBackStack() },
                    onOpenSmali = { nav.navigate("smali/$project?cls=${Uri.encode(it)}") },
                )
            }
            composable(
                "smali/{project}?cls={cls}",
                arguments = listOf(
                    navArgument("project") { type = NavType.StringType },
                    navArgument("cls") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                SmaliCodeScreen(
                    e.arguments?.getString("project").orEmpty(),
                    e.arguments?.getString("cls").orEmpty(),
                ) { nav.popBackStack() }
            }
            composable(
                "code/{project}?cls={cls}",
                arguments = listOf(
                    navArgument("project") { type = NavType.StringType },
                    navArgument("cls") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                CodeScreen(
                    e.arguments?.getString("project").orEmpty(),
                    e.arguments?.getString("cls").orEmpty(),
                ) { nav.popBackStack() }
            }
            composable("export") {
                SourceScreen(
                    "EXPOR-JAR/DEX",
                    onBack = { nav.popBackStack() },
                    onPicked = { nav.navigate("exportproject/$it") },
                    description = "Simpan JAR atau classes.dex dari proyek ke penyimpanan",
                )
            }
            
        }
    }
}

