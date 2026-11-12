package id.sch.santaagnes.sim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.repository.NotificationRepository
import id.sch.santaagnes.sim.data.repository.NotificationRepositoryImpl
import id.sch.santaagnes.sim.notification.FirebasePushTokenProvider
import id.sch.santaagnes.sim.notification.NotificationPresenter
import id.sch.santaagnes.sim.notification.PushTokenProvider
import id.sch.santaagnes.sim.notification.SystemNotificationPresenter

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    abstract fun bindPushTokenProvider(impl: FirebasePushTokenProvider): PushTokenProvider

    @Binds
    abstract fun bindNotificationPresenter(impl: SystemNotificationPresenter): NotificationPresenter
}
