package fr.cyberagenda.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [EventEntity::class, FavoriteEntity::class, SourceSyncEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class CyberAgendaDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun sourceSyncDao(): SourceSyncDao
}

/*
 * Pas de chiffrement de la base : elle ne contient que des données publiques
 * (événements) et une liste de favoris. Elle reste dans le stockage privé de l'app,
 * exclue des sauvegardes (voir data_extraction_rules.xml).
 */
/** v2 : ajout du tarif des événements. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN price TEXT DEFAULT NULL")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CyberAgendaDatabase =
        Room.databaseBuilder(context, CyberAgendaDatabase::class.java, "cyberagenda.db")
            // Migrations explicites : les favoris de l'utilisateur sont conservés.
            .addMigrations(MIGRATION_1_2)
            // Dernier recours (ex. retour à une version antérieure) : on repart de zéro.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides fun eventDao(db: CyberAgendaDatabase): EventDao = db.eventDao()
    @Provides fun sourceSyncDao(db: CyberAgendaDatabase): SourceSyncDao = db.sourceSyncDao()
}
