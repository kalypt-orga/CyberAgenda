package fr.cyberagenda.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.cyberagenda.core.model.BeginnerFilter
import fr.cyberagenda.core.model.EventFilter
import fr.cyberagenda.core.model.EventFormat
import fr.cyberagenda.core.model.EventKind
import fr.cyberagenda.core.model.ReminderMode
import fr.cyberagenda.core.model.ThemeMode
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Partagé par les préférences du module (réglages, sources, historique de recherche).
internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Préférences locales (non sensibles → pas de chiffrement nécessaire).
 * Pour les sources, on mémorise celles qui sont DÉSACTIVÉES : une nouvelle source
 * ajoutée dans une mise à jour est ainsi active par défaut.
 */
@Singleton
class SourcePreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val disabledKey = stringSetPreferencesKey("disabled_sources")
    private val beginnerTipKey = booleanPreferencesKey("beginner_tip_dismissed")

    val disabledSourceIds: Flow<Set<String>> =
        context.dataStore.data.map { it[disabledKey].orEmpty() }

    val beginnerTipDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[beginnerTipKey] ?: false }

    suspend fun setEnabled(sourceId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[disabledKey].orEmpty()
            prefs[disabledKey] = if (enabled) current - sourceId else current + sourceId
        }
    }

    suspend fun dismissBeginnerTip() {
        context.dataStore.edit { it[beginnerTipKey] = true }
    }
}

/** Réglages de l'application (thème, rappels). */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val reminderKey = stringPreferencesKey("reminder")
    private val tourDoneKey = booleanPreferencesKey("tour_done")

    /** Visite guidée déjà vue (ou passée) : elle ne se relance plus d'elle-même. */
    val tourDone: Flow<Boolean> = context.dataStore.data.map { it[tourDoneKey] ?: false }
    suspend fun setTourDone() { context.dataStore.edit { it[tourDoneKey] = true } }

    val theme: Flow<ThemeMode> = context.dataStore.data.map { p ->
        p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    val reminder: Flow<ReminderMode> = context.dataStore.data.map { p ->
        p[reminderKey]?.let { runCatching { ReminderMode.valueOf(it) }.getOrNull() } ?: ReminderMode.OFF
    }

    private val filterFormatsKey = stringSetPreferencesKey("filter_formats")
    private val filterKindsKey = stringSetPreferencesKey("filter_kinds")
    private val filterBeginnerKey = stringPreferencesKey("filter_beginner")
    private val filterFavoritesKey = booleanPreferencesKey("filter_favorites")
    private val filterPastKey = booleanPreferencesKey("filter_past")

    /** Filtre commun aux vues, conservé après la fermeture de l'application. */
    val filter: Flow<EventFilter> = context.dataStore.data.map { p ->
        EventFilter(
            formats = p[filterFormatsKey].orEmpty().mapNotNull { runCatching { EventFormat.valueOf(it) }.getOrNull() }.toSet(),
            kinds = p[filterKindsKey].orEmpty().mapNotNull { runCatching { EventKind.valueOf(it) }.getOrNull() }.toSet(),
            beginner = p[filterBeginnerKey]?.let { runCatching { BeginnerFilter.valueOf(it) }.getOrNull() } ?: BeginnerFilter.ALL,
            favoritesOnly = p[filterFavoritesKey] ?: false,
            includePast = p[filterPastKey] ?: false,
        )
    }

    suspend fun setFilter(f: EventFilter) {
        context.dataStore.edit { p ->
            p[filterFormatsKey] = f.formats.map { it.name }.toSet()
            p[filterKindsKey] = f.kinds.map { it.name }.toSet()
            p[filterBeginnerKey] = f.beginner.name
            p[filterFavoritesKey] = f.favoritesOnly
            p[filterPastKey] = f.includePast
        }
    }

    suspend fun setTheme(mode: ThemeMode) { context.dataStore.edit { it[themeKey] = mode.name } }

    private val terminalUnlockedKey = booleanPreferencesKey("theme_terminal_unlocked")

    /** Thème secret « Terminal » débloqué (7 appuis sur la version, comme le numéro de build d'Android). */
    val terminalUnlocked: Flow<Boolean> = context.dataStore.data.map { it[terminalUnlockedKey] ?: false }

    suspend fun unlockTerminal() {
        context.dataStore.edit { it[terminalUnlockedKey] = true; it[themeKey] = ThemeMode.BRUTAL.name }
    }
    suspend fun setReminder(mode: ReminderMode) { context.dataStore.edit { it[reminderKey] = mode.name } }
}
