package com.chroniclequest.di

import com.chroniclequest.service.AmbientPipeline
import com.chroniclequest.service.AmbientQuestPipeline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindAmbientPipeline(impl: AmbientQuestPipeline): AmbientPipeline
}
