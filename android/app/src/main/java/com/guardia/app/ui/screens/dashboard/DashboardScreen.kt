package com.guardia.app.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.guard.GuardState
import com.guardia.app.core.system.DeviceAdminManager
import androidx.compose.ui.text.style.TextOverflow
import com.guardia.app.domain.model.GuardEvent
import com.guardia.app.ui.components.animateEntrance
import com.guardia.app.ui.components.animatedCount
import com.guardia.app.ui.components.AlertVariant
import com.guardia.app.ui.components.BadgeVariant
import com.guardia.app.ui.components.ButtonSize
import com.guardia.app.ui.components.ButtonVariant
import com.guardia.app.ui.components.ShAlert
import com.guardia.app.ui.components.ShBadge
import com.guardia.app.ui.components.ShButton
import com.guardia.app.ui.components.ShCard
import com.guardia.app.ui.components.ShIconBox
import com.guardia.app.ui.components.ShProgress
import com.guardia.app.ui.components.ShSectionLabel
import com.guardia.app.ui.components.ShStatCard
import com.guardia.app.ui.components.ShSwitch
import com.guardia.app.ui.components.StatusOrb
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.MonoCaption
import com.guardia.app.ui.theme.Spacing
import com.guardia.app.ui.components.rememberAccessibilityOptIn
import com.guardia.app.ui.components.GlassIconButton
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Key

/**
 * Home.
 *
 * Laid out as a shadcn dashboard: a quiet header, one hero card carrying the single decision the
 * user came here to make, then a column of same-weight cards. Nothing is emphasised by chrome —
 * the hero is bigger, everything else is a plain bordered card, and the only color on the page is
 * the status hue, which is why "protected" is readable from across the room.
 *
 * Built on a LazyColumn rather than a scrolling Column so the battery receiver, the admin probe and
 * the setup checklist are only composed while actually on screen.
 */
