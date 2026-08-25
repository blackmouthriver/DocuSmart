package com.docsmart.core.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Único @Module del proyecto: todo lo demás usa @Inject constructor
// directamente, pero Room.databaseBuilder() no es una clase instanciable con
// un constructor simple, así que necesita un builder explícito.
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDocuSmartDatabase(@ApplicationContext context: Context): DocuSmartDatabase =
        Room.databaseBuilder(context, DocuSmartDatabase::class.java, "docsmart.db").build()

    @Provides
    fun provideDocumentHistoryDao(database: DocuSmartDatabase): DocumentHistoryDao =
        database.documentHistoryDao()
}
