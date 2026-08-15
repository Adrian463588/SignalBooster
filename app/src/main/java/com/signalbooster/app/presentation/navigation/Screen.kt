package com.signalbooster.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.SignalCellularAlt)
    object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Default.Analytics)
    object CrowdMode : Screen("crowd_mode", "Crowd Mode", Icons.Default.Group)
    object Privacy : Screen("privacy", "Privacy", Icons.Default.Security)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
