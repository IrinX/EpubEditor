package com.example.epubeditor.di

import com.example.epubeditor.data.epub.EpubParser
import com.example.epubeditor.data.epub.EpubWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEpubParser(): EpubParser = EpubParser()

    @Provides
    @Singleton
    fun provideEpubWriter(): EpubWriter = EpubWriter()
}
