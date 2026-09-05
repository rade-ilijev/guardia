package com.guardia.app.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.guardia.app.R
import com.guardia.app.core.guard.GuardController
import com.guardia.app.core.guard.StopGuardActivity

/**
 * Home-screen widget: protection status, and one tap to arm or disarm.
 *
 * The asymmetry is deliberate and matches the Quick Settings tile. **Arming is one tap** — turning
 * protection *on* is never dangerous, and making it easy is the whole point of a widget. **Disarming
 * goes through [StopGuardActivity]**, which demands the real PIN, because a widget sits on a home
 * screen that an intruder holding the unlocked phone can already see; a one-tap "stop guarding"
 * button there would be a hole straight through the app.
 *
 * The widget owns no state. It renders [GuardController.state] at update time and is refreshed by
 * [refresh] whenever that state changes, so it can never disagree with the notification or the app.
 */
class GuardiaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (GuardController.isProtected) {
                // Stopping needs the PIN — hand off to the gate rather than acting here.
                context.startActivity(
                    Intent(context, StopGuardActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } else {
                GuardController.start(context)
            }
            refresh(context)
        }
    }

    // One companion, not two: the helpers below are private to it and only [refresh] is reachable
    // from outside. Kotlin allows exactly one companion per class, so the visibility has to be
    // expressed per member rather than on the object.
    companion object {
        private const val ACTION_TOGGLE = "com.guardia.app.widget.TOGGLE"

        /**
         * Redraws every placed widget. Called from [GuardController] on any state change, so the
         * widget stays in step with the app and the notification without polling.
         */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, GuardiaWidget::class.java))
                if (ids.isEmpty()) return
                ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val protectedNow = GuardController.isProtected
            return RemoteViews(context.packageName, R.layout.widget_guardia).apply {
                setTextViewText(
                    R.id.widget_status,
                    context.getString(
                        if (protectedNow) R.string.widget_status_protected else R.string.widget_status_off,
                    ),
                )
                setTextViewText(
                    R.id.widget_action,
                    context.getString(
                        if (protectedNow) R.string.widget_action_stop else R.string.widget_action_start,
                    ),
                )
                setImageViewResource(
                    R.id.widget_icon,
                    if (protectedNow) R.drawable.ic_stat_guardia else R.drawable.ic_guardia_logo,
                )
                setInt(
                    R.id.widget_root,
                    "setBackgroundResource",
                    if (protectedNow) R.drawable.widget_bg_active else R.drawable.widget_bg_idle,
                )
                setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            }
        }

        private fun togglePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, GuardiaWidget::class.java).setAction(ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
