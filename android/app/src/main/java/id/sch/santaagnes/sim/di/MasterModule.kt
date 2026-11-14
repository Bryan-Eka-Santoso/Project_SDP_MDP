package id.sch.santaagnes.sim.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.remote.StudentApi
import id.sch.santaagnes.sim.data.remote.UserApi
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.StudentRepositoryImpl
import id.sch.santaagnes.sim.data.repository.UserRepository
import id.sch.santaagnes.sim.data.repository.UserRepositoryImpl
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MasterNetworkModule {

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideStudentApi(retrofit: Retrofit): StudentApi {
        return retrofit.create(StudentApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MasterRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindStudentRepository(impl: StudentRepositoryImpl): StudentRepository
}
