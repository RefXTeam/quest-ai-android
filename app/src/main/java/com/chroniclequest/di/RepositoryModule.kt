package com.chroniclequest.di

import com.chroniclequest.data.repository.QuestRepositoryImpl
import com.chroniclequest.data.repository.UserStatsRepositoryImpl
import com.chroniclequest.domain.repository.QuestRepository
import com.chroniclequest.domain.repository.UserStatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindQuestRepository(impl: QuestRepositoryImpl): QuestRepository

    @Binds
    @Singleton
    abstract fun bindUserStatsRepository(impl: UserStatsRepositoryImpl): UserStatsRepository
}
