package com.datameter.data.control

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class SqliteDataControlRepository(context: Context) : DataControlRepository {
    private val helper = DataControlDatabaseHelper(context.applicationContext)
    private val lock = Any()

    override fun readSettings(): DataControlSettings = synchronized(lock) {
        val values = readSettingsMap(helper.readableDatabase)
        return@synchronized DataControlSettings(
            enabled = values.boolean(KEY_ENABLED, false),
            disclosureAccepted = values.boolean(KEY_DISCLOSURE_ACCEPTED, false),
            globalAutoBlockEnabled = values.boolean(KEY_GLOBAL_AUTO_BLOCK_ENABLED, false),
            globalAutoBlockBytes = values.long(KEY_GLOBAL_AUTO_BLOCK_BYTES, DEFAULT_APP_LIMIT_BYTES),
        )
    }

    override fun saveSettings(settings: DataControlSettings) = synchronized(lock) {
        helper.writableDatabase.runInTransaction {
            putSetting(KEY_ENABLED, settings.enabled.asText())
            putSetting(KEY_DISCLOSURE_ACCEPTED, settings.disclosureAccepted.asText())
            putSetting(KEY_GLOBAL_AUTO_BLOCK_ENABLED, settings.globalAutoBlockEnabled.asText())
            putSetting(KEY_GLOBAL_AUTO_BLOCK_BYTES, settings.globalAutoBlockBytes.toString())
        }
    }

    override fun acceptDisclosure() {
        saveSettings(readSettings().copy(disclosureAccepted = true))
    }

    override fun readRule(uid: Int, packageName: String, label: String): DataControlRule = synchronized(lock) {
        queryRule(helper.readableDatabase, uid)?.withIdentity(packageName, label)
            ?: DataControlRule.default(uid, packageName, label)
    }

    override fun saveRule(rule: DataControlRule) {
        synchronized(lock) {
            helper.writableDatabase.insertWithOnConflict(
                TABLE_APP_RULES,
                null,
                rule.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    override fun clearAutoBlock(uid: Int) {
        synchronized(lock) {
            val values = ContentValues().apply {
                putNull(COL_AUTO_BLOCKED_LOCAL_DATE)
                putNull(COL_AUTO_BLOCKED_REASON)
                putNull(COL_AUTO_BLOCKED_AT_MILLIS)
                put(COL_UPDATED_AT_MILLIS, System.currentTimeMillis())
            }
            helper.writableDatabase.update(
                TABLE_APP_RULES,
                values,
                "$COL_UID = ?",
                arrayOf(uid.toString()),
            )
        }
    }

    private fun saveRuleLocked(rule: DataControlRule) {
        helper.writableDatabase.insertWithOnConflict(
            TABLE_APP_RULES,
            null,
            rule.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun loadPolicySnapshot(localDate: String, selfUid: Int): DataControlPolicySnapshot = synchronized(lock) {
        val db = helper.readableDatabase
        DataControlPolicySnapshot(
            settings = readSettings(),
            rulesByUid = queryRules(db).associateBy { it.uid },
            todayMobileBytesByUid = queryTodayMobileUsage(db, localDate),
            localDate = localDate,
            selfUid = selfUid,
        )
    }

    override fun recordUsage(
        identity: DataControlAppIdentity,
        localDate: String,
        networkType: DataControlNetworkType,
        bytes: Long,
    ) = synchronized(lock) {
        if (bytes <= 0L) return@synchronized

        val db = helper.writableDatabase
        db.runInTransaction {
            val current = queryDailyUsage(db, identity.uid, localDate)
            val nextMobile = current?.mobileBytes.orZero() +
                if (networkType == DataControlNetworkType.Mobile) bytes else 0L
            val nextWifi = current?.wifiBytes.orZero() +
                if (networkType == DataControlNetworkType.Wifi) bytes else 0L

            db.insertWithOnConflict(
                TABLE_DAILY_APP_USAGE,
                null,
                ContentValues().apply {
                    put(COL_LOCAL_DATE, localDate)
                    put(COL_UID, identity.uid)
                    put(COL_PACKAGE_NAME, identity.packageName)
                    put(COL_LABEL, identity.displayLabel)
                    put(COL_MOBILE_BYTES, nextMobile)
                    put(COL_WIFI_BYTES, nextWifi)
                    put(COL_UPDATED_AT_MILLIS, System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    override fun readDailyUsage(uid: Int, localDate: String): DataControlDailyUsage? = synchronized(lock) {
        queryDailyUsage(helper.readableDatabase, uid, localDate)
    }

    override fun markAutoBlocked(
        identity: DataControlAppIdentity,
        localDate: String,
        reason: DataControlBlockReason,
        networkType: DataControlNetworkType,
        usedBytes: Long,
        limitBytes: Long?,
        createdAtMillis: Long,
    ) = synchronized(lock) {
        val db = helper.writableDatabase
        db.runInTransaction {
            val existing = queryRule(db, identity.uid)
            val nextRule = (existing ?: DataControlRule.default(
                uid = identity.uid,
                packageName = identity.packageName,
                label = identity.displayLabel,
            )).copy(
                packageName = identity.packageName,
                label = identity.displayLabel,
                autoBlockedLocalDate = localDate,
                autoBlockedReason = reason,
                autoBlockedAtMillis = createdAtMillis,
            )
            db.insertWithOnConflict(
                TABLE_APP_RULES,
                null,
                nextRule.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.insert(
                TABLE_BLOCK_EVENTS,
                null,
                ContentValues().apply {
                    put(COL_UID, identity.uid)
                    put(COL_PACKAGE_NAME, identity.packageName)
                    put(COL_LABEL, identity.displayLabel)
                    put(COL_LOCAL_DATE, localDate)
                    put(COL_REASON, reason.name)
                    put(COL_NETWORK_TYPE, networkType.name)
                    put(COL_USED_BYTES, usedBytes)
                    if (limitBytes != null) put(COL_LIMIT_BYTES, limitBytes) else putNull(COL_LIMIT_BYTES)
                    put(COL_CREATED_AT_MILLIS, createdAtMillis)
                },
            )
        }
    }

    override fun blockedAppsCount(localDate: String): Int = synchronized(lock) {
        helper.readableDatabase.rawQuery(
            """
            SELECT COUNT(*) FROM app_rules
            WHERE block_mobile_data = 1
               OR block_wifi = 1
               OR auto_blocked_local_date = ?
            """.trimIndent(),
            arrayOf(localDate),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override fun activeRuleCount(): Int = synchronized(lock) {
        helper.readableDatabase.rawQuery(
            """
            SELECT COUNT(*) FROM app_rules
            WHERE block_mobile_data = 1
               OR block_wifi = 1
               OR daily_limit_enabled = 1
               OR auto_blocked_local_date IS NOT NULL
            """.trimIndent(),
            emptyArray<String>(),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override fun recentBlockEvents(uid: Int, limit: Int): List<DataControlBlockEvent> = synchronized(lock) {
        helper.readableDatabase.query(
            TABLE_BLOCK_EVENTS,
            null,
            "$COL_UID = ?",
            arrayOf(uid.toString()),
            null,
            null,
            "$COL_CREATED_AT_MILLIS DESC",
            limit.coerceIn(1, 50).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toBlockEvent())
                }
            }
        }
    }

    private fun readSettingsMap(db: SQLiteDatabase): Map<String, String> {
        return db.query(TABLE_SETTINGS, arrayOf(COL_KEY, COL_VALUE), null, null, null, null, null)
            .use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(COL_KEY), cursor.getString(COL_VALUE))
                    }
                }
            }
    }

    private fun SQLiteDatabase.putSetting(key: String, value: String) {
        insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            ContentValues().apply {
                put(COL_KEY, key)
                put(COL_VALUE, value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun queryRules(db: SQLiteDatabase): List<DataControlRule> {
        return db.query(TABLE_APP_RULES, null, null, null, null, null, null)
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toRule())
                    }
                }
            }
    }

    private fun queryRule(db: SQLiteDatabase, uid: Int): DataControlRule? {
        return db.query(
            TABLE_APP_RULES,
            null,
            "$COL_UID = ?",
            arrayOf(uid.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toRule() else null
        }
    }

    private fun queryTodayMobileUsage(db: SQLiteDatabase, localDate: String): Map<Int, Long> {
        return db.query(
            TABLE_DAILY_APP_USAGE,
            arrayOf(COL_UID, COL_MOBILE_BYTES),
            "$COL_LOCAL_DATE = ?",
            arrayOf(localDate),
            null,
            null,
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getInt(COL_UID), cursor.getLong(COL_MOBILE_BYTES).coerceAtLeast(0L))
                }
            }
        }
    }

    private fun queryDailyUsage(db: SQLiteDatabase, uid: Int, localDate: String): DataControlDailyUsage? {
        return db.query(
            TABLE_DAILY_APP_USAGE,
            null,
            "$COL_UID = ? AND $COL_LOCAL_DATE = ?",
            arrayOf(uid.toString(), localDate),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toDailyUsage() else null
        }
    }

    private fun DataControlRule.toContentValues(): ContentValues {
        return ContentValues().apply {
            put(COL_UID, uid)
            put(COL_PACKAGE_NAME, packageName)
            put(COL_LABEL, label)
            put(COL_BLOCK_MOBILE_DATA, blockMobileData.asInt())
            put(COL_BLOCK_WIFI, blockWifi.asInt())
            put(COL_DAILY_LIMIT_ENABLED, dailyLimitEnabled.asInt())
            put(COL_DAILY_LIMIT_BYTES, dailyLimitBytes)
            if (autoBlockedLocalDate != null) {
                put(COL_AUTO_BLOCKED_LOCAL_DATE, autoBlockedLocalDate)
            } else {
                putNull(COL_AUTO_BLOCKED_LOCAL_DATE)
            }
            if (autoBlockedReason != null) {
                put(COL_AUTO_BLOCKED_REASON, autoBlockedReason.name)
            } else {
                putNull(COL_AUTO_BLOCKED_REASON)
            }
            if (autoBlockedAtMillis != null) {
                put(COL_AUTO_BLOCKED_AT_MILLIS, autoBlockedAtMillis)
            } else {
                putNull(COL_AUTO_BLOCKED_AT_MILLIS)
            }
            put(COL_UPDATED_AT_MILLIS, System.currentTimeMillis())
        }
    }

    private fun Cursor.toRule(): DataControlRule {
        return DataControlRule(
            uid = getInt(COL_UID),
            packageName = getString(COL_PACKAGE_NAME),
            label = getString(COL_LABEL),
            blockMobileData = getInt(COL_BLOCK_MOBILE_DATA) == 1,
            blockWifi = getInt(COL_BLOCK_WIFI) == 1,
            dailyLimitEnabled = getInt(COL_DAILY_LIMIT_ENABLED) == 1,
            dailyLimitBytes = getLong(COL_DAILY_LIMIT_BYTES),
            autoBlockedLocalDate = getNullableString(COL_AUTO_BLOCKED_LOCAL_DATE),
            autoBlockedReason = getNullableString(COL_AUTO_BLOCKED_REASON)?.let { reason ->
                runCatching { DataControlBlockReason.valueOf(reason) }.getOrNull()
            },
            autoBlockedAtMillis = getNullableLong(COL_AUTO_BLOCKED_AT_MILLIS),
        )
    }

    private fun Cursor.toDailyUsage(): DataControlDailyUsage {
        return DataControlDailyUsage(
            uid = getInt(COL_UID),
            packageName = getString(COL_PACKAGE_NAME),
            label = getString(COL_LABEL),
            localDate = getString(COL_LOCAL_DATE),
            mobileBytes = getLong(COL_MOBILE_BYTES),
            wifiBytes = getLong(COL_WIFI_BYTES),
        )
    }

    private fun Cursor.toBlockEvent(): DataControlBlockEvent {
        return DataControlBlockEvent(
            id = getLong(COL_ID),
            uid = getInt(COL_UID),
            packageName = getString(COL_PACKAGE_NAME),
            label = getString(COL_LABEL),
            localDate = getString(COL_LOCAL_DATE),
            reason = runCatching {
                DataControlBlockReason.valueOf(getString(COL_REASON))
            }.getOrDefault(DataControlBlockReason.GlobalLimit),
            networkType = runCatching {
                DataControlNetworkType.valueOf(getString(COL_NETWORK_TYPE))
            }.getOrDefault(DataControlNetworkType.Unknown),
            usedBytes = getLong(COL_USED_BYTES),
            limitBytes = getNullableLong(COL_LIMIT_BYTES),
            createdAtMillis = getLong(COL_CREATED_AT_MILLIS),
        )
    }

    private fun DataControlRule.withIdentity(packageName: String, label: String): DataControlRule {
        return copy(
            packageName = packageName.takeIf { it.isNotBlank() } ?: this.packageName,
            label = label.takeIf { it.isNotBlank() } ?: this.label,
        )
    }

    private fun SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun Map<String, String>.boolean(key: String, default: Boolean): Boolean {
        return get(key)?.toBooleanStrictOrNull() ?: default
    }

    private fun Map<String, String>.long(key: String, default: Long): Long {
        return get(key)?.toLongOrNull()?.coerceAtLeast(0L) ?: default
    }

    private fun Boolean.asText(): String = if (this) "true" else "false"
    private fun Boolean.asInt(): Int = if (this) 1 else 0
    private fun Long?.orZero(): Long = this ?: 0L

    private fun Cursor.getString(columnName: String): String {
        return getString(getColumnIndexOrThrow(columnName))
    }

    private fun Cursor.getNullableString(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getInt(columnName: String): Int {
        return getInt(getColumnIndexOrThrow(columnName))
    }

    private fun Cursor.getLong(columnName: String): Long {
        return getLong(getColumnIndexOrThrow(columnName))
    }

    private fun Cursor.getNullableLong(columnName: String): Long? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getLong(index)
    }

    private companion object {
        const val TABLE_SETTINGS = "data_control_settings"
        const val TABLE_APP_RULES = "app_rules"
        const val TABLE_DAILY_APP_USAGE = "daily_app_usage"
        const val TABLE_BLOCK_EVENTS = "block_events"

        const val COL_ID = "id"
        const val COL_KEY = "key"
        const val COL_VALUE = "value"
        const val COL_UID = "uid"
        const val COL_PACKAGE_NAME = "package_name"
        const val COL_LABEL = "label"
        const val COL_BLOCK_MOBILE_DATA = "block_mobile_data"
        const val COL_BLOCK_WIFI = "block_wifi"
        const val COL_DAILY_LIMIT_ENABLED = "daily_limit_enabled"
        const val COL_DAILY_LIMIT_BYTES = "daily_limit_bytes"
        const val COL_AUTO_BLOCKED_LOCAL_DATE = "auto_blocked_local_date"
        const val COL_AUTO_BLOCKED_REASON = "auto_blocked_reason"
        const val COL_AUTO_BLOCKED_AT_MILLIS = "auto_blocked_at_millis"
        const val COL_LOCAL_DATE = "local_date"
        const val COL_MOBILE_BYTES = "mobile_bytes"
        const val COL_WIFI_BYTES = "wifi_bytes"
        const val COL_REASON = "reason"
        const val COL_NETWORK_TYPE = "network_type"
        const val COL_USED_BYTES = "used_bytes"
        const val COL_LIMIT_BYTES = "limit_bytes"
        const val COL_CREATED_AT_MILLIS = "created_at_millis"
        const val COL_UPDATED_AT_MILLIS = "updated_at_millis"

        const val KEY_ENABLED = "enabled"
        const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"
        const val KEY_GLOBAL_AUTO_BLOCK_ENABLED = "global_auto_block_enabled"
        const val KEY_GLOBAL_AUTO_BLOCK_BYTES = "global_auto_block_bytes"
    }
}
