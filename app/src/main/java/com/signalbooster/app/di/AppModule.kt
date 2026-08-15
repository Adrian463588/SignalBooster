package com.signalbooster.app.di

import android.content.Context
import com.signalbooster.app.data.DataStoreSettingsRepository
import com.signalbooster.app.domain.interfaces.AcousticMaskingController
import com.signalbooster.app.domain.interfaces.InterferenceClassifier
import com.signalbooster.app.domain.interfaces.NetworkMonitor
import com.signalbooster.app.domain.interfaces.PrivilegeGateway
import com.signalbooster.app.domain.interfaces.QualityProbe
import com.signalbooster.app.domain.interfaces.RadioTelemetrySource
import com.signalbooster.app.domain.interfaces.RecoveryCoordinator
import com.signalbooster.app.domain.interfaces.SettingsRepository
import com.signalbooster.app.platform.AndroidNetworkMonitor
import com.signalbooster.app.platform.AndroidTelemetrySource
import com.signalbooster.app.platform.RealQualityProbe
import com.signalbooster.app.platform.RealRecoveryCoordinator
import com.signalbooster.app.privacy.LocalInterferenceClassifier
import com.signalbooster.app.privacy.RealAcousticMaskingController
import com.signalbooster.app.privilege.RealPrivilegeGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(
        impl: AndroidNetworkMonitor
    ): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindQualityProbe(
        impl: RealQualityProbe
    ): QualityProbe

    @Binds
    @Singleton
    abstract fun bindRecoveryCoordinator(
        impl: RealRecoveryCoordinator
    ): RecoveryCoordinator

    @Binds
    @Singleton
    abstract fun bindRadioTelemetrySource(
        impl: AndroidTelemetrySource
    ): RadioTelemetrySource

    @Binds
    @Singleton
    abstract fun bindPrivilegeGateway(
        impl: RealPrivilegeGateway
    ): PrivilegeGateway

    @Binds
    @Singleton
    abstract fun bindInterferenceClassifier(
        impl: LocalInterferenceClassifier
    ): InterferenceClassifier

    @Binds
    @Singleton
    abstract fun bindAcousticMaskingController(
        impl: RealAcousticMaskingController
    ): AcousticMaskingController

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DataStoreSettingsRepository
    ): SettingsRepository
}