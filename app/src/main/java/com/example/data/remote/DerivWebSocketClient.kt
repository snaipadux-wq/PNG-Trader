package com.example.data.remote

import com.example.data.model.AccountInfo
import com.example.data.model.ConnectionState
import com.example.data.model.DerivApiEvent
import com.example.data.model.MarketSymbol
import com.example.data.model.RemoteProfitItem
import com.example.data.model.TickPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DerivWebSocketClient : MarketStreamClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var currentAppId: String = "1089"
    private var currentSymbol: String = "1HZ100V"
    private var pendingToken: String = ""
    private var manualDisconnect = false
    private var reconnectAttempt = 0
    private var lastPingSentMs = 0L
    private val reqCounter = AtomicInteger(1)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _ticks = MutableStateFlow<List<TickPoint>>(emptyList())
    override val ticks: StateFlow<List<TickPoint>> = _ticks.asStateFlow()

    private val _tickEvents = MutableSharedFlow<TickPoint>(extraBufferCapacity = 64)
    override val tickEvents: SharedFlow<TickPoint> = _tickEvents.asSharedFlow()

    private val _apiEvents = MutableSharedFlow<DerivApiEvent>(extraBufferCapacity = 64)
    override val apiEvents: SharedFlow<DerivApiEvent> = _apiEvents.asSharedFlow()

    override fun connect(appId: String, symbol: String, apiToken: String) {
        manualDisconnect = false
        currentAppId = appId.trim().ifBlank { "1089" }
        currentSymbol = symbol.trim().ifBlank { "1HZ100V" }
        pendingToken = apiToken.trim()

        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        _connectionState.value = ConnectionState.Connecting
        val url = "wss://ws.derivws.com/websockets/v3?app_id=$currentAppId"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.Connected(latencyMs = 0L)
                startHeartbeat()
                sendPing()
                fetchActiveSymbols()
                subscribeTicks(currentSymbol)
                if (pendingToken.isNotBlank()) {
                    authorize(pendingToken)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "WebSocket connection error")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!manualDisconnect) {
                    _connectionState.value = ConnectionState.Disconnected
                    scheduleReconnect()
                }
            }
        })
    }

    override fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun switchSymbol(symbol: String) {
        if (symbol == currentSymbol && _ticks.value.isNotEmpty()) return
        currentSymbol = symbol
        _ticks.value = emptyList()
        if (_connectionState.value is ConnectionState.Connected) {
            sendJson(JSONObject().put("forget_all", "ticks"))
            subscribeTicks(symbol)
        }
    }

    fun subscribeTicks(symbol: String) {
        currentSymbol = symbol
        val payload = JSONObject()
            .put("ticks_history", symbol)
            .put("adjust_start_time", 1)
            .put("count", 60)
            .put("end", "latest")
            .put("start", 1)
            .put("style", "ticks")
            .put("subscribe", 1)
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    fun fetchActiveSymbols() {
        val payload = JSONObject()
            .put("active_symbols", "brief")
            .put("product_type", "basic")
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    fun authorize(token: String) {
        pendingToken = token.trim()
        if (pendingToken.isBlank()) return
        val payload = JSONObject()
            .put("authorize", pendingToken)
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    fun subscribeBalance() {
        val payload = JSONObject()
            .put("balance", 1)
            .put("subscribe", 1)
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    override fun requestProposal(
        symbol: String,
        contractType: String,
        amount: Double,
        currency: String,
        multiplier: Int?,
        duration: Int?,
        durationUnit: String?
    ) {
        val payload = JSONObject()
            .put("proposal", 1)
            .put("amount", amount)
            .put("basis", "stake")
            .put("contract_type", contractType)
            .put("currency", currency)
            .put("symbol", symbol)
            .put("req_id", reqCounter.getAndIncrement())
        if (multiplier != null) {
            payload.put("multiplier", multiplier)
        }
        if (duration != null && durationUnit != null) {
            payload.put("duration", duration)
            payload.put("duration_unit", durationUnit)
        }
        sendJson(payload)
    }

    override fun buyMultiplierContract(
        symbol: String,
        contractType: String,
        stakeUsd: Double,
        multiplier: Int,
        takeProfitUsd: Double,
        stopLossUsd: Double,
        currency: String
    ) {
        val limitOrder = JSONObject()
            .put("take_profit", takeProfitUsd)
            .put("stop_loss", stopLossUsd)

        val parameters = JSONObject()
            .put("amount", stakeUsd)
            .put("basis", "stake")
            .put("contract_type", contractType)
            .put("currency", currency)
            .put("multiplier", multiplier)
            .put("symbol", symbol)
            .put("limit_order", limitOrder)

        val payload = JSONObject()
            .put("buy", 1)
            .put("price", stakeUsd)
            .put("parameters", parameters)
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    override fun buyRapidTickContract(
        symbol: String,
        contractType: String,
        stakeUsd: Double,
        durationSeconds: Int,
        currency: String
    ) {
        val parameters = JSONObject()
            .put("amount", stakeUsd)
            .put("basis", "stake")
            .put("contract_type", contractType)
            .put("currency", currency)
            .put("duration", durationSeconds)
            .put("duration_unit", "s")
            .put("symbol", symbol)

        val payload = JSONObject()
            .put("buy", 1)
            .put("price", stakeUsd)
            .put("parameters", parameters)
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    fun subscribeOpenContract(contractId: String) {
        val numericId = contractId.toLongOrNull()
        val payload = JSONObject()
            .put("proposal_open_contract", 1)
            .put("subscribe", 1)
            .put("req_id", reqCounter.getAndIncrement())
        if (numericId != null) {
            payload.put("contract_id", numericId)
        } else {
            payload.put("contract_id", contractId)
        }
        sendJson(payload)
    }

    override fun sellContract(contractId: String) {
        val numericId = contractId.toLongOrNull()
        val payload = JSONObject()
            .put("sell", numericId ?: contractId)
            .put("price", 0)
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    override fun fetchProfitTable(limit: Int) {
        val payload = JSONObject()
            .put("profit_table", 1)
            .put("description", 1)
            .put("limit", limit)
            .put("sort", "DESC")
            .put("req_id", reqCounter.getAndIncrement())
        sendJson(payload)
    }

    private fun sendPing() {
        lastPingSentMs = System.currentTimeMillis()
        sendJson(JSONObject().put("ping", 1))
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                sendPing()
            }
        }
    }

    private fun scheduleReconnect() {
        if (manualDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = (1000L * (1 shl reconnectAttempt.coerceAtMost(4))).coerceAtMost(15_000L)
            reconnectAttempt++
            delay(backoffMs)
            connect(currentAppId, currentSymbol, pendingToken)
        }
    }

    private fun sendJson(json: JSONObject) {
        webSocket?.send(json.toString())
    }

    private fun handleMessage(text: String) {
        runCatching {
            val root = JSONObject(text)
            val msgType = root.optString("msg_type", "")

            if (root.has("error")) {
                val err = root.getJSONObject("error")
                val code = err.optString("code", "API_ERROR")
                val message = err.optString("message", "Unknown Deriv API error")
                _apiEvents.tryEmit(DerivApiEvent.ApiError(code, message, msgType))
                return
            }

            when (msgType) {
                "ping" -> {
                    if (lastPingSentMs > 0L) {
                        val rtt = (System.currentTimeMillis() - lastPingSentMs).coerceAtLeast(1L)
                        _connectionState.value = ConnectionState.Connected(latencyMs = rtt)
                    }
                }

                "history" -> {
                    val history = root.optJSONObject("history") ?: return
                    val prices = history.optJSONArray("prices") ?: return
                    val times = history.optJSONArray("times") ?: return
                    val pipSize = root.optInt("pip_size", 2)
                    val list = ArrayList<TickPoint>(prices.length())
                    val now = System.currentTimeMillis()
                    for (i in 0 until prices.length()) {
                        val quote = prices.optDouble(i)
                        val epoch = times.optLong(i)
                        if (!quote.isNaN()) {
                            list.add(
                                TickPoint(
                                    epoch = epoch,
                                    quote = quote,
                                    pipSize = pipSize,
                                    receivedAtMs = now - ((prices.length() - i) * 1000L)
                                )
                            )
                        }
                    }
                    _ticks.value = list.takeLast(90)
                }

                "tick" -> {
                    val tick = root.optJSONObject("tick") ?: return
                    val symbol = tick.optString("symbol")
                    if (symbol.isNotBlank() && symbol != currentSymbol) return
                    val point = TickPoint(
                        epoch = tick.optLong("epoch"),
                        quote = tick.optDouble("quote"),
                        ask = if (tick.has("ask")) tick.optDouble("ask") else null,
                        bid = if (tick.has("bid")) tick.optDouble("bid") else null,
                        pipSize = tick.optInt("pip_size", 2),
                        receivedAtMs = System.currentTimeMillis()
                    )
                    if (!point.quote.isNaN()) {
                        val updated = (_ticks.value + point).takeLast(90)
                        _ticks.value = updated
                        _tickEvents.tryEmit(point)
                    }
                }

                "active_symbols" -> {
                    val arr = root.optJSONArray("active_symbols") ?: return
                    val mapped = ArrayList<MarketSymbol>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val sym = obj.optString("symbol")
                        val display = obj.optString("display_name")
                        val marketDisplay = obj.optString("market_display_name", obj.optString("market"))
                        val exchangeOpen = obj.optInt("exchange_is_open", 1) == 1
                        val pip = obj.optDouble("pip", 0.01)
                        val pipSize = when {
                            pip <= 0.00001 -> 5
                            pip <= 0.0001 -> 4
                            pip <= 0.001 -> 3
                            else -> 2
                        }
                        if (exchangeOpen && sym.isNotBlank()) {
                            mapped.add(
                                MarketSymbol(
                                    symbol = sym,
                                    displayName = display.ifBlank { sym },
                                    marketDisplay = marketDisplay.ifBlank { "Market" },
                                    pipSize = pipSize,
                                    isOneSecondIndex = sym.startsWith("1HZ")
                                )
                            )
                        }
                    }
                    if (mapped.isNotEmpty()) {
                        val prioritized = mapped.sortedWith(
                            compareByDescending<MarketSymbol> { it.isOneSecondIndex }
                                .thenBy { it.marketDisplay }
                                .thenBy { it.displayName }
                        )
                        _apiEvents.tryEmit(DerivApiEvent.SymbolsLoaded(prioritized))
                    }
                }

                "authorize" -> {
                    val auth = root.optJSONObject("authorize") ?: return
                    val account = AccountInfo(
                        loginId = auth.optString("loginid", ""),
                        currency = auth.optString("currency", "USD"),
                        balance = auth.optDouble("balance", 0.0),
                        isVirtual = auth.optInt("is_virtual", 1) == 1,
                        email = auth.optString("email", ""),
                        fullname = auth.optString("fullname", "")
                    )
                    _apiEvents.tryEmit(DerivApiEvent.Authorized(account))
                    subscribeBalance()
                }

                "balance" -> {
                    val balObj = root.optJSONObject("balance") ?: return
                    val balance = balObj.optDouble("balance", 0.0)
                    val currency = balObj.optString("currency", "USD")
                    _apiEvents.tryEmit(DerivApiEvent.BalanceUpdated(balance, currency))
                }

                "proposal" -> {
                    val prop = root.optJSONObject("proposal") ?: return
                    _apiEvents.tryEmit(
                        DerivApiEvent.ProposalReceived(
                            proposalId = prop.optString("id"),
                            askPrice = prop.optDouble("ask_price", 0.0),
                            payout = prop.optDouble("payout", 0.0),
                            spot = prop.optDouble("spot", 0.0)
                        )
                    )
                }

                "buy" -> {
                    val buy = root.optJSONObject("buy") ?: return
                    val contractId = buy.opt("contract_id")?.toString() ?: ""
                    val buyPrice = buy.optDouble("buy_price", 0.0)
                    val payout = buy.optDouble("payout", 0.0)
                    val startTime = buy.optLong("start_time", System.currentTimeMillis() / 1000L)
                    val longcode = buy.optString("longcode", "")
                    _apiEvents.tryEmit(
                        DerivApiEvent.ContractBought(
                            contractId = contractId,
                            buyPrice = buyPrice,
                            payout = payout,
                            startTimeEpoch = startTime,
                            longcode = longcode
                        )
                    )
                    if (contractId.isNotBlank()) {
                        subscribeOpenContract(contractId)
                    }
                }

                "proposal_open_contract" -> {
                    val poc = root.optJSONObject("proposal_open_contract") ?: return
                    val contractId = poc.opt("contract_id")?.toString() ?: return
                    val currentSpot = poc.optDouble("current_spot", 0.0)
                    val entrySpot = poc.optDouble("entry_spot", currentSpot)
                    val buyPrice = poc.optDouble("buy_price", 0.0)
                    val bidPrice = poc.optDouble("bid_price", buyPrice)
                    val profit = poc.optDouble("profit", bidPrice - buyPrice)
                    val isSold = poc.optInt("is_sold", 0) == 1
                    val status = poc.optString("status", "open")
                    _apiEvents.tryEmit(
                        DerivApiEvent.OpenContractUpdate(
                            contractId = contractId,
                            currentSpot = currentSpot,
                            entrySpot = entrySpot,
                            buyPrice = buyPrice,
                            bidPrice = bidPrice,
                            profit = profit,
                            isSold = isSold,
                            status = status
                        )
                    )
                }

                "sell" -> {
                    val sell = root.optJSONObject("sell") ?: return
                    val contractId = sell.opt("sold_for")?.let {
                        sell.opt("contract_id")?.toString() ?: ""
                    } ?: ""
                    val soldFor = sell.optDouble("sold_for", 0.0)
                    _apiEvents.tryEmit(
                        DerivApiEvent.ContractSold(
                            contractId = contractId,
                            soldFor = soldFor,
                            profit = null
                        )
                    )
                }

                "profit_table" -> {
                    val pt = root.optJSONObject("profit_table") ?: return
                    val txns = pt.optJSONArray("transactions") ?: return
                    val items = ArrayList<RemoteProfitItem>(txns.length())
                    for (i in 0 until txns.length()) {
                        val t = txns.optJSONObject(i) ?: continue
                        val cid = t.opt("contract_id")?.toString() ?: continue
                        items.add(
                            RemoteProfitItem(
                                contractId = cid,
                                symbol = t.optString("shortcode", "").split("_").getOrNull(1) ?: "DERIV",
                                contractType = t.optString("shortcode", "").substringBefore("_").ifBlank { "CONTRACT" },
                                buyPrice = t.optDouble("buy_price", 0.0),
                                sellPrice = t.optDouble("sell_price", 0.0),
                                purchaseTimeMs = t.optLong("purchase_time", 0L) * 1000L,
                                sellTimeMs = t.optLong("sell_time", 0L) * 1000L
                            )
                        )
                    }
                    _apiEvents.tryEmit(DerivApiEvent.ProfitTableLoaded(items))
                }
            }
        }
    }
}
