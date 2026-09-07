package com.datameter.data.usage

import com.datameter.domain.model.DateRange
import com.datameter.domain.model.NetworkFilter
import com.datameter.domain.model.UsageSnapshot

interface NetworkUsageDataSource {
    suspend fun query(filter: NetworkFilter, range: DateRange): UsageSnapshot
}
