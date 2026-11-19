package id.sch.santaagnes.sim.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.remote.AcademicYearApi
import id.sch.santaagnes.sim.data.remote.ClassApi
import id.sch.santaagnes.sim.data.remote.ElectiveApi
import id.sch.santaagnes.sim.data.remote.GuardianApi
import id.sch.santaagnes.sim.data.remote.StudentApi
import id.sch.santaagnes.sim.data.remote.TeacherApi
import id.sch.santaagnes.sim.data.remote.TeachingAssignmentApi
import id.sch.santaagnes.sim.data.remote.UserApi
import id.sch.santaagnes.sim.data.repository.AcademicYearRepository
import id.sch.santaagnes.sim.data.repository.AcademicYearRepositoryImpl
import id.sch.santaagnes.sim.data.repository.ClassRepository
import id.sch.santaagnes.sim.data.repository.ClassRepositoryImpl
import id.sch.santaagnes.sim.data.repository.ElectiveRepository
import id.sch.santaagnes.sim.data.repository.ElectiveRepositoryImpl
import id.sch.santaagnes.sim.data.repository.GuardianRepository
import id.sch.santaagnes.sim.data.repository.GuardianRepositoryImpl
import id.sch.santaagnes.sim.data.repository.StudentRepository
import id.sch.santaagnes.sim.data.repository.StudentRepositoryImpl
import id.sch.santaagnes.sim.data.repository.TeacherRepository
import id.sch.santaagnes.sim.data.repository.TeacherRepositoryImpl
import id.sch.santaagnes.sim.data.repository.UserRepository
import id.sch.santaagnes.sim.data.repository.UserRepositoryImpl
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MasterNetworkModule {

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideStudentApi(retrofit: Retrofit): StudentApi = retrofit.create(StudentApi::class.java)

    @Provides
    @Singleton
    fun provideTeacherApi(retrofit: Retrofit): TeacherApi = retrofit.create(TeacherApi::class.java)

    @Provides
    @Singleton
    fun provideTeachingAssignmentApi(retrofit: Retrofit): TeachingAssignmentApi =
        retrofit.create(TeachingAssignmentApi::class.java)

    @Provides
    @Singleton
    fun provideClassApi(retrofit: Retrofit): ClassApi = retrofit.create(ClassApi::class.java)

    @Provides
    @Singleton
    fun provideAcademicYearApi(retrofit: Retrofit): AcademicYearApi =
        retrofit.create(AcademicYearApi::class.java)

    @Provides
    @Singleton
    fun provideGuardianApi(retrofit: Retrofit): GuardianApi =
        retrofit.create(GuardianApi::class.java)

    @Provides
    @Singleton
    fun provideElectiveApi(retrofit: Retrofit): ElectiveApi =
        retrofit.create(ElectiveApi::class.java)
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

    @Binds
    @Singleton
    abstract fun bindTeacherRepository(impl: TeacherRepositoryImpl): TeacherRepository

    @Binds
    @Singleton
    abstract fun bindClassRepository(impl: ClassRepositoryImpl): ClassRepository

    @Binds
    @Singleton
    abstract fun bindAcademicYearRepository(impl: AcademicYearRepositoryImpl): AcademicYearRepository

    @Binds
    @Singleton
    abstract fun bindGuardianRepository(impl: GuardianRepositoryImpl): GuardianRepository

    @Binds
    @Singleton
    abstract fun bindElectiveRepository(impl: ElectiveRepositoryImpl): ElectiveRepository
}
