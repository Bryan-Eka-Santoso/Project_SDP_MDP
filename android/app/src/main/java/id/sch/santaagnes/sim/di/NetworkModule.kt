
package id.sch.santaagnes.sim.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.BuildConfig
import id.sch.santaagnes.sim.data.local.SessionManager
import id.sch.santaagnes.sim.data.remote.AuthApi
import id.sch.santaagnes.sim.data.remote.AuthInterceptor
import id.sch.santaagnes.sim.data.remote.TokenAuthenticator
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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
        sessionManager: SessionManager
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(sessionManager)
            )
            .authenticator(
                TokenAuthenticator(
                    sessionManager,
                    BuildConfig.BASE_URL
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
    fun provideLeaveApi(
        retrofit: Retrofit
    ): id.sch.santaagnes.sim.data.remote.LeaveApi {
        return retrofit.create(
            id.sch.santaagnes.sim.data.remote.LeaveApi::class.java
        )
    }
}
