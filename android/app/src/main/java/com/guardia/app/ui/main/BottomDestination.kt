package com.guardia.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomDestination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Shield),
    PEOPLE("people", "People", Icons.Filled.People),
    ACTIVITY("activity", "Activity", Icons.Filled.History),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}
