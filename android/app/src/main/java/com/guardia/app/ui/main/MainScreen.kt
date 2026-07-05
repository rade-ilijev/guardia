package com.guardia.app.ui.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guardia.app.ui.screens.activity.ActivityScreen
import com.guardia.app.ui.screens.dashboard.DashboardScreen
import com.guardia.app.ui.screens.intruders.IntrudersScreen
import com.guardia.app.ui.screens.people.AddPersonScreen
import com.guardia.app.ui.screens.people.PeopleScreen
import com.guardia.app.ui.screens.people.PersonDetailScreen
import com.guardia.app.ui.screens.settings.SettingsDetailScreen
import com.guardia.app.ui.screens.settings.SettingsScreen

private object MainRoutes {
    const val ADD_PERSON = "add_person"
    const val SETTINGS_DETAIL = "settings_detail"
    const val INTRUDERS = "intruders"
    const val PERSON = "person"
    const val STATS = "stats"
    const val PAYWALL = "paywall"
    const val ADD_BLOCKED = "add_blocked"
    const val BLOCKED_PEOPLE = "blocked_people"
    const val GALLERY_IMPORT = "gallery_import"
}

@Composable
fun MainScreen(onLock: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BottomDestination.entries.any { it.route == currentRoute }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = BottomDestination.HOME.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 8 } },
            exitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { -it / 18 } },
            popEnterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(300)) { -it / 18 } },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(240)) { it / 8 } },
        ) {
            composable(BottomDestination.HOME.route) {
                val switchTab: (String) -> Unit = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                DashboardScreen(
                    onLock = onLock,
                    onOpenPeople = { switchTab(BottomDestination.PEOPLE.route) },
                    onOpenActivity = { switchTab(BottomDestination.ACTIVITY.route) },
                )
            }
            composable(BottomDestination.PEOPLE.route) {
                PeopleScreen(
                    onAddPerson = { navController.navigate(MainRoutes.ADD_PERSON) },
                    onOpenBlocked = { navController.navigate(MainRoutes.BLOCKED_PEOPLE) },
                    onOpenPerson = { id -> navController.navigate("${MainRoutes.PERSON}/$id") },
                )
            }
            composable(MainRoutes.BLOCKED_PEOPLE) {
                com.guardia.app.ui.screens.people.BlockedPeopleScreen(
                    onBack = { navController.popBackStack() },
                    onAddBlocked = { navController.navigate(MainRoutes.ADD_BLOCKED) },
                    onOpenPerson = { id -> navController.navigate("${MainRoutes.PERSON}/$id") },
                )
            }
            composable("${MainRoutes.PERSON}/{personId}") {
                PersonDetailScreen(
                    onBack = { navController.popBackStack() },
                    onAddFaces = { id -> navController.navigate("${MainRoutes.ADD_PERSON}?personId=$id") },
                    onImportGallery = { id -> navController.navigate("${MainRoutes.GALLERY_IMPORT}/$id") },
                )
            }
            composable("${MainRoutes.GALLERY_IMPORT}/{personId}") {
                com.guardia.app.ui.screens.people.GalleryImportScreen(onBack = { navController.popBackStack() })
            }
            composable(BottomDestination.ACTIVITY.route) {
                ActivityScreen(
                    onOpenIntruders = { navController.navigate(MainRoutes.INTRUDERS) },
                    onOpenStats = { navController.navigate(MainRoutes.STATS) },
                )
            }
            composable(MainRoutes.INTRUDERS) {
                IntrudersScreen(onBack = { navController.popBackStack() })
            }
            composable(MainRoutes.STATS) {
                com.guardia.app.ui.screens.stats.StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(BottomDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenCategory = { key ->
                        when (key) {
                            "people" -> navController.navigate(BottomDestination.PEOPLE.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            "blocked" -> navController.navigate(MainRoutes.BLOCKED_PEOPLE)
                            else -> navController.navigate("${MainRoutes.SETTINGS_DETAIL}/$key")
                        }
                    },
                )
            }
            composable(
                route = "${MainRoutes.ADD_PERSON}?personId={personId}",
                arguments = listOf(navArgument("personId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) { entry ->
                AddPersonScreen(
                    onDone = { navController.popBackStack() },
                    personId = entry.arguments?.getString("personId"),
                )
            }
            composable("${MainRoutes.SETTINGS_DETAIL}/{key}") { entry ->
                SettingsDetailScreen(
                    categoryKey = entry.arguments?.getString("key") ?: "",
                    onBack = { navController.popBackStack() },
                    onUpgrade = { navController.navigate(MainRoutes.PAYWALL) },
                )
            }
            composable(MainRoutes.ADD_BLOCKED) {
                com.guardia.app.ui.screens.people.AddBlockedPersonScreen(onDone = { navController.popBackStack() })
            }
            composable(MainRoutes.PAYWALL) {
                com.guardia.app.ui.screens.paywall.PaywallScreen(onBack = { navController.popBackStack() })
            }
        }

        if (showBottomBar) {
            FloatingBottomBar(
                navController = navController,
                backStackEntry = backStackEntry,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun FloatingBottomBar(
    navController: NavController,
    backStackEntry: androidx.navigation.NavBackStackEntry?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomDestination.entries.forEach { dest ->
                    val selected = backStackEntry?.destination?.hierarchy?.any { it.route == dest.route } == true
                    FloatingNavItem(
                        destination = dest,
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    destination: BottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillColor by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "pill",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = androidx.compose.animation.core.tween(260),
        label = "navContent",
    )
    val pillWidth by androidx.compose.animation.core.animateDpAsState(
        if (selected) 60.dp else 48.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pillWidth",
    )
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "iconScale",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(width = pillWidth, height = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(pillColor)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            destination.icon,
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.scale(iconScale),
        )
    }
}
