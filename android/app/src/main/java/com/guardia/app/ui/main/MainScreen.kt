package com.guardia.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.guardia.app.ui.theme.Spacing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.guardia.app.ui.components.glow
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.guardia.app.ui.components.ShSeparator
import com.guardia.app.ui.screens.activity.ActivityScreen
import com.guardia.app.ui.screens.dashboard.DashboardScreen
import com.guardia.app.ui.screens.intruders.IntrudersScreen
import com.guardia.app.ui.screens.people.AddPersonScreen
import com.guardia.app.ui.screens.people.PeopleScreen
import com.guardia.app.ui.screens.people.PersonDetailScreen
import com.guardia.app.ui.screens.settings.SettingsDetailScreen
import com.guardia.app.ui.screens.settings.SettingsScreen
import com.guardia.app.ui.theme.Guardia
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private object MainRoutes {
    const val ADD_PERSON = "add_person"
    const val SETTINGS_DETAIL = "settings_detail"
    const val INTRUDERS = "intruders"
    const val PERSON = "person"
    const val STATS = "stats"
    const val ADD_BLOCKED = "add_blocked"
    const val BLOCKED_PEOPLE = "blocked_people"
    const val GALLERY_IMPORT = "gallery_import"
    const val GUEST_PASS = "guest_pass"
    const val APP_AUDIT = "app_audit"
    const val SECURITY_CENTER = "security_center"
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
                    onOpenPins = { navController.navigate("${MainRoutes.SETTINGS_DETAIL}/pins") },
                    onOpenSecurity = { navController.navigate(MainRoutes.SECURITY_CENTER) },
                )
            }
            composable(BottomDestination.PEOPLE.route) {
                PeopleScreen(
                    onAddPerson = { navController.navigate(MainRoutes.ADD_PERSON) },
                    onOpenBlocked = { navController.navigate(MainRoutes.BLOCKED_PEOPLE) },
                    onOpenPerson = { id -> navController.navigate("${MainRoutes.PERSON}/$id") },
                    onGuestPass = { navController.navigate(MainRoutes.GUEST_PASS) },
                )
            }
            composable(MainRoutes.GUEST_PASS) {
                com.guardia.app.ui.screens.people.GuestPassScreen(onDone = { navController.popBackStack() })
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
                            "appaudit" -> navController.navigate(MainRoutes.APP_AUDIT)
                            "security" -> navController.navigate(MainRoutes.SECURITY_CENTER)
                            else -> navController.navigate("${MainRoutes.SETTINGS_DETAIL}/$key")
                        }
                    },
                )
            }
            composable(MainRoutes.APP_AUDIT) {
                com.guardia.app.ui.screens.security.AppAuditScreen(onBack = { navController.popBackStack() })
            }
            composable(MainRoutes.SECURITY_CENTER) {
                com.guardia.app.ui.screens.security.SecurityCenterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenScan = { navController.navigate("${MainRoutes.SETTINGS_DETAIL}/scanner") },
                    onOpenAppAudit = { navController.navigate(MainRoutes.APP_AUDIT) },
                    onOpenCameraMic = { navController.navigate("${MainRoutes.SETTINGS_DETAIL}/cameramic") },
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
                    // No paywall while every feature is unlocked; the upsell paths are dead code
                    // behind `EntitlementManager.allFeaturesUnlocked` and never render.
                    onUpgrade = {},
                )
            }
            composable(MainRoutes.ADD_BLOCKED) {
                com.guardia.app.ui.screens.people.AddBlockedPersonScreen(onDone = { navController.popBackStack() })
            }
        }

        if (showBottomBar) {
            BottomNavBar(
                navController = navController,
                backStackEntry = backStackEntry,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Bottom navigation — a floating glass bar.
 *
 * The previous version was a full-width opaque strip with a hairline rule above it, which had the
 * same problem as the old header: it sealed off the bottom of the screen and stopped the ambient
 * backdrop dead at a hard edge, so every page ended in a slab of chrome.
 *
 * This one is inset from all three edges and translucent, so the aurora keeps moving underneath it
 * and the page reads as continuing behind the navigation rather than stopping at it. Depth comes
 * from the same trick the cards use — a lit top edge fading into the ordinary hairline — plus a
 * soft shadow that shows in light mode and is invisible in dark, which is where it should be.
 *
 * The selected tab is marked by a brand-tinted capsule with a matching halo. That is the one piece
 * of chrome in the app allowed to carry the brand colour, because "where am I" is worth answering
 * before the label is read.
 */
@Composable
private fun BottomNavBar(
    navController: NavController,
    backStackEntry: androidx.navigation.NavBackStackEntry?,
    modifier: Modifier = Modifier,
) {
    val c = Guardia.colors
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)
    val edge = remember(c.borderHighlight, c.border) {
        Brush.verticalGradient(listOf(c.borderHighlight, c.border, c.border))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, shape, clip = false)
                .clip(shape)
                .background(c.card.copy(alpha = 0.86f), shape)
                .border(BorderStroke(1.dp, edge), shape)
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomDestination.entries.forEach { dest ->
                val selected = backStackEntry?.destination?.hierarchy?.any { it.route == dest.route } == true
                BottomNavItem(
                    destination = dest,
                    selected = selected,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // A light tick on a real tab change — the capsule sliding over is the
                        // visual, this is the tactile half of the same feedback.
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

@Composable
private fun BottomNavItem(
    destination: BottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Guardia.colors
    val contentColor by animateColorAsState(
        targetValue = if (selected) c.brand else c.mutedForeground,
        animationSpec = tween(200),
        label = "navContent",
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
        label = "navPill",
    )
    // The capsule springs a little wider than its resting size on selection, which is what makes
    // the tab feel picked up rather than merely recoloured.
    val pillWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) 44.dp else 30.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "navPillWidth",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .graphicsLayer { alpha = pillAlpha }
                    .then(
                        if (selected) Modifier.glow(c.brand, CircleShape, radius = 14.dp, alpha = 0.55f)
                        else Modifier,
                    )
                    .size(width = pillWidth, height = 28.dp)
                    .clip(CircleShape)
                    .background(c.brand.copy(alpha = 0.18f))
                    .border(BorderStroke(1.dp, c.brand.copy(alpha = 0.34f)), CircleShape),
            )
            Icon(
                destination.icon,
                contentDescription = destination.label,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            destination.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = contentColor,
            maxLines = 1,
        )
    }
}
