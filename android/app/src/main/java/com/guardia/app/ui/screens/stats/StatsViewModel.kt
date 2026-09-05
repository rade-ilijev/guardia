package com.guardia.app.ui.screens.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.GuardEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One labelled bucket in a chart. */
data class DayBar(val label: String, val value: Int)

/** How well guarding knows one enrolled person. */
@Immutable
data class PersonQuality(
    val name: String,
    val recognitions: Int,
    /** Average match confidence, 0..1; 0 when this person has never been recognised. */
    val confidence: Float,
    val sampleCount: Int,
) {
    /**
     * Below this, matches are landing close enough to the threshold that ordinary variation —
     * glasses, a dim room, a different angle — will start pushing them under it. More samples is
     * the fix, and it is worth saying so before the user is locked out by their own phone.
     */
    val weak: Boolean get() = recognitions > 0 && confidence < 0.72f
}

/**
 * Marked `@Immutable` for Compose: it holds `List`s, and Compose treats every `List` as unstable
 * because the interface allows a mutable implementation. Without the annotation, any composable
 * reading this state is re-run on *every* recomposition of its parent, even when the state itself
 * has not changed. The contents genuinely are never mutated after construction, so the promise is
 * safe to make — and it is what lets Compose skip the subtree.
 */
@Immutable
data class StatsUi(
    val intruderTotal: Int = 0,
    val intruders7d: Int = 0,
    val intrudersPrev7d: Int = 0,
    val totalRecognitions: Int = 0,
    val falseLocks: Int = 0,
    val unknownFaceLocks: Int = 0,
    val perDay: List<DayBar> = emptyList(),
    val perWindow: List<DayBar> = emptyList(),
    val busiestWindow: String? = null,
    val people: List<PersonQuality> = emptyList(),
    /** Timestamp of the oldest event these numbers were drawn from, or 0 when there are none. */
    val since: Long = 0L,
) {
    /**
     * Share of unknown-face locks the owner later said were actually them.
     *
     * Coerced to 1f rather than trusted: the activity log is capped, so an old UNKNOWN_FACE can age
     * out while the FALSE_LOCK that answered it is still in the window, which would otherwise
     * produce a rate above 100%.
     */
    val falseLockRate: Float
        get() = if (unknownFaceLocks == 0) 0f else (falseLocks.toFloat() / unknownFaceLocks).coerceIn(0f, 1f)

    val hasIntruderHistory: Boolean get() = intruderTotal > 0
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    events: EventsRepository,
    people: PeopleRepository,
) : ViewModel() {

    val ui: StateFlow<StatsUi> = combine(events.events, people.people) { evts, ppl ->
        val intruders = evts.filter { it.type in INTRUDER_TYPES }
        val days = lastSevenDays()
        val perDay = days.map { (label, start, end) ->
            DayBar(label, intruders.count { it.timestamp in start until end })
        }
        val weekMs = 7 * DAY_MS
        val now = System.currentTimeMillis()
        val windows = hourWindows(intruders)

        StatsUi(
            intruderTotal = intruders.size,
            intruders7d = intruders.count { it.timestamp >= now - weekMs },
            intrudersPrev7d = intruders.count { it.timestamp in (now - 2 * weekMs) until (now - weekMs) },
            totalRecognitions = ppl.sumOf { it.recognitionCount },
            falseLocks = evts.count { it.type == GuardEvent.Type.FALSE_LOCK },
            unknownFaceLocks = evts.count { it.type == GuardEvent.Type.UNKNOWN_FACE },
            perDay = perDay,
            perWindow = windows,
            busiestWindow = busiestLabel(windows),
            people = ppl
                .filter { !it.blocked }
                .sortedByDescending { it.recognitionCount }
                .map { PersonQuality(it.name, it.recognitionCount, it.avgConfidence, it.sampleCount) },
            since = evts.minOfOrNull { it.timestamp } ?: 0L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUi())

    /**
     * Intruder events bucketed into eight three-hour windows of the day.
     *
     * Three hours rather than one: the activity log holds a few hundred events, and spread across
     * 24 buckets that is noise wearing the shape of a finding. Eight buckets also fit across a
     * phone as readable columns.
     */
    private fun hourWindows(intruders: List<GuardEvent>): List<DayBar> {
        val counts = IntArray(WINDOWS)
        val cal = Calendar.getInstance()
        intruders.forEach { e ->
            cal.timeInMillis = e.timestamp
            counts[cal.get(Calendar.HOUR_OF_DAY) / WINDOW_HOURS]++
        }
        return List(WINDOWS) { i -> DayBar("%02d".format(i * WINDOW_HOURS), counts[i]) }
    }

    /** Returns the last 7 day buckets as (short weekday label, startMs, endMs), oldest first. */
    private fun lastSevenDays(): List<Triple<String, Long, Long>> {
        val labels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        return (6 downTo 0).map { offset ->
            val start = todayStart - offset * DAY_MS
            val c = Calendar.getInstance().apply { timeInMillis = start }
            val label = if (offset == 0) "Today" else labels[c.get(Calendar.DAY_OF_WEEK) - 1]
            Triple(label, start, start + DAY_MS)
        }
    }

    companion object {
        /**
         * Names the busiest window, or null when there is nothing worth naming.
         *
         * The guard against a marginal peak is the point: with a handful of events, one bucket
         * always happens to be the tallest, and reporting that as "most attempts between 03:00 and
         * 06:00" would be inventing a pattern out of noise. It has to lead the runner-up by at
         * least two events before it is called a finding.
         */
        internal fun busiestLabel(windows: List<DayBar>): String? {
            val peak = windows.maxByOrNull { it.value } ?: return null
            if (peak.value == 0) return null
            val runnerUp = windows.filter { it !== peak }.maxOfOrNull { it.value } ?: 0
            if (peak.value - runnerUp < 2) return null
            val startHour = windows.indexOf(peak) * WINDOW_HOURS
            return "%02d:00 - %02d:00".format(startHour, (startHour + WINDOW_HOURS) % 24)
        }

        // The companion is public only so [busiestLabel] can be reached from tests; everything
        // else in it stays private so that did not widen the class's surface.
        private val INTRUDER_TYPES = setOf(
            GuardEvent.Type.INTRUDER_LOCK,
            GuardEvent.Type.UNKNOWN_FACE,
            GuardEvent.Type.WRONG_UNLOCK,
        )
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val WINDOW_HOURS = 3
        private const val WINDOWS = 24 / WINDOW_HOURS
    }
}
