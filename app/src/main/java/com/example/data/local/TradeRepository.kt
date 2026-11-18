package com.example.data.local

import kotlinx.coroutines.flow.Flow

class TradeRepository(private val tradeDao: TradeDao) {
    val allTrades: Flow<List<TradeEntity>> = tradeDao.getAllTrades()

    suspend fun recordTrade(trade: TradeEntity) {
        tradeDao.insertTrade(trade)
    }

    suspend fun importRemoteTrades(trades: List<TradeEntity>): Int {
        var inserted = 0
        for (trade in trades) {
            if (!tradeDao.existsByContractId(trade.contractId)) {
                tradeDao.insertTrade(trade)
                inserted++
            }
        }
        return inserted
    }

    suspend fun clearPaperTrades() {
        tradeDao.clearPaperTrades()
    }

    suspend fun clearAllTrades() {
        tradeDao.clearAllTrades()
    }
}
