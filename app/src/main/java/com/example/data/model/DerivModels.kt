package com.example.data.model

data class MarketSymbol(
    val symbol: String,
    val displayName: String,
    val marketDisplay: String,
    val pipSize: Int = 2,
    val isOneSecondIndex: Boolean = false
)

val DefaultMarketSymbols = listOf(
    MarketSymbol("1HZ100V", "Volatility 100 (1s)", "Derived 1s", 2, true),
    MarketSymbol("1HZ75V", "Volatility 75 (1s)", "Derived 1s", 2, true),
    MarketSymbol("1HZ50V", "Volatility 50 (1s)", "Derived 1s", 2, true),
    MarketSymbol("1HZ25V", "Volatility 25 (1s)", "Derived 1s", 2, true),
    MarketSymbol("1HZ10V", "Volatility 10 (1s)", "Derived 1s", 2, true),
    MarketSymbol("R_100", "Volatility 100 Index", "Derived", 2, false),
    MarketSymbol("R_75", "Volatility 75 Index", "Derived", 4, false),
    MarketSymbol("R_50", "Volatility 50 Index", "Derived", 4, false),
    MarketSymbol("frxEURUSD", "EUR/USD", "Forex", 5, false),
    MarketSymbol("cryBTCUSD", "BTC/USD", "Crypto", 2, false)
)

data class TickPoint(
    val epoch: Long,
    val quote: Double,
    val ask: Double? = null,
    val bid: Double? = null,
    val pipSize: Int = 2,
    val receivedAtMs: Long = System.currentTimeMillis()
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val latencyMs: Long) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class AccountInfo(
    val loginId: String,
    val currency: String,
    val balance: Double,
    val isVirtual: Boolean,
    val email: String = "",
    val fullname: String = ""
)

enum class ExecutionMode(val label: String) {
    PAPER_LIVE_FEED("PAPER • LIVE FEED"),
    DERIV_DEMO("DERIV DEMO"),
    DERIV_REAL("DERIV REAL")
}

enum class ContractMode(val title: String, val subtitle: String) {
    MULTIPLIER_SCALP(
        "1s Multiplier Scalp",
        "Opens MULTUP/MULTDOWN & triggers instant API sell every second on Take Profit"
    ),
    RAPID_TICK(
        "Rapid Rise / Fall",
        "Executes ultra-short 1s/2s/5s Rise & Fall contracts on 1s indices"
    )
}

enum class TradeStrategy(val title: String, val description: String) {
    MOMENTUM_BREAKOUT(
        "1s Momentum Pulse",
        "Enters when 3-tick velocity & EMA(5/12) slope align with RSI(7) confirmation"
    ),
    HIGH_CONFLUENCE_SCALP(
        "High-Confluence Filter Scalp",
        "Waits for 3-tick alignment, EMA trend, RSI band & low-spike volatility before entering"
    ),
    MEAN_REVERSION(
        "1s Snap-Back Reversion",
        "Scales counter-trend when short-term RSI(7) stretches past 72 / 28 extremes"
    ),
    CONTINUOUS_SCALP(
        "Continuous Scalp Loop",
        "Re-arms immediately after each take-profit hit using live EMA(5) bias"
    )
}

enum class BotStatus(val label: String) {
    IDLE("IDLE"),
    SCANNING("SCANNING 1S FEED"),
    IN_POSITION("IN POSITION"),
    TAKING_PROFIT("TAKING PROFIT"),
    COOLDOWN("COOLDOWN"),
    CIRCUIT_BREAKER("RISK PAUSED")
}

data class OpenPosition(
    val contractId: String,
    val symbol: String,
    val contractType: String,
    val buyPrice: Double,
    val entrySpot: Double,
    val currentSpot: Double,
    val currentPayout: Double,
    val profitUsd: Double,
    val takeProfitUsd: Double,
    val stopLossUsd: Double,
    val openedAtMs: Long,
    val expiresAtMs: Long? = null,
    val isPaper: Boolean,
    val multiplier: Int = 50
)

data class SignalTelemetry(
    val velocity: Double = 0.0,
    val momentumPct: Double = 0.0,
    val rsi: Double = 50.0,
    val smaFast: Double = 0.0,
    val smaSlow: Double = 0.0,
    val emaFast: Double = 0.0,
    val emaSlow: Double = 0.0,
    val emaFastSeries: List<Double> = emptyList(),
    val smaSlowSeries: List<Double> = emptyList(),
    val maCrossoverState: String = "WARMING UP",
    val directionBias: String = "NEUTRAL",
    val signalReason: String = "Warming up tick buffer...",
    val ticksPerSecond: Double = 0.0
)

enum class StakeSizingMode(val title: String, val subtitle: String) {
    FIXED_STAKE(
        "Fixed Stake (USD)",
        "Uses the exact fixed USD stake, Take Profit, and Stop Loss on every trade"
    ),
    COMPOUND_EQUITY(
        "Dynamic Equity Compounding (%)",
        "Scales stake, Take Profit, and Stop Loss proportionally to live account balance"
    )
}

data class BotConfig(
    val appId: String = "1089",
    val apiToken: String = "",
    val symbol: String = "1HZ100V",
    val contractMode: ContractMode = ContractMode.MULTIPLIER_SCALP,
    val strategy: TradeStrategy = TradeStrategy.MOMENTUM_BREAKOUT,
    val stakeSizingMode: StakeSizingMode = StakeSizingMode.FIXED_STAKE,
    val stakeUsd: Double = 10.0,
    val takeProfitUsd: Double = 0.50,
    val stopLossUsd: Double = 2.00,
    val compoundRiskPercent: Double = 15.0,
    val takeProfitPercentOfStake: Double = 10.0,
    val stopLossPercentOfStake: Double = 30.0,
    val startingCapitalUsd: Double = 10.0,
    val targetGoalUsd: Double = 100000.0,
    val multiplier: Int = 100,
    val tickDurationSeconds: Int = 2,
    val dailyProfitCapUsd: Double = 1000.0,
    val maxConsecutiveLosses: Int = 4,
    val cooldownSeconds: Int = 1,
    val allowRealTrading: Boolean = false,
    val darkTheme: Boolean = true
)

sealed interface DerivApiEvent {
    data class Authorized(val account: AccountInfo) : DerivApiEvent
    data class BalanceUpdated(val balance: Double, val currency: String) : DerivApiEvent
    data class SymbolsLoaded(val symbols: List<MarketSymbol>) : DerivApiEvent
    data class ProposalReceived(
        val proposalId: String,
        val askPrice: Double,
        val payout: Double,
        val spot: Double
    ) : DerivApiEvent
    data class ContractBought(
        val contractId: String,
        val buyPrice: Double,
        val payout: Double,
        val startTimeEpoch: Long,
        val longcode: String
    ) : DerivApiEvent
    data class OpenContractUpdate(
        val contractId: String,
        val currentSpot: Double,
        val entrySpot: Double,
        val buyPrice: Double,
        val bidPrice: Double,
        val profit: Double,
        val isSold: Boolean,
        val status: String
    ) : DerivApiEvent
    data class ContractSold(
        val contractId: String,
        val soldFor: Double,
        val profit: Double?
    ) : DerivApiEvent
    data class ProfitTableLoaded(val items: List<RemoteProfitItem>) : DerivApiEvent
    data class ApiError(val code: String, val message: String, val msgType: String) : DerivApiEvent
}

data class RemoteProfitItem(
    val contractId: String,
    val symbol: String,
    val contractType: String,
    val buyPrice: Double,
    val sellPrice: Double,
    val purchaseTimeMs: Long,
    val sellTimeMs: Long
)
