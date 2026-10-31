package com.jartool.app.ui

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jartool.app.ui.screens.*
import com.jartool.app.ui.theme.JarColors

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
            composable(Dest.Tools.route) { ToolsScreen(onOpen = { nav.navigate("tool/$it") }) }
            composable(Dest.Projects.route) { ProjectsScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
            composable("tool/{id}") { e ->
                ToolPlaceholderScreen(e.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
            }
        }
    }
}
