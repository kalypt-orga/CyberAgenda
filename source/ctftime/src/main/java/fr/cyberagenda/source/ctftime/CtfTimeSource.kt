package fr.cyberagenda.source.ctftime

import dagger.Binds
import fr.cyberagenda.core.network.mapEachSafely
import fr.cyberagenda.core.network.decodeEach
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.cyberagenda.core.data.EventSource
import fr.cyberagenda.core.data.InvalidSourceDataException
import fr.cyberagenda.core.model.Event
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.SourceMeta
import fr.cyberagenda.core.network.HttpFetcher
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CtfTimeSource @Inject constructor(
    private val fetcher: HttpFetcher,
    private val json: Json,
) : EventSource {

    override val meta = SourceMeta(
        id = ID,
        name = "CTFtime",
        description = "Compétitions CTF en ligne et CTF sur place en France, issues de l'agenda mondial CTFtime.",
        homepage = "https://ctftime.org",
        kinds = setOf(EventKind.CTF),
        verified = false,
        order = 1,
    )

    override suspend fun fetch(): List<Event> {
        val nowSec = System.currentTimeMillis() / 1000
        // Fenêtre : 3 mois passés (consultables sur la carte) → 3 mois à venir.
        val url = "https://ctftime.org/api/v1/events/?limit=$LIMIT&start=${nowSec - PAST_SEC}&finish=${nowSec + WINDOW_SEC}"
        val body = fetcher.getText(url)
        val dtos = try {
            json.decodeEach<CtfTimeEventDto>(body)
        } catch (e: SerializationException) {
            throw InvalidSourceDataException("Réponse CTFtime illisible", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidSourceDataException("Réponse CTFtime illisible", e)
        }
        return dtos.mapEachSafely { CtfTimeMapper.map(it, ID) }
    }

    companion object {
        const val ID = "ctftime"
        private const val LIMIT = 400
        private const val WINDOW_SEC = 90L * 24 * 60 * 60 // 90 jours
        private const val PAST_SEC = 90L * 24 * 60 * 60
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CtfTimeModule {
    @Binds
    @IntoSet
    abstract fun bindSource(source: CtfTimeSource): EventSource
}
