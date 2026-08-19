package com.signalbooster.app.presentation.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.signalbooster.app.presentation.capability.CapabilityScreen
import com.signalbooster.app.presentation.capability.CapabilityViewModel
import com.signalbooster.app.presentation.connection.DashboardScreen
import com.signalbooster.app.presentation.connection.DashboardViewModel
import com.signalbooster.app.presentation.crowdmode.CrowdModeScreen
import com.signalbooster.app.presentation.crowdmode.CrowdModeViewModel
import com.signalbooster.app.presentation.diagnostics.DiagnosticsScreen
import com.signalbooster.app.presentation.diagnostics.DiagnosticsViewModel
import com.signalbooster.app.presentation.privacy.PrivacyScreen
import com.signalbooster.app.presentation.privacy.PrivacyViewModel
import com.signalbooster.app.presentation.settings.SettingsScreen
import com.signalbooster.app.presentation.settings.SettingsViewModel
import com.signalbooster.app.domain.models.SettingsDestination

@Composable
fun AppNavigation(
    onOpenSettings: (SettingsDestination) -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Dashboard,
        Screen.Diagnostics,
        Screen.CrowdMode,
        Screen.Privacy,
        Screen.Settings
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletOrWide = maxWidth >= 600.dp
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        if (isTabletOrWide) {
            // Adaptive Tablet / Wide Landscape Layout: NavigationRail on left
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    items.forEach { screen ->
                        NavigationRailItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f)
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        } else {
            // Standard Phone Compact Layout: NavigationBar at bottom
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = {
                                    Text(
                                        text = screen.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                AppNavHost(
                    navController = navController,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    onOpenSettings: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            val viewModel: DashboardViewModel = hiltViewModel()
            DashboardScreen(
                viewModel = viewModel,
                onOpenSettings = onOpenSettings
            )
        }
        composable(Screen.Diagnostics.route) {
            val viewModel: DiagnosticsViewModel = hiltViewModel()
            DiagnosticsScreen(viewModel = viewModel)
        }
        composable(Screen.CrowdMode.route) {
            val viewModel: CrowdModeViewModel = hiltViewModel()
            CrowdModeScreen(
                viewModel = viewModel,
                onOpenSettings = onOpenSettings
            )
        }
        composable(Screen.Privacy.route) {
            val viewModel: PrivacyViewModel = hiltViewModel()
            PrivacyScreen(viewModel = viewModel)
        }
        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateToCapabilities = {
                    navController.navigate("capabilities")
                }
            )
        }
        composable("capabilities") {
            val viewModel: CapabilityViewModel = hiltViewModel()
            CapabilityScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

