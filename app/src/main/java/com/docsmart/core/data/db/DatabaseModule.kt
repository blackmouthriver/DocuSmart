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
    fun provideDocuSmartDatabase(
        @ApplicationContext context: Context,
    ): DocuSmartDatabase =
        Room
            .databaseBuilder(context, DocuSmartDatabase::class.java, "docsmart.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDocumentHistoryDao(database: DocuSmartDatabase): DocumentHistoryDao = database.documentHistoryDao()

    @Provides
    fun provideTrashDao(database: DocuSmartDatabase): TrashDao = database.trashDao()

    @Provides
    fun provideAnnotationDao(database: DocuSmartDatabase): AnnotationDao = database.annotationDao()

    @Provides
    fun providePageBookmarkDao(database: DocuSmartDatabase): PageBookmarkDao = database.pageBookmarkDao()

    @Provides
    fun provideLastViewedPageDao(database: DocuSmartDatabase): LastViewedPageDao = database.lastViewedPageDao()

    @Provides
    fun provideNoteDao(database: DocuSmartDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideAgendaEventDao(database: DocuSmartDatabase): AgendaEventDao = database.agendaEventDao()
}
