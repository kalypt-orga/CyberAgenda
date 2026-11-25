package fr.cyberagenda.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Validation des URL provenant des sources avant de les stocker ou de les ouvrir. */
object SafeUrl {
    /** Renvoie l'URL normalisée si elle est https, sans identifiants, avec un hôte valide. */
    fun parseHttps(raw: String?): HttpUrl? {
        if (raw.isNullOrBlank() || raw.length > 2_048) return null
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null // https://banque.fr@evil.com
        if (url.host.isBlank() || !url.host.contains('.')) return null
        return url
    }

    fun isHttps(raw: String?): Boolean = parseHttps(raw) != null

    /** Domaine à afficher à l'utilisateur avant d'ouvrir un lien (version punycode, anti-homographe). */
    fun displayHost(raw: String?): String? = parseHttps(raw)?.host
}
