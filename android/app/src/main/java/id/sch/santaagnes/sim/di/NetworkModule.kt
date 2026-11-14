package id.sch.santaagnes.sim.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.BuildConfig
import id.sch.santaagnes.sim.core.SessionEvents
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AdminScheduleApi
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.remote.BillApi
import id.sch.santaagnes.sim.data.remote.DeviceTokenApi
import id.sch.santaagnes.sim.data.remote.AuthInterceptor
import id.sch.santaagnes.sim.data.remote.MasterLookupApi
import id.sch.santaagnes.sim.data.remote.ReportApi
import id.sch.santaagnes.sim.data.remote.ScheduleApi
import id.sch.santaagnes.sim.data.remote.TaskApi
import id.sch.santaagnes.sim.data.remote.TokenAuthenticator
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import id.sch.santaagnes.sim.data.remote.MataPelajaranApi
import id.sch.santaagnes.sim.data.remote.FasilitasApi
import id.sch.santaagnes.sim.data.remote.JenisPelanggaranApi
import id.sch.santaagnes.sim.data.remote.PeminjamanApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context
    ): SessionManager {
        return SessionManager(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionManager: SessionManager,
        sessionEvents: SessionEvents
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(sessionManager)
            )
            .authenticator(
                TokenAuthenticator(
                    sessionManager,
                    BuildConfig.BASE_URL,
                    sessionEvents
                )
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
        }

    @Provides
    @Singleton
    fun provideAuthApi(
        retrofit: Retrofit
    ): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideScheduleApi(
        retrofit: Retrofit
    ): ScheduleApi {
        return retrofit.create(ScheduleApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAdminScheduleApi(
        retrofit: Retrofit
    ): AdminScheduleApi {
        return retrofit.create(AdminScheduleApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMasterLookupApi(
        retrofit: Retrofit
    ): MasterLookupApi {
        return retrofit.create(MasterLookupApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTaskApi(
        retrofit: Retrofit
    ): TaskApi {
        return retrofit.create(TaskApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBillApi(
        retrofit: Retrofit
    ): BillApi {
        return retrofit.create(BillApi::class.java)
    }

    @Provides
    @Singleton
    fun provideReportApi(
        retrofit: Retrofit
    ): ReportApi {
        return retrofit.create(ReportApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDeviceTokenApi(
        retrofit: Retrofit
    ): DeviceTokenApi {
        return retrofit.create(DeviceTokenApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMataPelajaranApi(
        retrofit: Retrofit
    ): MataPelajaranApi {
        return retrofit.create(MataPelajaranApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFasilitasApi(
        retrofit: Retrofit
    ): FasilitasApi {
        return retrofit.create(FasilitasApi::class.java)
    }

    @Provides
    @Singleton
    fun provideJenisPelanggaranApi(
        retrofit: Retrofit
    ): JenisPelanggaranApi {
        return retrofit.create(JenisPelanggaranApi::class.java)
    }

    @Provides
    @Singleton
    fun providePeminjamanApi(
        retrofit: Retrofit
    ): PeminjamanApi {
        return retrofit.create(PeminjamanApi::class.java)
    }
}
