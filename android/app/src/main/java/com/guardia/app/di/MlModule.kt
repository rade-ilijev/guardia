package com.guardia.app.di

import com.guardia.app.core.ml.FacePipeline
import com.guardia.app.core.ml.FacePipelineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {

    @Binds
    @Singleton
    abstract fun bindFacePipeline(impl: FacePipelineImpl): FacePipeline
}
