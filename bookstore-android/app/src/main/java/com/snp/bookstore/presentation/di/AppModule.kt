package com.snp.bookstore.presentation.di

import android.content.Context
import com.snp.bookstore.data.source.local.TokenStore
import com.snp.bookstore.data.source.remote.AuthManager
import com.snp.bookstore.data.source.remote.BookstoreApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext context: Context): AuthManager {
        return AuthManager(context)
    }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        return TokenStore(context)
    }

    @Provides
    @Singleton
    fun provideBookstoreApi(): BookstoreApi {
        return BookstoreApi()
    }
}
