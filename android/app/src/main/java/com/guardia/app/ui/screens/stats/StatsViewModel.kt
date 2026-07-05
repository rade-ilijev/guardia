package com.guardia.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.data.EventsRepository
import com.guardia.app.data.PeopleRepository
import com.guardia.app.domain.model.GuardEvent
import com.guardia.app.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class DayBar(val label: String, val value: Int)

data class StatsUi(
    val totalEvents: Int = 0,
    val totalIntruders: Int = 0,
    val totalRecognitions: Int = 0,
    val intrudersPerDay: List<DayBar> = emptyList(),
    val topPeople: List<Person> = emptyList(),
    val mostActiveDay: String? = null,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    events: EventsRepository,
    people: PeopleRepository,
) : ViewModel() {

    val ui: StateFlow<StatsUi> = combine(events.events, people.people) { evts, ppl ->
        val intruderTypes = setOf(
            GuardEvent.Type.INTRUDER_LOCK,
            GuardEvent.Type.UNKNOWN_FACE,
            GuardEvent.Type.WRONG_UNLOCK,
        )
        val intruders = evts.filter { it.type in intruderTypes }
        val days = lastSevenDays()
        val bars = days.map { (label, start, end) ->
            DayBar(label, intruders.count { it.timestamp in start until end })
        }
        StatsUi(
            totalEvents = evts.size,
            totalIntruders = intruders.size,
            totalRecognitions = ppl.sumOf { it.recognitionCount },
            intrudersPerDay = bars,
            topPeople = ppl.sortedByDescending { it.recognitionCount }.take(5),
            mostActiveDay = bars.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.label,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUi())

    /** Returns the last 7 day buckets as (short weekday label, startMs, endMs), oldest first. */
    private fun lastSevenDays(): List<Triple<String, Long, Long>> {
        val labels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayMs = 24 * 60 * 60 * 1000L
        val todayStart = cal.timeInMillis
        return (6 downTo 0).map { offset ->
            val start = todayStart - offset * dayMs
            val c = Calendar.getInstance().apply { timeInMillis = start }
            val label = if (offset == 0) "Today" else labels[c.get(Calendar.DAY_OF_WEEK) - 1]
            Triple(label, start, start + dayMs)
        }
    }
}
