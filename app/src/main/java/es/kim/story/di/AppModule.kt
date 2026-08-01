package es.kim.story.di
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.kim.story.data.AppDatabase
import es.kim.story.data.UserDao
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "story.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
            )
            .build()
    @Provides fun userDao(db: AppDatabase): UserDao = db.userDao()
}
