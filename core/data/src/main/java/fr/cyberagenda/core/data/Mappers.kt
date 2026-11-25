package fr.cyberagenda.core.data

import fr.cyberagenda.core.database.EventEntity
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind

internal fun Event.toEntity() = EventEntity(
    id = id, sourceId = sourceId, title = title, description = description,
    startsAt = startsAtEpochMs, endsAt = endsAtEpochMs, format = format.name, kind = kind.name,
    url = url, organizer = organizer, location = location, latitude = latitude,
    longitude = longitude, beginnerFriendly = beginnerFriendly, free = free, price = price,
)

internal fun EventEntity.toModel() = Event(
    id = id, sourceId = sourceId, title = title, description = description,
    startsAtEpochMs = startsAt, endsAtEpochMs = endsAt,
    format = runCatching { EventFormat.valueOf(format) }.getOrDefault(EventFormat.ONLINE),
    kind = runCatching { EventKind.valueOf(kind) }.getOrDefault(EventKind.OTHER),
    url = url, organizer = organizer, location = location, latitude = latitude,
    longitude = longitude, beginnerFriendly = beginnerFriendly, free = free, price = price,
)
