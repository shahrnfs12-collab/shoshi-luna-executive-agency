package com.shoshiluna.executive.core.business.db

import androidx.room.TypeConverter
import com.shoshiluna.executive.core.business.DurableTransactionStatus
import com.shoshiluna.executive.core.business.ExecutionMode
import com.shoshiluna.executive.core.business.LedgerEntryType

class TreasuryConverters {
    @TypeConverter fun executionModeToString(value: ExecutionMode): String = value.name
    @TypeConverter fun stringToExecutionMode(value: String): ExecutionMode = ExecutionMode.valueOf(value)
    @TypeConverter fun ledgerTypeToString(value: LedgerEntryType): String = value.name
    @TypeConverter fun stringToLedgerType(value: String): LedgerEntryType = LedgerEntryType.valueOf(value)
    @TypeConverter fun statusToString(value: DurableTransactionStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): DurableTransactionStatus = DurableTransactionStatus.valueOf(value)
}
