package com.shoshiluna.executive.core.business.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FinancialIdempotencyEntity::class,
        FinancialApprovalEntity::class,
        FinancialTransactionEntity::class,
        LedgerEntryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(TreasuryConverters::class)
abstract class TreasuryDatabase : RoomDatabase() {
    abstract fun treasuryDao(): TreasuryDao

    companion object {
        @Volatile private var INSTANCE: TreasuryDatabase? = null

        fun get(context: Context): TreasuryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                TreasuryDatabase::class.java,
                "shoshi_luna_treasury.db"
            ).build().also { INSTANCE = it }
        }
    }
}
