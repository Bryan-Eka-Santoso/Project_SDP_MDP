package id.sch.santaagnes.sim.di

import id.sch.santaagnes.sim.data.local.MIGRATION_5_6
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.local.AppDatabase
import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.MIGRATION_4_5
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "santa_agnes_local.db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideLocalDao(
        database: AppDatabase
    ): LocalDao {
        return database.localDao()
    }
}
