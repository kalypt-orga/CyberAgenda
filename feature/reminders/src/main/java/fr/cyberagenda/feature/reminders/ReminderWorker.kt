package fr.cyberagenda.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.cyberagenda.core.ui.Formatters

/** Affiche la notification de rappel d'un événement favori. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val start = inputData.getLong(KEY_START, 0)
        val place = inputData.getString(KEY_PLACE).orEmpty()
        if (start <= System.currentTimeMillis()) return Result.success() // trop tard : pas de rappel

        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        ensureChannel(ctx)
        // Intent explicite vers l'app elle-même, immuable : aucune autre app ne peut le détourner.
        // L'identifiant de l'événement est aussi placé dans la donnée de l'intent : deux rappels
        // ne partagent jamais le même PendingIntent (un hashCode() identique ne suffit plus).
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.putExtra(EXTRA_EVENT_ID, id)
            ?.setData(Uri.fromParts(URI_SCHEME, id, null))
            ?.setPackage(ctx.packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        // Écran verrouillé : version neutre, sans titre ni lieu (les favoris révèlent centres
        // d'intérêt et déplacements). Le détail n'apparaît qu'une fois le téléphone déverrouillé,
        // et jamais si l'utilisateur a choisi de masquer le contenu sensible.
        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("Rappel CyberAgenda")
            .setContentText("Un événement suivi approche")
            .build()
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText("${Formatters.dateTime(start)} · $place")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        // Le tag (identifiant complet) distingue les notifications : pas de collision possible.
        NotificationManagerCompat.from(ctx).notify(id, NOTIFICATION_ID, notification)
        return Result.success()
    }

    private fun ensureChannel(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rappels d'événements", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Rappels pour les événements mis en favori"
                }
            )
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val KEY_TITLE = "title"
        const val KEY_START = "start"
        const val KEY_PLACE = "place"
        const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_ID = 1
        private const val URI_SCHEME = "cyberagenda-event"
        /** Extra lu par MainActivity pour ouvrir la fiche de l'événement. */
        const val EXTRA_EVENT_ID = "fr.cyberagenda.extra.EVENT_ID"
    }
}
