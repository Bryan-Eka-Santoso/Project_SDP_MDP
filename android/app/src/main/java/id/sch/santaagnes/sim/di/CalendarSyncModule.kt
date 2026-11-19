package id.sch.santaagnes.sim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.local.CalendarSyncStore
import id.sch.santaagnes.sim.data.local.ContentResolverDeviceCalendar
import id.sch.santaagnes.sim.data.local.DataStoreCalendarSyncStore
import id.sch.santaagnes.sim.data.local.DeviceCalendar
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepository
import id.sch.santaagnes.sim.data.repository.CalendarSyncRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarSyncModule {

    @Binds
    abstract fun bindCalendarSyncRepository(impl: CalendarSyncRepositoryImpl): CalendarSyncRepository

    @Binds
    abstract fun bindCalendarSyncStore(impl: DataStoreCalendarSyncStore): CalendarSyncStore

    @Binds
    abstract fun bindDeviceCalendar(impl: ContentResolverDeviceCalendar): DeviceCalendar
}
