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
import id.sch.santaagnes.sim.data.remote.AdminOverviewApi
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
import id.sch.santaagnes.sim.data.remote.SchoolInfoApi
import id.sch.santaagnes.sim.data.remote.PelanggaranApi
import id.sch.santaagnes.sim.data.remote.RiwayatPelanggaranApi
import id.sch.santaagnes.sim.data.remote.AjukanPeminjamanApi
import id.sch.santaagnes.sim.data.remote.RiwayatPeminjamanApi
import id.sch.santaagnes.sim.data.remote.BukuPenghubungApi
import id.sch.santaagnes.sim.data.remote.BacaBukuPenghubungApi
import id.sch.santaagnes.sim.data.remote.ProfilSiswaBinaanApi
import id.sch.santaagnes.sim.data.remote.SurveiMinatApi
import id.sch.santaagnes.sim.data.remote.IsiSurveiMinatApi

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
    fun provideProgressNoteApi(retrofit: Retrofit): id.sch.santaagnes.sim.data.remote.ProgressNoteApi =
        retrofit.create(id.sch.santaagnes.sim.data.remote.ProgressNoteApi::class.java)

    @Provides
    @Singleton
    fun provideGradesApi(
        retrofit: Retrofit
    ): id.sch.santaagnes.sim.data.remote.GradesApi {
        return retrofit.create(
            id.sch.santaagnes.sim.data.remote.GradesApi::class.java
        )
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
    fun provideAttendanceApi(
        retrofit: Retrofit
    ): id.sch.santaagnes.sim.data.remote.AttendanceApi {
        return retrofit.create(
            id.sch.santaagnes.sim.data.remote.AttendanceApi::class.java
        )
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
    fun provideLeaveApi(
        retrofit: Retrofit
    ): id.sch.santaagnes.sim.data.remote.LeaveApi {
        return retrofit.create(
            id.sch.santaagnes.sim.data.remote.LeaveApi::class.java
        )
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
    fun provideAdminOverviewApi(
        retrofit: Retrofit
    ): AdminOverviewApi {
        return retrofit.create(AdminOverviewApi::class.java)
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
    fun provideElectiveAllocationApi(retrofit: Retrofit): id.sch.santaagnes.sim.data.remote.ElectiveAllocationApi {
        return retrofit.create(id.sch.santaagnes.sim.data.remote.ElectiveAllocationApi::class.java)
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

    @Provides
    @Singleton
    fun provideSchoolInfoApi(
        retrofit: Retrofit
    ): SchoolInfoApi {
        return retrofit.create(SchoolInfoApi::class.java)
    }

    @Provides
    @Singleton
    fun providePelanggaranApi(
        retrofit: Retrofit
    ): PelanggaranApi {
        return retrofit.create(PelanggaranApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRiwayatPelanggaranApi(
        retrofit: Retrofit
    ): RiwayatPelanggaranApi {
        return retrofit.create(
            RiwayatPelanggaranApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideAjukanPeminjamanApi(
        retrofit: Retrofit
    ): AjukanPeminjamanApi {
        return retrofit.create(
            AjukanPeminjamanApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideRiwayatPeminjamanApi(
        retrofit: Retrofit
    ): RiwayatPeminjamanApi {
        return retrofit.create(
            RiwayatPeminjamanApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideBukuPenghubungApi(
        retrofit: Retrofit
    ): BukuPenghubungApi {
        return retrofit.create(
            BukuPenghubungApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideBacaBukuPenghubungApi(
        retrofit: Retrofit
    ): BacaBukuPenghubungApi {
        return retrofit.create(
            BacaBukuPenghubungApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideProfilSiswaBinaanApi(
        retrofit: Retrofit
    ): ProfilSiswaBinaanApi {
        return retrofit.create(
            ProfilSiswaBinaanApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideSurveiMinatApi(
        retrofit: Retrofit
    ): SurveiMinatApi {
        return retrofit.create(
            SurveiMinatApi::class.java
        )
    }

    @Provides
    @Singleton
    fun provideIsiSurveiMinatApi(
        retrofit: Retrofit
    ): IsiSurveiMinatApi {
        return retrofit.create(
            IsiSurveiMinatApi::class.java
        )
    }
}
