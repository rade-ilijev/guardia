package com.guardia.app.data

import com.guardia.app.data.db.EventDao
import com.guardia.app.data.db.EventEntity
import com.guardia.app.domain.model.GuardEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventsRepository @Inject constructor(
    private val dao: EventDao,
) {
    val events: Flow<List<GuardEvent>> = dao.observeEvents().map { list ->
        list.map { e ->
            GuardEvent(
                id = e.id,
                type = runCatching { GuardEvent.Type.valueOf(e.type) }.getOrDefault(GuardEvent.Type.INFO),
                message = e.message,
                timestamp = e.timestamp,
                photoPath = e.photoPath,
            )
        }
    }

    suspend fun log(type: GuardEvent.Type, message: String, photoPath: String? = null) {
        dao.insert(
            EventEntity(
                id = UUID.randomUUID().toString(),
                type = type.name,
                message = message,
                timestamp = System.currentTimeMillis(),
                photoPath = photoPath,
            )
        )
    }

    suspend fun clear() = dao.clear()
}
