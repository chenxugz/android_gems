package com.gems.android.data.di

import com.gems.android.data.engine.LiteRtLmEngine
import com.gems.android.data.engine.SdCppEngine
import com.gems.android.domain.engine.ImageGenEngine
import com.gems.android.domain.engine.LlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds domain-layer engine interfaces to their concrete
 * Data-layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindImageGenEngine(impl: SdCppEngine): ImageGenEngine

    @Binds
    @Singleton
    abstract fun bindLlmEngine(impl: LiteRtLmEngine): LlmEngine
}
