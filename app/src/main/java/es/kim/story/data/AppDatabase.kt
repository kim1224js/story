package es.kim.story.data
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, RpgCharacterEntity::class, RpgProgressEntity::class, RpgEquipmentInventoryEntity::class],
    version = 15,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun rpgDao(): RpgDao

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
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN blueChips INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user ADD COLUMN premiumIdColor INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user ADD COLUMN ownedApartmentDistricts TEXT NOT NULL DEFAULT ''",
                )
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user ADD COLUMN apartmentRentLastClaimAt INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN selectedTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE user SET selectedTitle = 'game_master' WHERE premiumIdColor = 1",
                )
                db.execSQL(
                    "UPDATE user SET selectedTitle = 'real_estate_master' " +
                        "WHERE selectedTitle = '' AND ownedApartmentDistricts != '' AND " +
                        "(LENGTH(ownedApartmentDistricts) - " +
                        "LENGTH(REPLACE(ownedApartmentDistricts, ',', '')) + 1) >= 25",
                )
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rpg_character (" +
                        "userId TEXT NOT NULL, slot INTEGER NOT NULL, name TEXT NOT NULL, " +
                        "avatarIndex INTEGER NOT NULL, job TEXT NOT NULL, level INTEGER NOT NULL, " +
                        "experience INTEGER NOT NULL, statPoints INTEGER NOT NULL, " +
                        "strength INTEGER NOT NULL, dexterity INTEGER NOT NULL, " +
                        "intelligence INTEGER NOT NULL, luck INTEGER NOT NULL, " +
                        "maxHp INTEGER NOT NULL, maxMp INTEGER NOT NULL, currentHp INTEGER NOT NULL, " +
                        "currentMp INTEGER NOT NULL, isDead INTEGER NOT NULL, weaponId TEXT NOT NULL, " +
                        "weaponEnhancement INTEGER NOT NULL, armorId TEXT NOT NULL, " +
                        "armorEnhancement INTEGER NOT NULL, PRIMARY KEY(userId, slot))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rpg_progress (" +
                        "userId TEXT NOT NULL PRIMARY KEY, unlockedStage INTEGER NOT NULL, " +
                        "highestClearedStage INTEGER NOT NULL, battlesWon INTEGER NOT NULL, " +
                        "battlesLost INTEGER NOT NULL)",
                )
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rpg_progress ADD COLUMN walletBalance INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS rpg_equipment_inventory (" +
                        "instanceId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerId TEXT NOT NULL, " +
                        "equipmentId TEXT NOT NULL, enhancement INTEGER NOT NULL, equippedCharacterSlot INTEGER)",
                )
                db.execSQL(
                    "INSERT INTO rpg_equipment_inventory (ownerId, equipmentId, enhancement, equippedCharacterSlot) " +
                        "SELECT userId, weaponId, weaponEnhancement, slot FROM rpg_character WHERE weaponId != ''",
                )
                db.execSQL(
                    "INSERT INTO rpg_equipment_inventory (ownerId, equipmentId, enhancement, equippedCharacterSlot) " +
                        "SELECT userId, armorId, armorEnhancement, slot FROM rpg_character WHERE armorId != ''",
                )
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user ADD COLUMN heroTitleUnlocked INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
