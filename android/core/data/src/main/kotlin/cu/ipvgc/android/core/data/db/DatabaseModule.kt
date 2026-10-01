package cu.ipvgc.android.core.data.db

import android.content.Context
import androidx.room.Room
import cu.ipvgc.android.core.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        key: DatabaseKeyProvider,
    ): IpvDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(key.passphrase())
        return Room.databaseBuilder(context, IpvDatabase::class.java, "ipvgc.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
