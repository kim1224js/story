package es.kim.story.data
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UserEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN money INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN gender TEXT NOT NULL DEFAULT '남성'")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE user_new (userId TEXT NOT NULL PRIMARY KEY, money INTEGER NOT NULL, " +
                        "gender TEXT NOT NULL, chapter INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO user_new (userId, money, gender, chapter) " +
                        "SELECT userId, money, gender, 1 FROM user",
                )
                db.execSQL("DROP TABLE user")
                db.execSQL("ALTER TABLE user_new RENAME TO user")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE user SET money = 10000000 WHERE userId = '심심'")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE user SET money = 0 WHERE userId = '심심' AND money = 10000000")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN seotdaName1 TEXT NOT NULL DEFAULT '졸린'")
                db.execSQL("ALTER TABLE user ADD COLUMN seotdaName2 TEXT NOT NULL DEFAULT '토끼'")
                db.execSQL("ALTER TABLE user ADD COLUMN seotdaName3 TEXT NOT NULL DEFAULT '콜라'")
            }
        }
    }
}
