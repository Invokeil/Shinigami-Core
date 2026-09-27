package com.invokeil.shinigami.di

import com.invokeil.shinigami.core.overlay.AssistantOverlayController
import com.invokeil.shinigami.core.overlay.DefaultAssistantOverlayController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OverlayModule {

    @Binds
    @Singleton
    abstract fun bindOverlayController(impl: DefaultAssistantOverlayController): AssistantOverlayController
}
