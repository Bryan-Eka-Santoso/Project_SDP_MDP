
package id.sch.santaagnes.sim.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.local.AppDatabase
import id.sch.santaagnes.sim.data.local.LocalDao
import id.sch.santaagnes.sim.data.local.ScheduleDao
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
            "santa_agnes_db"
        ).build()
    }

    @Provides
    fun provideLocalDao(
        database: AppDatabase
    ): LocalDao {
        return database.localDao()
    fun provideScheduleDao(
        database: AppDatabase
    ): ScheduleDao {
        return database.scheduleDao()
    }
}
