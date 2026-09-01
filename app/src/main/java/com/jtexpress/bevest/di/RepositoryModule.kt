package com.jtexpress.bevest.di

import com.jtexpress.bevest.data.repository.AlertRepositoryImpl
import com.jtexpress.bevest.data.repository.AuthRepositoryImpl
import com.jtexpress.bevest.data.repository.MonitoringRepositoryImpl
import com.jtexpress.bevest.data.repository.ProjectRepositoryImpl
import com.jtexpress.bevest.data.repository.ReportRepositoryImpl
import com.jtexpress.bevest.data.repository.SettingsRepositoryImpl
import com.jtexpress.bevest.data.repository.SystemRepositoryImpl
import com.jtexpress.bevest.data.repository.UserRepositoryImpl
import com.jtexpress.bevest.data.repository.VestRepositoryImpl
import com.jtexpress.bevest.data.repository.WorkerRepositoryImpl
import com.jtexpress.bevest.domain.repository.AlertRepository
import com.jtexpress.bevest.domain.repository.AuthRepository
import com.jtexpress.bevest.domain.repository.MonitoringRepository
import com.jtexpress.bevest.domain.repository.ProjectRepository
import com.jtexpress.bevest.domain.repository.ReportRepository
import com.jtexpress.bevest.domain.repository.SettingsRepository
import com.jtexpress.bevest.domain.repository.SystemRepository
import com.jtexpress.bevest.domain.repository.UserRepository
import com.jtexpress.bevest.domain.repository.VestRepository
import com.jtexpress.bevest.domain.repository.WorkerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindAccountRepository(impl: com.jtexpress.bevest.data.repository.AccountRepositoryImpl):
        com.jtexpress.bevest.domain.repository.AccountRepository

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindWorkerRepository(impl: WorkerRepositoryImpl): WorkerRepository

    @Binds @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds @Singleton
    abstract fun bindVestRepository(impl: VestRepositoryImpl): VestRepository

    @Binds @Singleton
    abstract fun bindMonitoringRepository(impl: MonitoringRepositoryImpl): MonitoringRepository

    @Binds @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindSystemRepository(impl: SystemRepositoryImpl): SystemRepository
}
