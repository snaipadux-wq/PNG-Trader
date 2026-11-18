package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contractId: String,
    val symbol: String,
    val contractType: String,
    val executionMode: String,
    val stakeUsd: Double,
    val profitUsd: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val openedAtMs: Long,
    val closedAtMs: Long,
    val holdDurationMs: Long,
    val closeReason: String
)
