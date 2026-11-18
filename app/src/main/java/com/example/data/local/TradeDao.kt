package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY closedAtMs DESC")
    fun getAllTrades(): Flow<List<TradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<TradeEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM trades WHERE contractId = :contractId)")
    suspend fun existsByContractId(contractId: String): Boolean

    @Query("DELETE FROM trades WHERE executionMode = 'PAPER • LIVE FEED'")
    suspend fun clearPaperTrades()

    @Query("DELETE FROM trades")
    suspend fun clearAllTrades()
}
