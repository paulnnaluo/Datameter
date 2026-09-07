package com.datameter.data.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import com.datameter.domain.model.ByteCount
import com.datameter.domain.model.DateRange
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.TimelineBucket
import com.datameter.domain.model.UsageRow
import com.datameter.domain.model.UsageRowKind
import com.datameter.domain.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidNetworkUsageDataSource(
    context: Context,
    private val labelResolver: PackageLabelResolver,
) : NetworkUsageDataSource {
    private val networkStatsManager = context
        .applicationContext
        .getSystemService(NetworkStatsManager::class.java)

    override suspend fun query(filter: NetworkFilter, range: DateRange): UsageSnapshot {
        return withContext(Dispatchers.IO) {
            when (filter) {
                NetworkFilter.Mobile -> queryNetworkType(ConnectivityManager.TYPE_MOBILE, range)
                NetworkFilter.Wifi -> queryNetworkType(ConnectivityManager.TYPE_WIFI, range)
                NetworkFilter.MobileAndWifi -> combineSnapshots(
                    queryNetworkType(ConnectivityManager.TYPE_MOBILE, range),
                    queryNetworkType(ConnectivityManager.TYPE_WIFI, range),
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun queryNetworkType(networkType: Int, range: DateRange): UsageSnapshot {
        val total = queryDeviceTotal(networkType, range)
        val rowAccumulator = linkedMapOf<RowKey, MutableByteCount>()
        val bucketAccumulator = linkedMapOf<Long, MutableByteCount>()

        val stats = try {
            networkStatsManager.queryDetails(
                networkType,
                null,
                range.startMillis,
                range.endMillis,
            )
        } catch (_: SecurityException) {
            return snapshotWithoutIdentifiedRows(total)
        } catch (_: RuntimeException) {
            return snapshotWithoutIdentifiedRows(total)
        }

        val bucket = NetworkStats.Bucket()
        try {
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val bytes = ByteCount(bucket.rxBytes, bucket.txBytes)
                if (bytes.totalBytes <= 0L) continue

                val key = rowKeyForUid(bucket.uid)
                rowAccumulator.getOrPut(key) { MutableByteCount() }.add(bytes)

                val timelineStart = floorToHour(bucket.startTimeStamp).coerceAtLeast(range.startMillis)
                bucketAccumulator.getOrPut(timelineStart) { MutableByteCount() }.add(bytes)
            }
        } finally {
            stats.close()
        }

        val rows = rowAccumulator.map { (key, bytes) ->
            UsageRow(
                id = key.id,
                label = key.label,
                kind = key.kind,
                rxBytes = bytes.rxBytes,
                txBytes = bytes.txBytes,
            )
        }.toMutableList()

        return UsageSnapshot(
            total = total,
            rows = rows.sortedByDescending { it.totalBytes },
            timelineBuckets = bucketAccumulator.toTimelineBuckets(range),
        )
    }

    @Suppress("DEPRECATION")
    private fun queryDeviceTotal(networkType: Int, range: DateRange): ByteCount {
        return try {
            val totalBucket = networkStatsManager.querySummaryForDevice(
                networkType,
                null,
                range.startMillis,
                range.endMillis,
            )
            ByteCount(totalBucket.rxBytes, totalBucket.txBytes)
        } catch (_: SecurityException) {
            ByteCount.Zero
        } catch (_: RuntimeException) {
            ByteCount.Zero
        }
    }

    private fun rowKeyForUid(uid: Int): RowKey {
        return when {
            uid == NetworkStats.Bucket.UID_TETHERING -> RowKey(
                id = "hotspot",
                label = "Hotspot / Tethering",
                kind = UsageRowKind.Hotspot,
            )

            uid == NetworkStats.Bucket.UID_REMOVED -> RowKey(
                id = "removed_apps",
                label = "Removed apps",
                kind = UsageRowKind.RemovedApps,
            )

            uid == NetworkStats.Bucket.UID_ALL ||
                uid == Process.SYSTEM_UID ||
                uid in 0 until Process.FIRST_APPLICATION_UID -> RowKey(
                id = "system",
                label = "System",
                kind = UsageRowKind.System,
            )

            else -> {
                val identity = labelResolver.resolve(uid)
                RowKey(
                    id = "app:${identity.id}",
                    label = identity.label,
                    kind = UsageRowKind.App,
                )
            }
        }
    }

    private fun snapshotWithoutIdentifiedRows(total: ByteCount): UsageSnapshot {
        return UsageSnapshot(
            total = total,
            rows = emptyList(),
            timelineBuckets = emptyList(),
        )
    }

    private fun combineSnapshots(first: UsageSnapshot, second: UsageSnapshot): UsageSnapshot {
        val rows = linkedMapOf<String, UsageRow>()
        (first.rows + second.rows).forEach { row ->
            rows[row.id] = rows[row.id]?.plus(row) ?: row
        }

        val buckets = linkedMapOf<Long, MutableByteCount>()
        (first.timelineBuckets + second.timelineBuckets).forEach { bucket ->
            buckets.getOrPut(bucket.startMillis) { MutableByteCount() }
                .add(ByteCount(bucket.rxBytes, bucket.txBytes))
        }

        val combinedRange = DateRange(
            startMillis = minOf(
                first.timelineBuckets.firstOrNull()?.startMillis ?: Long.MAX_VALUE,
                second.timelineBuckets.firstOrNull()?.startMillis ?: Long.MAX_VALUE,
            ).takeIf { it != Long.MAX_VALUE } ?: 0L,
            endMillis = maxOf(
                first.timelineBuckets.lastOrNull()?.endMillis ?: 0L,
                second.timelineBuckets.lastOrNull()?.endMillis ?: 0L,
            ),
        )

        return UsageSnapshot(
            total = first.total + second.total,
            rows = rows.values.sortedByDescending { it.totalBytes },
            timelineBuckets = buckets.toTimelineBuckets(combinedRange),
        )
    }

    private fun Map<Long, MutableByteCount>.toTimelineBuckets(range: DateRange): List<TimelineBucket> {
        return entries
            .sortedBy { it.key }
            .map { (startMillis, bytes) ->
                TimelineBucket(
                    startMillis = startMillis,
                    endMillis = minOf(startMillis + ONE_HOUR_MILLIS, range.endMillis),
                    rxBytes = bytes.rxBytes,
                    txBytes = bytes.txBytes,
                )
            }
    }

    private fun floorToHour(millis: Long): Long {
        return millis - (millis % ONE_HOUR_MILLIS)
    }

    private data class RowKey(
        val id: String,
        val label: String,
        val kind: UsageRowKind,
    )

    private class MutableByteCount(
        var rxBytes: Long = 0L,
        var txBytes: Long = 0L,
    ) {
        fun add(bytes: ByteCount) {
            rxBytes += bytes.rxBytes.coerceAtLeast(0L)
            txBytes += bytes.txBytes.coerceAtLeast(0L)
        }
    }

    private companion object {
        const val ONE_HOUR_MILLIS = 60L * 60L * 1000L
    }
}
