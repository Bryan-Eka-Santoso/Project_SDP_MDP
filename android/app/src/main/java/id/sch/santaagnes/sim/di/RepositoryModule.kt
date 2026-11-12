package id.sch.santaagnes.sim.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.sch.santaagnes.sim.data.repository.AccountRepository
import id.sch.santaagnes.sim.data.repository.AccountRepositoryImpl
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepository
import id.sch.santaagnes.sim.data.repository.AdminScheduleRepositoryImpl
import id.sch.santaagnes.sim.data.repository.BillRepository
import id.sch.santaagnes.sim.data.repository.BillRepositoryImpl
import id.sch.santaagnes.sim.data.repository.ReportRepository
import id.sch.santaagnes.sim.data.repository.ReportRepositoryImpl
import id.sch.santaagnes.sim.data.repository.ScheduleRepository
import id.sch.santaagnes.sim.data.repository.ScheduleRepositoryImpl
import id.sch.santaagnes.sim.data.repository.TaskRepository
import id.sch.santaagnes.sim.data.repository.TaskRepositoryImpl
import id.sch.santaagnes.sim.data.repository.TodoRepository
import id.sch.santaagnes.sim.data.repository.TodoRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    abstract fun bindAdminScheduleRepository(impl: AdminScheduleRepositoryImpl): AdminScheduleRepository

    @Binds
    abstract fun bindBillRepository(impl: BillRepositoryImpl): BillRepository

    @Binds
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindTodoRepository(impl: TodoRepositoryImpl): TodoRepository
}
