package com.difusion.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Contact::class, MessageTemplate::class, SendRecord::class, SmsMessage::class, CallRecord::class, PurgedSms::class],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun templateDao(): TemplateDao
    abstract fun sendDao(): SendDao
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun callDao(): CallDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`providerId` INTEGER, " +
                        "`threadId` INTEGER NOT NULL, " +
                        "`address` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`isIncoming` INTEGER NOT NULL, " +
                        "`status` INTEGER NOT NULL, " +
                        "`read` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calls` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`contactName` TEXT NOT NULL, " +
                        "`phone` TEXT NOT NULL, " +
                        "`success` INTEGER NOT NULL, " +
                        "`label` TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN assignment TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sends ADD COLUMN user TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE calls ADD COLUMN user TEXT NOT NULL DEFAULT ''")
            }
        }

        // Papelera de mensajes: bandera inTrash en sms_messages y tabla de ids
        // borrados definitivamente (no se re-importan al sincronizar).
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN inTrash INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_purged` (" +
                        "`providerId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`providerId`))"
                )
            }
        }

        // Soporte MMS: campos de multimedia en sms_messages y clave compuesta
        // (providerId, isMms) en sms_purged (los ids de SMS y MMS no coinciden).
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN isMms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN mediaPath TEXT")
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN mediaMime TEXT")
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN subject TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_purged_new` (" +
                        "`providerId` INTEGER NOT NULL, " +
                        "`isMms` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`providerId`, `isMms`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sms_purged_providerId` " +
                        "ON `sms_purged_new` (`providerId`)"
                )
                db.execSQL("INSERT INTO `sms_purged_new` (`providerId`, `isMms`) SELECT `providerId`, 0 FROM `sms_purged`")
                db.execSQL("DROP TABLE `sms_purged`")
                db.execSQL("ALTER TABLE `sms_purged_new` RENAME TO `sms_purged`")
            }
        }

        // Causa del fallo al enviar SMS/MMS (código de error + etiqueta) y
        // desglose de causas por lote en los registros de envío.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN errorCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sms_messages ADD COLUMN errorLabel TEXT")
                db.execSQL("ALTER TABLE sends ADD COLUMN failureBreakdown TEXT NOT NULL DEFAULT ''")
            }
        }

        // Cédula de identidad: además del nombre y la asignación, Google Drive
        // añade la cédula como tercera columna y la muestra en la tarjeta del
        // contacto junto al nombre.
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN cedula TEXT NOT NULL DEFAULT ''")
            }
        }

        // Categoría de gestión: la hoja de Drive añade una columna "Gestión" y
        // los contactos se agrupan por ese valor para filtrarlos en Contactos.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN gestion TEXT NOT NULL DEFAULT ''")
            }
        }

        // Categorías adicionales de la hoja: estado (STATUS) y medio de contacto.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN estado TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN medio TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN idCuota TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE contacts ADD COLUMN monto TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "difusion.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}