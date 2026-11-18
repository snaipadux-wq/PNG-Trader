package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TradeEntity
import com.example.data.local.TradeRepository
import com.example.data.model.AccountInfo
import com.example.data.model.BotConfig
import com.example.data.model.BotStatus
import com.example.data.model.ConnectionState
import com.example.data.model.ContractMode
import com.example.data.model.DefaultMarketSymbols
import com.example.data.model.DerivApiEvent
import com.example.data.model.ExecutionMode
import com.example.data.model.MarketSymbol
import com.example.data.model.OpenPosition
import com.example.data.model.SignalTelemetry
import com.example.data.model.StakeSizingMode
import com.example.data.model.TickPoint
import com.example.data.remote.DerivWebSocketClient
import com.example.data.remote.MarketStreamClient
import com.example.domain.EffectiveTradeParams
import com.example.domain.ExitAction
import com.example.domain.ScalpingEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

data class BotLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val message: String,
    val isPositive: Boolean? = null
)

data class MainUiState(
    val config: BotConfig = BotConfig(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val accountInfo: AccountInfo? = null,
    val executionMode: ExecutionMode = ExecutionMode.PAPER_LIVE_FEED,
    val paperBalanceUsd: Double = 10000.0,
    val availableSymbols: List<MarketSymbol> = DefaultMarketSymbols,
    val ticks: List<TickPoint> = emptyList(),
    val telemetry: SignalTelemetry = SignalTelemetry(),
    val botStatus: BotStatus = BotStatus.IDLE,
    val openPosition: OpenPosition? = null,
    val pendingOrderInFlight: Boolean = false,
    val trades: List<TradeEntity> = emptyList(),
    val sessionNetProfitUsd: Double = 0.0,
    val sessionTradesCount: Int = 0,
    val consecutiveLosses: Int = 0,
    val cooldownRemainingSec: Int = 0,
    val lastProposalPayout: Double? = null,
    val statusBanner: String? = null,
    val activityLog: List<BotLogEntry> = emptyList(),
    val effectiveParams: EffectiveTradeParams = EffectiveTradeParams(10.0, 0.50, 2.00)
)

class MainViewModel(
    application: Application,
    private val wsClient: MarketStreamClient,
    private val settingsStore: SettingsDataStore,
    private val tradeRepo: TradeRepository,
    private val engine: ScalpingEngine = ScalpingEngine()
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        wsClient = DerivWebSocketClient(),
        settingsStore = SettingsDataStore(application),
        tradeRepo = TradeRepository(AppDatabase.getInstance(application).tradeDao()),
        engine = ScalpingEngine()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lastPendingDirection: String = "MULTUP"
    private var lastPendingEntrySpot: Double = 0.0
    private var lastPendingOpenedAtMs: Long = 0L
    private var sellInFlightContractId: String? = null
    private var cooldownUntilMs: Long = 0L
    private var hasInitializedConnection = false

    init {
        observePersistence()
        observeWebSocket()
        startOneSecondBotHeartbeat()
    }

    private fun observePersistence() {
        viewModelScope.launch {
            settingsStore.configFlow.collect { config ->
                _uiState.update { state ->
                    val bal = state.accountInfo?.balance ?: state.paperBalanceUsd
                    state.copy(
                        config = config,
                        executionMode = resolveExecutionMode(state.accountInfo, config),
                        effectiveParams = engine.computeEffectiveTradeParams(config, bal)
                    )
                }
                if (!hasInitializedConnection) {
                    hasInitializedConnection = true
                    wsClient.connect(
                        appId = config.appId,
                        symbol = config.symbol,
                        apiToken = config.apiToken
                    )
                    addLog("Connecting to Deriv real-time WebSocket (app_id=${config.appId})")
                }
            }
        }

        viewModelScope.launch {
            settingsStore.paperBalanceFlow.collect { bal ->
                _uiState.update { state ->
                    val activeBal = state.accountInfo?.balance ?: bal
                    state.copy(
                        paperBalanceUsd = bal,
                        effectiveParams = engine.computeEffectiveTradeParams(state.config, activeBal)
                    )
                }
            }
        }

        viewModelScope.launch {
            tradeRepo.allTrades.collect { trades ->
                _uiState.update { it.copy(trades = trades) }
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            wsClient.connectionState.collect { conn ->
                _uiState.update { it.copy(connectionState = conn) }
            }
        }

        viewModelScope.launch {
            wsClient.ticks.collect { ticks ->
                val telemetry = engine.analyzeTicks(ticks)
                _uiState.update { it.copy(ticks = ticks, telemetry = telemetry) }
            }
        }

        viewModelScope.launch {
            wsClient.tickEvents.collect { tick ->
                onTickReceived(tick)
            }
        }

        viewModelScope.launch {
            wsClient.apiEvents.collect { event ->
                handleApiEvent(event)
            }
        }
    }

    private fun startOneSecondBotHeartbeat() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val state = _uiState.value

                if (state.botStatus == BotStatus.COOLDOWN) {
                    val remaining = ((cooldownUntilMs - now + 999L) / 1000L).toInt().coerceAtLeast(0)
                    if (remaining <= 0) {
                        _uiState.update {
                            it.copy(
                                botStatus = BotStatus.SCANNING,
                                cooldownRemainingSec = 0
                            )
                        }
                    } else {
                        _uiState.update { it.copy(cooldownRemainingSec = remaining) }
                    }
                }

                // Also evaluate rapid-tick expiry every second even if tick pauses briefly
                val pos = state.openPosition
                if (pos != null && pos.isPaper) {
                    val action = engine.evaluateOpenPosition(pos, now)
                    if (action == ExitAction.RAPID_SETTLE) {
                        val latestQuote = state.ticks.lastOrNull()?.quote ?: pos.currentSpot
                        closePaperPosition(pos, latestQuote, "RAPID_SETTLE")
                    }
                }
            }
        }
    }

    private fun onTickReceived(tick: TickPoint) {
        val state = _uiState.value
        val now = System.currentTimeMillis()

        // 1. Update open paper position every single 1s tick & check Take-Profit / Stop-Loss
        val pos = state.openPosition
        if (pos != null && pos.isPaper) {
            val profitUsd = if (pos.contractType == "MULTUP" || pos.contractType == "MULTDOWN") {
                engine.calculatePaperMultiplierProfit(pos, tick.quote)
            } else {
                val win = if (pos.contractType == "CALL") tick.quote > pos.entrySpot else tick.quote < pos.entrySpot
                if (win) pos.buyPrice * 0.95 else -pos.buyPrice
            }
            val updatedPos = pos.copy(
                currentSpot = tick.quote,
                currentPayout = (pos.buyPrice + profitUsd).coerceAtLeast(0.0),
                profitUsd = profitUsd
            )
            _uiState.update { it.copy(openPosition = updatedPos) }

            val action = engine.evaluateOpenPosition(updatedPos, now)
            when (action) {
                ExitAction.TAKE_PROFIT -> closePaperPosition(updatedPos, tick.quote, "TAKE_PROFIT")
                ExitAction.STOP_LOSS -> closePaperPosition(updatedPos, tick.quote, "STOP_LOSS")
                ExitAction.RAPID_SETTLE -> closePaperPosition(updatedPos, tick.quote, "CONTRACT_SETTLED")
                ExitAction.HOLD -> Unit
            }
            return
        }

        // 2. If bot is actively scanning & no order in flight, check entry signal
        if (state.botStatus == BotStatus.SCANNING && pos == null && !state.pendingOrderInFlight) {
            val activeBalance = state.accountInfo?.balance ?: state.paperBalanceUsd
            if (state.config.stakeSizingMode == StakeSizingMode.COMPOUND_EQUITY &&
                activeBalance >= state.config.targetGoalUsd
            ) {
                _uiState.update {
                    it.copy(
                        botStatus = BotStatus.CIRCUIT_BREAKER,
                        statusBanner = "Target milestone $${"%,.2f".format(state.config.targetGoalUsd)} reached!"
                    )
                }
                addLog("Milestone goal $${"%,.2f".format(state.config.targetGoalUsd)} reached — bot paused", isPositive = true)
                return
            }

            if (activeBalance < 0.35) {
                _uiState.update {
                    it.copy(
                        botStatus = BotStatus.CIRCUIT_BREAKER,
                        statusBanner = "Balance below $0.35 Deriv minimum stake."
                    )
                }
                addLog("Balance below $0.35 minimum stake — bot paused", isPositive = false)
                return
            }

            if (state.sessionNetProfitUsd >= state.config.dailyProfitCapUsd) {
                _uiState.update {
                    it.copy(
                        botStatus = BotStatus.CIRCUIT_BREAKER,
                        statusBanner = "Daily profit cap ($${"%.2f".format(state.config.dailyProfitCapUsd)}) reached!"
                    )
                }
                addLog("Daily profit cap reached — bot paused automatically", isPositive = true)
                return
            }

            if (state.consecutiveLosses >= state.config.maxConsecutiveLosses) {
                _uiState.update {
                    it.copy(
                        botStatus = BotStatus.CIRCUIT_BREAKER,
                        statusBanner = "Stopped after ${state.consecutiveLosses} consecutive losses"
                    )
                }
                addLog("Max consecutive loss guard triggered — bot paused", isPositive = false)
                return
            }

            val signal = engine.evaluateEntry(state.ticks, state.config, state.telemetry)
            if (signal != null) {
                openPosition(signal.contractType, signal.reason, tick.quote)
            }
        }
    }

    private fun openPosition(contractType: String, reason: String, currentSpot: Double) {
        val state = _uiState.value
        val config = state.config
        val account = state.accountInfo
        val activeBalance = account?.balance ?: state.paperBalanceUsd
        val params = engine.computeEffectiveTradeParams(config, activeBalance)

        val canTradeDerivAccount = account != null &&
            (account.isVirtual || config.allowRealTrading)

        if (canTradeDerivAccount && account != null) {
            _uiState.update { it.copy(pendingOrderInFlight = true, effectiveParams = params) }
            lastPendingDirection = contractType
            lastPendingEntrySpot = currentSpot
            lastPendingOpenedAtMs = System.currentTimeMillis()
            addLog("Signal: $reason -> Live Deriv buy ($contractType $${"%.2f".format(params.stakeUsd)})")

            if (config.contractMode == ContractMode.MULTIPLIER_SCALP) {
                wsClient.buyMultiplierContract(
                    symbol = config.symbol,
                    contractType = contractType,
                    stakeUsd = params.stakeUsd,
                    multiplier = config.multiplier,
                    takeProfitUsd = params.takeProfitUsd,
                    stopLossUsd = params.stopLossUsd,
                    currency = account.currency
                )
            } else {
                wsClient.buyRapidTickContract(
                    symbol = config.symbol,
                    contractType = contractType,
                    stakeUsd = params.stakeUsd,
                    durationSeconds = config.tickDurationSeconds,
                    currency = account.currency
                )
            }
        } else {
            if (account != null && !account.isVirtual && !config.allowRealTrading) {
                _uiState.update {
                    it.copy(
                        botStatus = BotStatus.IDLE,
                        statusBanner = "Real account safety lock is ON. Enable it in Strategy tab or clear token for Paper Live Feed."
                    )
                }
                addLog("Blocked real-money order because Real Trading Safety Lock is enabled", isPositive = false)
                return
            }

            // Paper execution against 100% real-time Deriv WebSocket tick feed
            if (state.paperBalanceUsd < params.stakeUsd || params.stakeUsd < 0.35) {
                _uiState.update {
                    it.copy(
                        botStatus = BotStatus.IDLE,
                        statusBanner = "Insufficient Paper balance. Reset Paper balance in Strategy tab."
                    )
                }
                return
            }

            val now = System.currentTimeMillis()
            val expiresAt = if (config.contractMode == ContractMode.RAPID_TICK) {
                now + (config.tickDurationSeconds * 1000L)
            } else {
                null
            }
            val newPosition = OpenPosition(
                contractId = "PAPER-${now % 1000000}",
                symbol = config.symbol,
                contractType = contractType,
                buyPrice = params.stakeUsd,
                entrySpot = currentSpot,
                currentSpot = currentSpot,
                currentPayout = params.stakeUsd,
                profitUsd = 0.0,
                takeProfitUsd = params.takeProfitUsd,
                stopLossUsd = params.stopLossUsd,
                openedAtMs = now,
                expiresAtMs = expiresAt,
                isPaper = true,
                multiplier = config.multiplier
            )
            _uiState.update {
                it.copy(
                    botStatus = BotStatus.IN_POSITION,
                    openPosition = newPosition,
                    effectiveParams = params,
                    statusBanner = null
                )
            }
            addLog("Opened PAPER $contractType $${"%.2f".format(params.stakeUsd)} on ${config.symbol} @ ${"%.2f".format(currentSpot)}")
        }
    }

    private fun closePaperPosition(position: OpenPosition, exitSpot: Double, reason: String) {
        val now = System.currentTimeMillis()
        val finalProfit = if (position.contractType == "CALL" || position.contractType == "PUT") {
            val win = if (position.contractType == "CALL") exitSpot > position.entrySpot else exitSpot < position.entrySpot
            if (win) position.buyPrice * 0.95 else -position.buyPrice
        } else {
            position.profitUsd
        }

        val holdMs = (now - position.openedAtMs).coerceAtLeast(100L)
        viewModelScope.launch {
            val currentBal = settingsStore.paperBalanceFlow.first()
            settingsStore.updatePaperBalance((currentBal + finalProfit).coerceAtLeast(0.0))

            tradeRepo.recordTrade(
                TradeEntity(
                    contractId = position.contractId,
                    symbol = position.symbol,
                    contractType = position.contractType,
                    executionMode = ExecutionMode.PAPER_LIVE_FEED.label,
                    stakeUsd = position.buyPrice,
                    profitUsd = finalProfit,
                    entryPrice = position.entrySpot,
                    exitPrice = exitSpot,
                    openedAtMs = position.openedAtMs,
                    closedAtMs = now,
                    holdDurationMs = holdMs,
                    closeReason = reason
                )
            )
        }

        finalizeTradeState(
            profitUsd = finalProfit,
            reason = reason,
            holdMs = holdMs,
            symbol = position.symbol
        )
    }

    private fun handleApiEvent(event: DerivApiEvent) {
        when (event) {
            is DerivApiEvent.Authorized -> {
                _uiState.update { state ->
                    val mode = resolveExecutionMode(event.account, state.config)
                    state.copy(
                        accountInfo = event.account,
                        executionMode = mode,
                        statusBanner = "Authorized ${event.account.loginId} (${if (event.account.isVirtual) "Demo" else "Real"})"
                    )
                }
                addLog("Authorized Deriv account ${event.account.loginId} • Balance ${"%.2f".format(event.account.balance)} ${event.account.currency}", isPositive = true)
            }

            is DerivApiEvent.BalanceUpdated -> {
                _uiState.update { state ->
                    val updatedAcc = state.accountInfo?.copy(
                        balance = event.balance,
                        currency = event.currency
                    )
                    state.copy(
                        accountInfo = updatedAcc,
                        effectiveParams = engine.computeEffectiveTradeParams(state.config, event.balance)
                    )
                }
            }

            is DerivApiEvent.SymbolsLoaded -> {
                _uiState.update { it.copy(availableSymbols = event.symbols) }
            }

            is DerivApiEvent.ProposalReceived -> {
                _uiState.update { it.copy(lastProposalPayout = event.payout) }
            }

            is DerivApiEvent.ContractBought -> {
                val config = _uiState.value.config
                val params = _uiState.value.effectiveParams
                val now = System.currentTimeMillis()
                val pos = OpenPosition(
                    contractId = event.contractId,
                    symbol = config.symbol,
                    contractType = lastPendingDirection,
                    buyPrice = event.buyPrice,
                    entrySpot = lastPendingEntrySpot,
                    currentSpot = lastPendingEntrySpot,
                    currentPayout = event.buyPrice,
                    profitUsd = 0.0,
                    takeProfitUsd = params.takeProfitUsd,
                    stopLossUsd = params.stopLossUsd,
                    openedAtMs = now,
                    expiresAtMs = if (config.contractMode == ContractMode.RAPID_TICK) {
                        now + (config.tickDurationSeconds * 1000L)
                    } else null,
                    isPaper = false,
                    multiplier = config.multiplier
                )
                sellInFlightContractId = null
                _uiState.update {
                    it.copy(
                        pendingOrderInFlight = false,
                        openPosition = pos,
                        botStatus = BotStatus.IN_POSITION
                    )
                }
                addLog("Live Deriv contract #${event.contractId} opened ($${"%.2f".format(event.buyPrice)})")
            }

            is DerivApiEvent.OpenContractUpdate -> {
                val currentPos = _uiState.value.openPosition ?: return
                if (currentPos.isPaper || currentPos.contractId != event.contractId) return

                val updated = currentPos.copy(
                    entrySpot = if (event.entrySpot > 0) event.entrySpot else currentPos.entrySpot,
                    currentSpot = if (event.currentSpot > 0) event.currentSpot else currentPos.currentSpot,
                    currentPayout = event.bidPrice,
                    profitUsd = event.profit
                )

                if (event.isSold) {
                    val now = System.currentTimeMillis()
                    val holdMs = (now - updated.openedAtMs).coerceAtLeast(100L)
                    val reason = when {
                        event.profit >= updated.takeProfitUsd -> "TAKE_PROFIT"
                        event.profit <= -abs(updated.stopLossUsd) -> "STOP_LOSS"
                        else -> "CONTRACT_SETTLED"
                    }
                    viewModelScope.launch {
                        tradeRepo.recordTrade(
                            TradeEntity(
                                contractId = updated.contractId,
                                symbol = updated.symbol,
                                contractType = updated.contractType,
                                executionMode = _uiState.value.executionMode.label,
                                stakeUsd = updated.buyPrice,
                                profitUsd = event.profit,
                                entryPrice = updated.entrySpot,
                                exitPrice = updated.currentSpot,
                                openedAtMs = updated.openedAtMs,
                                closedAtMs = now,
                                holdDurationMs = holdMs,
                                closeReason = reason
                            )
                        )
                    }
                    sellInFlightContractId = null
                    finalizeTradeState(event.profit, reason, holdMs, updated.symbol)
                } else {
                    _uiState.update { it.copy(openPosition = updated) }
                    val action = engine.evaluateOpenPosition(updated, System.currentTimeMillis())
                    if ((action == ExitAction.TAKE_PROFIT || action == ExitAction.STOP_LOSS) &&
                        sellInFlightContractId != updated.contractId
                    ) {
                        sellInFlightContractId = updated.contractId
                        _uiState.update { it.copy(botStatus = BotStatus.TAKING_PROFIT) }
                        wsClient.sellContract(updated.contractId)
                        addLog("Triggering instant API sell on #${updated.contractId} (P&L $${"%.2f".format(updated.profitUsd)})")
                    }
                }
            }

            is DerivApiEvent.ContractSold -> {
                addLog("Deriv contract #${event.contractId} sold for $${"%.2f".format(event.soldFor)}", isPositive = true)
            }

            is DerivApiEvent.ProfitTableLoaded -> {
                viewModelScope.launch {
                    val mode = _uiState.value.executionMode.label
                    val entities = event.items.map { item ->
                        val profit = item.sellPrice - item.buyPrice
                        TradeEntity(
                            contractId = item.contractId,
                            symbol = item.symbol,
                            contractType = item.contractType,
                            executionMode = mode,
                            stakeUsd = item.buyPrice,
                            profitUsd = profit,
                            entryPrice = 0.0,
                            exitPrice = 0.0,
                            openedAtMs = item.purchaseTimeMs,
                            closedAtMs = item.sellTimeMs,
                            holdDurationMs = (item.sellTimeMs - item.purchaseTimeMs).coerceAtLeast(1000L),
                            closeReason = if (profit >= 0) "TAKE_PROFIT" else "STOP_LOSS"
                        )
                    }
                    val count = tradeRepo.importRemoteTrades(entities)
                    _uiState.update {
                        it.copy(statusBanner = "Synced $count new trades from Deriv Profit Table")
                    }
                    addLog("Synced $count trades from Deriv profit_table", isPositive = true)
                }
            }

            is DerivApiEvent.ApiError -> {
                _uiState.update {
                    it.copy(
                        pendingOrderInFlight = false,
                        statusBanner = "Deriv API [${event.code}]: ${event.message}"
                    )
                }
                addLog("Deriv API error (${event.msgType}): ${event.message}", isPositive = false)
            }
        }
    }

    private fun finalizeTradeState(
        profitUsd: Double,
        reason: String,
        holdMs: Long,
        symbol: String
    ) {
        val state = _uiState.value
        val wasRunning = state.botStatus != BotStatus.IDLE && state.botStatus != BotStatus.CIRCUIT_BREAKER
        val newSessionProfit = state.sessionNetProfitUsd + profitUsd
        val newLosses = if (profitUsd < 0) state.consecutiveLosses + 1 else 0
        val cooldownSec = state.config.cooldownSeconds.coerceAtLeast(0)

        val nextStatus = when {
            !wasRunning -> BotStatus.IDLE
            newSessionProfit >= state.config.dailyProfitCapUsd -> BotStatus.CIRCUIT_BREAKER
            newLosses >= state.config.maxConsecutiveLosses -> BotStatus.CIRCUIT_BREAKER
            cooldownSec > 0 -> {
                cooldownUntilMs = System.currentTimeMillis() + (cooldownSec * 1000L)
                BotStatus.COOLDOWN
            }
            else -> BotStatus.SCANNING
        }

        _uiState.update {
            it.copy(
                openPosition = null,
                botStatus = nextStatus,
                sessionNetProfitUsd = newSessionProfit,
                sessionTradesCount = it.sessionTradesCount + 1,
                consecutiveLosses = newLosses,
                cooldownRemainingSec = if (nextStatus == BotStatus.COOLDOWN) cooldownSec else 0
            )
        }

        val sign = if (profitUsd >= 0) "+" else ""
        val sec = "%.1fs".format(holdMs / 1000.0)
        addLog(
            "$reason on $symbol: $sign$${"%.2f".format(profitUsd)} ($sec)",
            isPositive = profitUsd >= 0
        )
    }

    fun toggleBot() {
        val state = _uiState.value
        if (state.botStatus == BotStatus.IDLE || state.botStatus == BotStatus.CIRCUIT_BREAKER) {
            if (state.connectionState !is ConnectionState.Connected) {
                wsClient.connect(state.config.appId, state.config.symbol, state.config.apiToken)
            }
            _uiState.update {
                it.copy(
                    botStatus = BotStatus.SCANNING,
                    consecutiveLosses = 0,
                    statusBanner = null
                )
            }
            addLog("1s Take-Profit Bot STARTED on ${state.config.symbol}", isPositive = true)
        } else {
            _uiState.update {
                it.copy(
                    botStatus = BotStatus.IDLE,
                    cooldownRemainingSec = 0
                )
            }
            addLog("Bot STOPPED by operator")
        }
    }

    fun triggerManualTakeProfitNow() {
        val pos = _uiState.value.openPosition ?: return
        if (pos.isPaper) {
            val spot = _uiState.value.ticks.lastOrNull()?.quote ?: pos.currentSpot
            closePaperPosition(pos, spot, "MANUAL_SELL")
        } else if (sellInFlightContractId != pos.contractId) {
            sellInFlightContractId = pos.contractId
            _uiState.update { it.copy(botStatus = BotStatus.TAKING_PROFIT) }
            wsClient.sellContract(pos.contractId)
            addLog("Manual Take Profit sent for #${pos.contractId}")
        }
    }

    fun switchSymbol(symbol: String) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(symbol = symbol)
            settingsStore.saveConfig(updated)
            wsClient.switchSymbol(symbol)
            addLog("Switched live feed to $symbol")
        }
    }

    fun updateConfig(newConfig: BotConfig) {
        viewModelScope.launch {
            settingsStore.saveConfig(newConfig)
        }
    }

    fun connectAndAuthorize(appId: String, apiToken: String) {
        viewModelScope.launch {
            val cleanAppId = appId.trim().ifBlank { "1089" }
            val cleanToken = apiToken.trim()
            val updated = _uiState.value.config.copy(appId = cleanAppId, apiToken = cleanToken)
            settingsStore.saveConfig(updated)
            if (cleanToken.isBlank()) {
                _uiState.update {
                    it.copy(
                        accountInfo = null,
                        executionMode = ExecutionMode.PAPER_LIVE_FEED,
                        statusBanner = "Connected in Paper • Live Feed mode"
                    )
                }
                wsClient.connect(cleanAppId, updated.symbol, "")
            } else {
                wsClient.connect(cleanAppId, updated.symbol, cleanToken)
            }
        }
    }

    fun disconnectToken() {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(apiToken = "", allowRealTrading = false)
            settingsStore.saveConfig(updated)
            _uiState.update {
                it.copy(
                    accountInfo = null,
                    executionMode = ExecutionMode.PAPER_LIVE_FEED,
                    statusBanner = "Token cleared — switched to Paper • Live Feed mode"
                )
            }
            wsClient.connect(updated.appId, updated.symbol, "")
            addLog("API token cleared; running on public Deriv live tick feed")
        }
    }

    fun requestLiveProposalCheck() {
        val state = _uiState.value
        val config = state.config
        val contractType = if (config.contractMode == ContractMode.MULTIPLIER_SCALP) "MULTUP" else "CALL"
        wsClient.requestProposal(
            symbol = config.symbol,
            contractType = contractType,
            amount = config.stakeUsd,
            currency = state.accountInfo?.currency ?: "USD",
            multiplier = if (config.contractMode == ContractMode.MULTIPLIER_SCALP) config.multiplier else null,
            duration = if (config.contractMode == ContractMode.RAPID_TICK) config.tickDurationSeconds else null,
            durationUnit = if (config.contractMode == ContractMode.RAPID_TICK) "s" else null
        )
    }

    fun syncRemoteProfitTable() {
        if (_uiState.value.accountInfo == null) {
            _uiState.update { it.copy(statusBanner = "Authorize a Deriv API token in Strategy tab to sync account Profit Table") }
            return
        }
        wsClient.fetchProfitTable(25)
    }

    fun resetPaperBalance(amountUsd: Double = 10000.0) {
        viewModelScope.launch {
            settingsStore.resetPaperBalance(amountUsd)
            _uiState.update {
                it.copy(
                    sessionNetProfitUsd = 0.0,
                    sessionTradesCount = 0,
                    consecutiveLosses = 0,
                    statusBanner = "Paper balance reset to ${"$%,.2f".format(amountUsd)}"
                )
            }
            addLog("Paper account balance reset to ${"$%,.2f".format(amountUsd)}")
        }
    }

    fun applyTenDollarCompoundPreset() {
        viewModelScope.launch {
            val current = _uiState.value.config
            val updated = current.copy(
                stakeSizingMode = StakeSizingMode.COMPOUND_EQUITY,
                startingCapitalUsd = 10.0,
                targetGoalUsd = 100000.0,
                compoundRiskPercent = 15.0,
                takeProfitPercentOfStake = 10.0,
                stopLossPercentOfStake = 30.0,
                dailyProfitCapUsd = 100000.0
            )
            settingsStore.saveConfig(updated)
            if (_uiState.value.accountInfo == null) {
                settingsStore.resetPaperBalance(10.0)
            }
            _uiState.update {
                it.copy(
                    sessionNetProfitUsd = 0.0,
                    sessionTradesCount = 0,
                    consecutiveLosses = 0,
                    statusBanner = "Enabled $10 -> $100,000 Dynamic Compounding (15% equity/trade). High drawdown risk — test in Paper first."
                )
            }
            addLog("Applied $10 Dynamic Equity Compounding preset (Target $100,000)")
        }
    }

    fun clearPaperTrades() {
        viewModelScope.launch {
            tradeRepo.clearPaperTrades()
            _uiState.update { it.copy(statusBanner = "Cleared local Paper trades") }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    fun toggleTheme() {
        val current = _uiState.value.config
        updateConfig(current.copy(darkTheme = !current.darkTheme))
    }

    private fun resolveExecutionMode(account: AccountInfo?, config: BotConfig): ExecutionMode {
        return when {
            account == null -> ExecutionMode.PAPER_LIVE_FEED
            account.isVirtual -> ExecutionMode.DERIV_DEMO
            config.allowRealTrading -> ExecutionMode.DERIV_REAL
            else -> ExecutionMode.PAPER_LIVE_FEED
        }
    }

    private fun addLog(message: String, isPositive: Boolean? = null) {
        _uiState.update { state ->
            val updated = (listOf(BotLogEntry(message = message, isPositive = isPositive)) + state.activityLog)
                .take(25)
            state.copy(activityLog = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsClient.disconnect()
    }
}
