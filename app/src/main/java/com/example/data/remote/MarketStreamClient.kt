package com.example.data.remote

import com.example.data.model.ConnectionState
import com.example.data.model.DerivApiEvent
import com.example.data.model.TickPoint
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MarketStreamClient {
    val connectionState: StateFlow<ConnectionState>
    val ticks: StateFlow<List<TickPoint>>
    val tickEvents: SharedFlow<TickPoint>
    val apiEvents: SharedFlow<DerivApiEvent>

    fun connect(appId: String = "1089", symbol: String = "1HZ100V", apiToken: String = "")
    fun disconnect()
    fun switchSymbol(symbol: String)
    fun requestProposal(
        symbol: String,
        contractType: String,
        amount: Double,
        currency: String = "USD",
        multiplier: Int? = null,
        duration: Int? = null,
        durationUnit: String? = null
    )
    fun buyMultiplierContract(
        symbol: String,
        contractType: String,
        stakeUsd: Double,
        multiplier: Int,
        takeProfitUsd: Double,
        stopLossUsd: Double,
        currency: String = "USD"
    )
    fun buyRapidTickContract(
        symbol: String,
        contractType: String,
        stakeUsd: Double,
        durationSeconds: Int,
        currency: String = "USD"
    )
    fun sellContract(contractId: String)
    fun fetchProfitTable(limit: Int = 25)
}