@Composable
fun DashboardScreen(
    onLock: () -> Unit,
    onOpenPeople: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenPins: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.guardState.collectAsStateWithLifecycle()
    val peopleCount by viewModel.peopleCount.collectAsStateWithLifecycle()
    val intruderCount by viewModel.intruderCount.collectAsStateWithLifecycle()
    val lastIntruderAt by viewModel.lastIntruderAt.collectAsStateWithLifecycle()
    val intruderAlert by viewModel.intruderAlert.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    val testMode by viewModel.testMode.collectAsStateWithLifecycle()
    val responsiveness by viewModel.responsiveness.collectAsStateWithLifecycle()
    val appActivity by viewModel.appActivity.collectAsStateWithLifecycle()
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val setupDismissed by viewModel.setupDismissed.collectAsStateWithLifecycle()
    val disclosureAccepted by viewModel.disclosureAccepted.collectAsStateWithLifecycle()
    val relaxedOnWifi by viewModel.relaxedOnWifi.collectAsStateWithLifecycle()
    val integrityWarning by viewModel.integrityWarning.collectAsStateWithLifecycle()
    val needsRecoveryCode by viewModel.needsRecoveryCode.collectAsStateWithLifecycle()

    val protectedNow = state == GuardState.PROTECTED
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val c = Guardia.colors
    var showDisclosure by remember { mutableStateOf(false) }

    if (showDisclosure) {
        BackgroundCameraDisclosureDialog(
            onAgree = {
                showDisclosure = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.acceptDisclosureAndStart()
            },
            onDismiss = { showDisclosure = false },
        )
    }

    // Items composed within this window of arriving on screen animate in; anything composed later
    // (i.e. scrolled into view) appears immediately, so scrolling never re-plays the entrance.
    val enteredAt = remember { android.os.SystemClock.uptimeMillis() }
    fun arriving() = android.os.SystemClock.uptimeMillis() - enteredAt < 700L

    // The status-bar inset is applied as *content* padding rather than to the list itself, so the
    // page scrolls up underneath the status bar instead of stopping short of it — and the scrim
    // below keeps the clock legible while it does. Every other screen behaves this way through
    // GuardiaScaffold; the dashboard builds its own shell, so it does the same thing by hand.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topScrim = remember(c.background) {
        Brush.verticalGradient(listOf(c.background, c.background.copy(alpha = 0f)))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The hero gets roughly a third of the screen and the rest of the page gets the remainder.
        // Derived from the real height rather than fixed, because "a third" on a compact phone and
        // on a tablet are very different numbers — and clamped, because a third of a very tall
        // screen would go back to being the wall of gauge this replaced.
        val heroBudget = (maxHeight * 0.30f).coerceIn(168.dp, 236.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = topInset + Spacing.md,
                bottom = Spacing.bottomBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "header") {
                DashboardHeader(onLock, Modifier.animateEntrance(0, arriving()))
            }

            item(key = "hero") {
                HeroStatus(
                    modifier = Modifier.animateEntrance(1, arriving()),
                    heightBudget = heroBudget,
                    state = state,
                    protectedNow = protectedNow,
                    relaxed = relaxedOnWifi,
                    checksToday = appActivity.checks24h,
                    onToggle = {
                        // Google Play requires a prominent, in-context disclosure before background
                        // camera collection begins. Show it the first time guarding is turned on.
                        if (!protectedNow && !disclosureAccepted) {
                            showDisclosure = true
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleGuarding()
                        }
                    },
                )
            }

            // Anything caught in the last day and not yet acknowledged goes above everything else.
            // The stat card turning red is not enough: "someone tried to use your phone" is the
            // event the whole app exists for, and it belongs at the top with the evidence one tap
            // away — but it also has to be dismissible, or it stops being read.
            intruderAlert?.let { alert ->
                item(key = "recent-intruder") {
                    val ago = DateUtils.getRelativeTimeSpanString(
                        alert.at,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString().lowercase(java.util.Locale.getDefault())
                    ShAlert(
                        title = if (alert.count == 1) "Intruder detected"
                        else "${alert.count} intruder events",
                        description = if (alert.count == 1) {
                            "Someone who isn't enrolled tried to use this phone $ago."
                        } else {
                            "Someone who isn't enrolled tried to use this phone. Most recent: $ago."
                        },
                        modifier = Modifier.animateEntrance(2, arriving()),
                        variant = AlertVariant.Destructive,
                        icon = Icons.Filled.Warning,
                        onDismiss = { viewModel.acknowledgeIntruders(alert.at) },
                        action = {
                            ShButton(
                                text = "See what happened",
                                onClick = {
                                    // Looking at the evidence is the strongest acknowledgement there
                                    // is; making them dismiss the banner afterwards as well would
                                    // just be nagging.
                                    viewModel.acknowledgeIntruders(alert.at)
                                    onOpenActivity()
                                },
                                variant = ButtonVariant.Destructive,
                                size = ButtonSize.Sm,
                            )
                        },
                    )
                }
            }

        // One forgotten PIN away from a reinstall. Worth interrupting for, once.
        if (needsRecoveryCode) {
            item(key = "recovery") {
                ShAlert(
                    title = "Set up a recovery code",
                    description = "Without one, forgetting your PIN means reinstalling Guardia and " +
                        "losing every enrolled face and saved photo. It takes a few seconds.",
                    modifier = Modifier.animateEntrance(2, arriving()),
                    variant = AlertVariant.Warning,
                    icon = Icons.Filled.Key,
                    action = {
                        ShButton(
                            text = "Create one",
                            onClick = onOpenPins,
                            size = ButtonSize.Sm,
                        )
                    },
                )
            }
        }

        integrityWarning?.let { warning ->
                item(key = "integrity") {
                    ShAlert(
                        title = "Integrity check failed",
                        description = warning,
                        modifier = Modifier.animateEntrance(2, arriving()),
                        variant = AlertVariant.Destructive,
                        icon = Icons.Filled.GppBad,
                    )
                }
            }

            item(key = "falselock") { FalseLockCard(viewModel, Modifier.animateEntrance(2, arriving())) }

            if (!setupDismissed) {
                item(key = "setup") {
                    Box(Modifier.animateEntrance(3, arriving())) {
                        SetupChecklistCard(
                            pinSet = pinSet,
                            peopleEnrolled = peopleCount > 0,
                            onAddPerson = onOpenPeople,
                            onDismiss = viewModel::dismissSetup,
                        )
                    }
                }
            }

            item(key = "admin") { Box(Modifier.animateEntrance(4, arriving())) { DeviceAdminCard() } }

            if (recentEvents.isNotEmpty()) {
                item(key = "recent-label") {
                    Spacer(Modifier.height(Spacing.md))
                    ShSectionLabel(
                        "Recent activity",
                        Modifier.animateEntrance(5, arriving()),
                        trailing = {
                            ShButton(
                                text = "See all",
                                onClick = onOpenActivity,
                                variant = ButtonVariant.Ghost,
                                size = ButtonSize.Sm,
                                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            )
                        },
                    )
                }
                item(key = "recent") {
                    RecentActivityCard(
                        events = recentEvents,
                        onOpenActivity = onOpenActivity,
                        modifier = Modifier.animateEntrance(6, arriving()),
                    )
                }
            }

            item(key = "overview-label") {
                Spacer(Modifier.height(Spacing.md))
                ShSectionLabel("Overview", Modifier.animateEntrance(7, arriving()))
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.animateEntrance(8, arriving()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ShStatCard(
                        label = "Enrolled people",
                        // Counts climb to their value rather than appearing, so a change is visible
                        // without needing a badge to announce it.
                        value = animatedCount(peopleCount).toString(),
                        icon = Icons.Filled.People,
                        accent = c.brand,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPeople,
                    )
                    ShStatCard(
                        label = "Intruder events",
                        value = animatedCount(intruderCount).toString(),
                        icon = Icons.Filled.Warning,
                        accent = if (intruderCount > 0) c.destructive else c.violet,
                        modifier = Modifier.weight(1f),
                        caption = lastIntruderAt?.let {
                            DateUtils.getRelativeTimeSpanString(
                                it,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString()
                        },
                        captionColor = if (intruderCount > 0) c.destructiveForeground else null,
                        onClick = onOpenActivity,
                    )
                }
            }

            item(key = "battery") {
                BatteryCard(protectedNow, responsiveness, Modifier.animateEntrance(9, arriving()))
            }
    
            item(key = "test-label") {
                Spacer(Modifier.height(Spacing.md))
                ShSectionLabel("Diagnostics", Modifier.animateEntrance(9, arriving()))
            }
            item(key = "testmode") {
                TestModeCard(
                    modifier = Modifier.animateEntrance(10, arriving()),
                    enabled = testMode,
                    onChange = viewModel::setTestMode,
                    onTestLock = {
                        if (!viewModel.testDeviceLock()) {
                            android.widget.Toast.makeText(
                                context,
                                "Can't lock yet — enable Device Admin (or the App-detection service) first.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + Spacing.sm)
                .align(Alignment.TopCenter)
                .background(topScrim),
        )
    }
}

/**
 * Wordmark, and the one action that belongs in the shell.
 *
 * This used to carry a "Live / Off" pill, which said the same thing as the word "Protected" set at
 * display size forty pixels below it — two status indicators visible at once, neither earning its
 * place. The status belongs to the hero, so the header gives that space to locking the app
 * instead: it was previously a full-width outline button stranded at the very bottom of the scroll,
 * which is the worst possible position for a security control you might need in a hurry.
 */
@Composable
private fun DashboardHeader(onLock: () -> Unit, modifier: Modifier = Modifier) {
    val c = Guardia.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.guardia.app.ui.components.GuardiaLogo(size = 28.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "Guardia",
            style = MaterialTheme.typography.headlineSmall,
            color = c.foreground,
        )
        Spacer(Modifier.weight(1f))
        GlassIconButton(
            icon = Icons.Filled.Lock,
            onClick = onLock,
            contentDescription = "Lock Guardia",
        )
    }
}

/**
 * The hero: status gauge, state name, one line of explanation, and the primary action.
 *
 * Deliberately *not* a card. This is the one thing on the page the user came for, and putting a
 * border around it made it a peer of the cards below. Sitting directly on the backdrop — with a
 * soft brand glow bleeding out behind the gauge and nothing else competing for the space — is what
 * makes it read as the subject rather than the first item in a list.
 *
 * It used to be a centred column with a 184dp gauge, the state word at display size and generous
 * air around both, which came to something like two thirds of a phone screen. That is a fine
 * landing screen and a poor home screen: everything the app had to *tell* you sat below the fold.
 * Laid out on one line instead, the gauge and the words take about a third, and the reader gets to
 * the alerts and the log without scrolling. Nothing was cut — the same status, sentence, check
 * count and action are all still here, just read left to right rather than top to bottom.
 *
 * [heightBudget] is that third, measured from the real screen; the gauge is sized from it so the
 * block fills its share on a tall phone and shrinks rather than overflowing on a short one.
 */
@Composable
private fun HeroStatus(
    state: GuardState,
    protectedNow: Boolean,
    heightBudget: Dp,
    relaxed: Boolean = false,
    checksToday: Int = 0,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val c = Guardia.colors
    val (label, sub, color) = when (state) {
        GuardState.PROTECTED ->
            if (relaxed) Triple("Protected", "Relaxed — on trusted Wi-Fi, checks paused", c.brand)
            else Triple("Protected", "Watching who uses this device", c.brand)
        GuardState.PAUSED -> Triple("Paused", "Guarding temporarily paused", c.warning)
        GuardState.NEEDS_ATTENTION -> Triple("Needs attention", "Check permissions to continue", c.destructive)
        GuardState.STOPPED -> Triple("Off", "Guarding is not active", c.mutedForeground)
    }
    val animColor by animateColorAsState(color, label = "statusColor")
    val heroIcon = when (state) {
        GuardState.PROTECTED -> Icons.Filled.VerifiedUser
        GuardState.NEEDS_ATTENTION -> Icons.Filled.GppBad
        else -> Icons.Filled.Shield
    }
    // How much light the state throws onto the page behind it. Off is nearly nothing; running is a
    // wash you can see from across the room.
    val glowStrength by animateFloatAsState(
        targetValue = if (protectedNow) 0.30f else 0.08f,
        animationSpec = tween(620),
        label = "heroGlow",
    )
    // The gauge takes a little over half the budget; the button and the gaps take the rest.
    val orbSize = (heightBudget * 0.58f).coerceIn(84.dp, 132.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The glow is drawn on the page, not on a surface — no card, no edge, nothing to clip
            // it. It is what replaces the border as the thing separating the hero from the page.
            //
            // It is painted *by* the gauge's own box rather than by a larger sibling behind it.
            // A sibling would have to be bigger than the gauge to spill, and a Box takes the size
            // of its largest child — so the glow would have claimed a third of the row's width and
            // squeezed the status text into whatever was left. Compose does not clip draw commands
            // to layout bounds, so a circle drawn wider than its node bleeds out for free.
            Box(
                modifier = Modifier
                    .size(orbSize)
                    .drawBehind {
                        val radius = size.minDimension * 0.95f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(animColor.copy(alpha = glowStrength), Color.Transparent),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = radius,
                            ),
                            radius = radius,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                StatusOrb(active = protectedNow, icon = heroIcon, accent = animColor, size = orbSize)
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(
                // Guard state is the one thing in the app worth interrupting for: a user who
                // cannot see the gauge stop still needs to hear that it stopped. Polite, so it
                // waits for the current utterance rather than talking over it. The label and its
                // explanatory line are merged so the announcement is a sentence, not two.
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.foreground,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground,
                )
                // The one number worth keeping from the old battery card: how many times the guard
                // has actually looked today. It is a count, not an estimate, and it is the honest
                // answer to "is this thing doing anything?".
                if (protectedNow && checksToday > 0) {
                    Spacer(Modifier.height(Spacing.sm))
                    ShBadge(
                        text = "${animatedCount(checksToday)} checks today",
                        variant = BadgeVariant.Outline,
                        leadingIcon = Icons.Filled.Visibility,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        if (protectedNow) {
            // Stopping protection is never the primary action, so it is an outline button.
            ShButton(
                text = "Stop guarding",
                onClick = onToggle,
                variant = ButtonVariant.Outline,
                size = ButtonSize.Lg,
                leadingIcon = Icons.Filled.PauseCircle,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ShButton(
                text = "Start guarding",
                onClick = onToggle,
                variant = ButtonVariant.Default,
                size = ButtonSize.Lg,
                leadingIcon = Icons.Filled.Shield,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The last few things that happened, as a glance rather than a log.
 *
 * The home screen previously said how *many* intruder events there had been and nothing at all
 * about what any of them were; the answer to "what has my phone been doing" lived one tab away.
 * Three rows is the whole budget on purpose — enough to see that something happened and go look,
 * not so much that the Activity screen has been reproduced here and the user has two places to
 * read the same thing.
 */
@Composable
private fun RecentActivityCard(
    events: List<GuardEvent>,
    onOpenActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Guardia.colors
    ShCard(modifier = modifier.fillMaxWidth(), onClick = onOpenActivity) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
            events.forEach { event ->
                val alert = event.type == GuardEvent.Type.INTRUDER_LOCK ||
                    event.type == GuardEvent.Type.UNKNOWN_FACE ||
                    event.type == GuardEvent.Type.WRONG_UNLOCK
                val tint = when {
                    alert -> c.destructive
                    event.type == GuardEvent.Type.GUARDING_STARTED -> c.success
                    else -> c.mutedForeground
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.card, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShIconBox(
                        when {
                            alert -> Icons.Filled.Warning
                            event.type == GuardEvent.Type.GUARDING_STARTED -> Icons.Filled.CheckCircle
                            event.type == GuardEvent.Type.ENROLLMENT -> Icons.Filled.People
                            else -> Icons.Filled.Info
                        },
                        tint = tint,
                        size = 28.dp,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        event.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (alert) c.foreground else c.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        DateUtils.getRelativeTimeSpanString(
                            event.timestamp,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString(),
                        style = MonoCaption,
                        color = c.mutedForeground,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * "Was that you?" — a recent unknown-face lock the owner can turn into a training sample. The
 * feedback loop that makes recognition improve exactly where it failed.
 */
@Composable
private fun FalseLockCard(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    val prompt by viewModel.falseLockPrompt.collectAsStateWithLifecycle()
    val owners by viewModel.owners.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Guardia.colors
    var pickOwner by remember { mutableStateOf(false) }
    val current = prompt ?: return

    val toast: (String) -> Unit = { msg ->
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    val learn: (String) -> Unit = { personId -> viewModel.confirmFalseLock(personId, toast) }
    val at = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(current.at))

    ShCard(modifier = modifier.fillMaxWidth(), borderColor = c.warning.copy(alpha = 0.4f)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShIconBox(Icons.Filled.Lock, tint = c.warning, size = 34.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    "Was that you?",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.foreground,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Guardia locked this phone at $at because it didn't recognize the face. " +
                    "If that was you, teach it this moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.mutedForeground,
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ShButton(
                    text = "It was an intruder",
                    onClick = viewModel::dismissFalseLock,
                    variant = ButtonVariant.Outline,
                    size = ButtonSize.Sm,
                    modifier = Modifier.weight(1f),
                )
                ShButton(
                    text = "It was me",
                    onClick = {
                        val single = owners.singleOrNull()
                        if (single != null) learn(single.id) else pickOwner = true
                    },
                    enabled = owners.isNotEmpty(),
                    size = ButtonSize.Sm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (pickOwner) {
        AlertDialog(
            onDismissRequest = { pickOwner = false },
            containerColor = c.popover,
            title = { Text("Whose face was it?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    owners.forEach { person ->
                        ShButton(
                            text = person.name,
                            onClick = { pickOwner = false; learn(person.id) },
                            variant = ButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                ShButton("Cancel", onClick = { pickOwner = false }, variant = ButtonVariant.Ghost)
            },
        )
    }
}

/** Onboarding progress as a shadcn checklist card: a Progress bar and four tappable rows. */
@Composable
private fun SetupChecklistCard(
    pinSet: Boolean,
    peopleEnrolled: Boolean,
    onAddPerson: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val c = Guardia.colors
    var adminActive by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }
    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    // Play's AccessibilityService policy requires a prominent disclosure before the user is sent to
    // enable the service; rememberAccessibilityOptIn shows it.
    val openAccessibility = rememberAccessibilityOptIn()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adminActive = DeviceAdminManager.isAdminActive(context)
                accessibilityOn = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val steps = listOf(
        SetupStep("Set your PIN", pinSet) {
            android.widget.Toast.makeText(context, "Set this in Settings > PINs", android.widget.Toast.LENGTH_SHORT).show()
        },
        SetupStep("Enroll your face", peopleEnrolled, onAddPerson),
        SetupStep("Enable device locking", adminActive) {
            runCatching { context.startActivity(DeviceAdminManager.enableIntent(context)) }
        },
        SetupStep("Enable App Lock service", accessibilityOn, openAccessibility),
    )
    val done = steps.count { it.complete }
    if (done == steps.size) return

    ShCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Get protected", style = MaterialTheme.typography.titleLarge, color = c.foreground)
                    Text(
                        "Finish these to switch guarding on",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.mutedForeground,
                    )
                }
                ShBadge("$done/${steps.size}", variant = BadgeVariant.Secondary, mono = true)
            }
            Spacer(Modifier.height(Spacing.lg))
            ShProgress(progress = done.toFloat() / steps.size, height = 6.dp)
            Spacer(Modifier.height(Spacing.sm))
            steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(com.guardia.app.ui.theme.Radius.md))
                        .then(
                            if (!step.complete)
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = step.action,
                                )
                            else Modifier,
                        )
                        .padding(vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (step.complete) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (step.complete) c.success else c.mutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.complete) c.mutedForeground else c.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    if (!step.complete) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.mutedForeground,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            ShButton(
                text = "Dismiss",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Sm,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private data class SetupStep(val label: String, val complete: Boolean, val action: () -> Unit)

private fun isAccessibilityEnabled(context: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return flat.contains("${context.packageName}/")
}

/** Blocking prerequisite — a destructive alert with its own action, not a red card. */
@Composable
private fun DeviceAdminCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = DeviceAdminManager.isAdminActive(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active = DeviceAdminManager.isAdminActive(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (active) return

    ShAlert(
        title = "Finish setup",
        description = "Guardia needs Device Admin permission to lock the screen when an unauthorized " +
            "person is detected. Without it, guarding can still detect and capture, but cannot lock.",
        variant = AlertVariant.Destructive,
        icon = Icons.Filled.AdminPanelSettings,
        action = {
            ShButton(
                text = "Enable device locking",
                onClick = { launcher.launch(DeviceAdminManager.enableIntent(context)) },
                variant = ButtonVariant.Destructive,
                size = ButtonSize.Sm,
            )
        },
    )
}

/**
 * Device battery, and what the current guard profile costs it — on one line.
 *
 * This was a full card with a progress bar and a paragraph, which is a lot of surface for a number
 * the status bar is already showing two centimetres higher up. What it is actually here to answer
 * is narrower and worth keeping: an app that runs the camera in the background owes the user a
 * plain statement of what that costs. So the bar and the paragraph go, and the answer stays.
 */
@Composable
private fun BatteryCard(guarding: Boolean, responsiveness: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Guardia.colors
    var level by remember { mutableIntStateOf(batteryLevel(context)) }
    var charging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                if (raw >= 0) level = (raw * 100) / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val (impactLabel, impactDetail, impactVariant) = when {
        !guarding -> Triple("Idle", "Guarding is off — no extra battery use", BadgeVariant.Secondary)
        responsiveness == 0 -> Triple("Low impact", "Battery saver — fewer camera checks", BadgeVariant.Success)
        responsiveness == 2 -> Triple("Higher impact", "Max security — frequent camera checks", BadgeVariant.Warning)
        else -> Triple("Moderate", "Balanced — sensor-gated checks", BadgeVariant.Info)
    }
    val barColor = when {
        level <= 15 -> c.destructive
        level <= 35 -> c.warning
        else -> c.success
    }

    ShCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.card, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShIconBox(
                if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                tint = barColor,
                size = 32.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (charging) "Battery $level% · charging" else "Battery $level%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.foreground,
                )
                Text(impactDetail, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
            }
            Spacer(Modifier.width(Spacing.md))
            ShBadge(impactLabel, variant = impactVariant)
        }
    }
}

private fun batteryLevel(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
}

@Composable
private fun TestModeCard(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    onTestLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Guardia.colors
    // Test mode is useless without notifications — results arrive as heads-up messages. The
    // permission is normally asked during onboarding, but if it was denied or skipped there,
    // this switch was silently posting into the void. Ask again the moment testing turns on.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(
                context,
                "Notifications are blocked, so test results can't be shown. Allow them for Guardia in system settings.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    val toggleTestMode: (Boolean) -> Unit = { on ->
        onChange(on)
        if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    ShCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShIconBox(Icons.Filled.Science, size = 34.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text("Test mode", style = MaterialTheme.typography.titleMedium, color = c.foreground)
                    Text(
                        "Show recognition results as notifications instead of locking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.mutedForeground,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                ShSwitch(checked = enabled, onCheckedChange = toggleTestMode)
            }
            Spacer(Modifier.height(Spacing.lg))
            ShButton(
                text = "Test device lock now",
                onClick = onTestLock,
                variant = ButtonVariant.Outline,
                leadingIcon = Icons.Filled.Lock,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Locks this device right now so you can confirm locking works on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = c.mutedForeground,
            )
        }
    }
}

/**
 * Prominent disclosure shown before the first time guarding starts, satisfying Google Play's
 * requirement to disclose background sensor (camera) access in-context, separate from the privacy
 * policy, with an explicit accept/decline choice.
 */
@Composable
private fun BackgroundCameraDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    val c = Guardia.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popover,
        icon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = c.foreground) },
        title = { Text("Turn on guarding?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Guardia uses your front camera in the background to check whether the person using " +
                    "this device is you. Checks run only while the device is in use, a notification " +
                    "stays visible while guarding is on, and images are processed on your device and " +
                    "never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.mutedForeground,
            )
        },
        confirmButton = { ShButton("Turn on", onClick = onAgree) },
        dismissButton = { ShButton("Not now", onClick = onDismiss, variant = ButtonVariant.Ghost) },
    )
}
