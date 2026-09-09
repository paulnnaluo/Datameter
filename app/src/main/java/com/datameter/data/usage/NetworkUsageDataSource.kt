package com.datameter.data.usage

import com.datameter.domain.model.DateRange
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.ByteCount
import com.datameter.domain.model.UsageSnapshot

interface NetworkUsageDataSource {
    suspend fun queryTotal(filter: NetworkFilter, range: DateRange): ByteCount {
        return query(filter, range).total
    }

    suspend fun query(filter: NetworkFilter, range: DateRange): UsageSnapshot
}
