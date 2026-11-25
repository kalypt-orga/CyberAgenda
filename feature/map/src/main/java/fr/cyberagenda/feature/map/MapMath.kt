package fr.cyberagenda.feature.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sinh
import kotlin.math.tan

/*
 * Calculs de caméra en projection Web Mercator, avec la convention de MapLibre : à un zoom z,
 * le monde mesure 512·2^z dp. Tout est explicite et sans état, donc testable et reproductible.
 */

private const val TILE_DP = 512.0

internal fun mercatorX(lon: Double) = (lon + 180.0) / 360.0
internal fun mercatorY(lat: Double): Double {
    val r = Math.toRadians(lat)
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
}
internal fun latitudeOf(mercatorY: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * mercatorY))))

/** Latitude du point situé [dp] plus au sud (ou au nord si négatif) que [lat] au zoom donné. */
internal fun shiftSouth(lat: Double, zoom: Double, dp: Double): Double =
    latitudeOf(mercatorY(lat) + dp / (TILE_DP * Math.pow(2.0, zoom)))

/** Zoom pour qu'un cercle de [radiusKm] autour de [lat] occupe [radiusDp] à l'écran. */
internal fun zoomToFit(radiusKm: Double, lat: Double, radiusDp: Double): Double {
    val earthCircumferenceKm = 40_075.017
    return log2(radiusDp * earthCircumferenceKm * cos(Math.toRadians(lat)) / (TILE_DP * radiusKm)).coerceIn(4.0, 13.0)
}

/** Centre et zoom pour cadrer tous les points dans un espace de [widthDp] × [heightDp]. */
internal data class Framing(val latitude: Double, val longitude: Double, val zoom: Double)

internal fun frame(points: List<Pair<Double, Double>>, widthDp: Double, heightDp: Double, maxZoom: Double = 10.0): Framing? {
    if (points.isEmpty() || widthDp <= 0 || heightDp <= 0) return null
    val xs = points.map { mercatorX(it.second) }
    val ys = points.map { mercatorY(it.first) }
    val dx = (xs.max() - xs.min()) * TILE_DP
    val dy = (ys.max() - ys.min()) * TILE_DP
    val zx = if (dx > 0) log2(widthDp / dx) else maxZoom
    val zy = if (dy > 0) log2(heightDp / dy) else maxZoom
    return Framing(
        latitude = latitudeOf((ys.max() + ys.min()) / 2),
        longitude = (points.maxOf { it.second } + points.minOf { it.second }) / 2,
        zoom = minOf(zx, zy, maxZoom).coerceAtLeast(3.0),
    )
}

/** France métropolitaine et Corse. */
internal val FRANCE_BOUNDS = listOf(51.1 to -5.2, 41.3 to 9.6)
