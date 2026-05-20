package com.navink.di

import com.navink.data.remote.SubsonicAuthInterceptor
import com.navink.data.remote.SubsonicService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Placeholder base URL — SubsonicAuthInterceptor rewrites it per-request.
    private const val PLACEHOLDER_URL = "http://localhost/"

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: SubsonicAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideSubsonicService(retrofit: Retrofit): SubsonicService =
        retrofit.create(SubsonicService::class.java)
}
