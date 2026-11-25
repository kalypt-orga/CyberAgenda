package fr.cyberagenda.source.ctftime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Format brut de https://ctftime.org/api/v1/events/ (seuls les champs utiles sont déclarés). */
@Serializable
internal data class CtfTimeEventDto(
    val id: Long,
    val title: String,
    val start: String,
    val finish: String,
    val onsite: Boolean = false,
    val location: String = "",
    val url: String = "",
    @SerialName("ctftime_url") val ctftimeUrl: String = "",
    val format: String = "",
    val description: String = "",
    val restrictions: String = "",
    val organizers: List<CtfTimeOrganizerDto> = emptyList(),
)

@Serializable
internal data class CtfTimeOrganizerDto(val name: String = "")
