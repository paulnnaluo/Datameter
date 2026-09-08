package com.datameter.data.control

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DataControlDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE data_control_settings (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE app_rules (
                uid INTEGER PRIMARY KEY NOT NULL,
                package_name TEXT NOT NULL,
                label TEXT NOT NULL,
                block_mobile_data INTEGER NOT NULL DEFAULT 0,
                block_wifi INTEGER NOT NULL DEFAULT 0,
                daily_limit_enabled INTEGER NOT NULL DEFAULT 0,
                daily_limit_bytes INTEGER NOT NULL DEFAULT $DEFAULT_APP_LIMIT_BYTES,
                auto_blocked_local_date TEXT,
                auto_blocked_reason TEXT,
                auto_blocked_at_millis INTEGER,
                updated_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE daily_app_usage (
                local_date TEXT NOT NULL,
                uid INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                label TEXT NOT NULL,
                mobile_bytes INTEGER NOT NULL DEFAULT 0,
                wifi_bytes INTEGER NOT NULL DEFAULT 0,
                updated_at_millis INTEGER NOT NULL,
                PRIMARY KEY(local_date, uid)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE block_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid INTEGER NOT NULL,
                package_name TEXT NOT NULL,
                label TEXT NOT NULL,
                local_date TEXT NOT NULL,
                reason TEXT NOT NULL,
                network_type TEXT NOT NULL,
                used_bytes INTEGER NOT NULL,
                limit_bytes INTEGER,
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_daily_app_usage_uid_date ON daily_app_usage(uid, local_date)")
        db.execSQL("CREATE INDEX idx_block_events_uid_created ON block_events(uid, created_at_millis)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            onCreate(db)
        }
    }

    private companion object {
        const val DATABASE_NAME = "datameter_data_control.db"
        const val DATABASE_VERSION = 1
    }
}
