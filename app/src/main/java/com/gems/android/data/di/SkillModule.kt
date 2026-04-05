package com.gems.android.data.di

import com.gems.android.data.skill.AssetSkillManager
import com.gems.android.domain.skill.SkillManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SkillModule {

    @Binds
    @Singleton
    abstract fun bindSkillManager(impl: AssetSkillManager): SkillManager
}
