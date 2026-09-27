package com.invokeil.shinigami.di

import android.content.Context
import androidx.room.Room
import com.invokeil.shinigami.core.data.db.AuditDao
import com.invokeil.shinigami.core.data.db.ConversationDao
import com.invokeil.shinigami.core.data.db.CustomCommandDao
import com.invokeil.shinigami.core.data.db.MemoryDao
import com.invokeil.shinigami.core.data.db.MessageDao
import com.invokeil.shinigami.core.data.db.ProtectedAppDao
import com.invokeil.shinigami.core.data.db.ProviderProfileDao
import com.invokeil.shinigami.core.data.db.ShinigamiDatabase
import com.invokeil.shinigami.core.data.db.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShinigamiDatabase =
        Room.databaseBuilder(context, ShinigamiDatabase::class.java, ShinigamiDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providerProfileDao(db: ShinigamiDatabase): ProviderProfileDao = db.providerProfileDao()
    @Provides fun conversationDao(db: ShinigamiDatabase): ConversationDao = db.conversationDao()
    @Provides fun messageDao(db: ShinigamiDatabase): MessageDao = db.messageDao()
    @Provides fun auditDao(db: ShinigamiDatabase): AuditDao = db.auditDao()
    @Provides fun usageDao(db: ShinigamiDatabase): UsageDao = db.usageDao()
    @Provides fun customCommandDao(db: ShinigamiDatabase): CustomCommandDao = db.customCommandDao()
    @Provides fun memoryDao(db: ShinigamiDatabase): MemoryDao = db.memoryDao()
    @Provides fun protectedAppDao(db: ShinigamiDatabase): ProtectedAppDao = db.protectedAppDao()
}
