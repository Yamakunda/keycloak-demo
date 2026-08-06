package com.snp.bookstorebio.presentation.di

import android.content.Context
import com.snp.bookstorebio.data.source.local.BiometricVault
import com.snp.bookstorebio.data.source.remote.AuthManager
import com.snp.bookstorebio.data.source.remote.BookstoreApi
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
    fun provideBiometricVault(@ApplicationContext context: Context): BiometricVault {
        return BiometricVault(context)
    }

    @Provides
    @Singleton
    fun provideBookstoreApi(): BookstoreApi {
        return BookstoreApi()
    }
}
