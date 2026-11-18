package com.example.domain

import com.example.data.model.BotConfig
import com.example.data.model.ContractMode
import com.example.data.model.OpenPosition
import com.example.data.model.SignalTelemetry
import com.example.data.model.StakeSizingMode
import com.example.data.model.TickPoint
import com.example.data.model.TradeStrategy
import kotlin.math.abs
import kotlin.math.roundToInt

data class EntrySignal(
    val contractType: String,
    val reason: String
)

data class EffectiveTradeParams(
    val stakeUsd: Double,
    val takeProfitUsd: Double,
    val stopLossUsd: Double
)

enum class ExitAction {
    HOLD,
    TAKE_PROFIT,
    STOP_LOSS,
    RAPID_SETTLE
}

class ScalpingEngine {

    fun computeEffectiveTradeParams(
        config: BotConfig,
        availableBalanceUsd: Double
    ): EffectiveTradeParams {
        if (config.stakeSizingMode == StakeSizingMode.FIXED_STAKE) {
            return EffectiveTradeParams(
                stakeUsd = config.stakeUsd,
                takeProfitUsd = config.takeProfitUsd,
                stopLossUsd = config.stopLossUsd
            )
        }

        // Dynamic equity compounding: scales stake proportionally to live equity,
        // respecting Deriv minimum stake ($0.35) and a per-order safety cap ($5,000).
        val rawStake = availableBalanceUsd * (config.compoundRiskPercent / 100.0)
        val clampedStake = rawStake
            .coerceAtMost(availableBalanceUsd)
            .coerceIn(0.35, 5000.0)
        val roundedStake = (clampedStake * 100.0).roundToInt() / 100.0

        val tp = ((roundedStake * (config.takeProfitPercentOfStake / 100.0)) * 100.0).roundToInt() / 100.0
        val sl = ((roundedStake * (config.stopLossPercentOfStake / 100.0)) * 100.0).roundToInt() / 100.0

        return EffectiveTradeParams(
            stakeUsd = roundedStake,
            takeProfitUsd = tp.coerceAtLeast(0.05),
            stopLossUsd = sl.coerceAtLeast(0.20)
        )
    }

    fun analyzeTicks(ticks: List<TickPoint>): SignalTelemetry {
        if (ticks.size < 6) {
            return SignalTelemetry(
                signalReason = "Buffering ticks (${ticks.size}/6)..."
            )
        }

        val quotes = ticks.map { it.quote }
        val emaFastSeries = calculateEmaSeries(quotes, period = 5)
        val emaSlowSeries = calculateEmaSeries(quotes, period = 12)
        val smaFastSeries = calculateSmaSeries(quotes, period = 5)
        val smaSlowSeries = calculateSmaSeries(quotes, period = 15)

        val emaFast = emaFastSeries.last()
        val emaSlow = emaSlowSeries.last()
        val smaFast = smaFastSeries.last()
        val smaSlow = smaSlowSeries.last()
        val rsi = calculateRsi(quotes, period = 7)

        val recent = ticks.takeLast(5)
        val velocity = recent.last().quote - recent.first().quote
        val baseQuote = recent.first().quote.takeIf { it > 0.0 } ?: 1.0
        val momentumPct = (velocity / baseQuote) * 100.0
        val elapsedSec = ((recent.last().receivedAtMs - recent.first().receivedAtMs).coerceAtLeast(500L)) / 1000.0
        val ticksPerSecond = (recent.size - 1) / elapsedSec

        val prevEmaFast = emaFastSeries.getOrElse(emaFastSeries.lastIndex - 1) { emaFast }
        val prevEmaSlow = emaSlowSeries.getOrElse(emaSlowSeries.lastIndex - 1) { emaSlow }
        val maCrossoverState = when {
            prevEmaFast <= prevEmaSlow && emaFast > emaSlow -> "GOLDEN CROSS"
            prevEmaFast >= prevEmaSlow && emaFast < emaSlow -> "DEATH CROSS"
            emaFast > emaSlow && smaFast > smaSlow -> "MA BULLISH"
            emaFast < emaSlow && smaFast < smaSlow -> "MA BEARISH"
            else -> "CONSOLIDATION"
        }

        val bias = when {
            emaFast > emaSlow && velocity > 0 -> "BULLISH"
            emaFast < emaSlow && velocity < 0 -> "BEARISH"
            else -> "NEUTRAL"
        }

        val reason = "EMA5 ${"%.2f".format(emaFast)} | SMA15 ${"%.2f".format(smaSlow)} | MOM ${"%+.3f".format(momentumPct)}% | RSI ${"%.1f".format(rsi)}"
        return SignalTelemetry(
            velocity = velocity,
            momentumPct = momentumPct,
            rsi = rsi,
            smaFast = smaFast,
            smaSlow = smaSlow,
            emaFast = emaFast,
            emaSlow = emaSlow,
            emaFastSeries = emaFastSeries,
            smaSlowSeries = smaSlowSeries,
            maCrossoverState = maCrossoverState,
            directionBias = bias,
            signalReason = reason,
            ticksPerSecond = ticksPerSecond
        )
    }

    fun evaluateEntry(
        ticks: List<TickPoint>,
        config: BotConfig,
        telemetry: SignalTelemetry
    ): EntrySignal? {
        if (ticks.size < 8) return null
        val lastTwoDelta = ticks.last().quote - ticks[ticks.size - 2].quote
        val isMultiplier = config.contractMode == ContractMode.MULTIPLIER_SCALP

        fun upType() = if (isMultiplier) "MULTUP" else "CALL"
        fun downType() = if (isMultiplier) "MULTDOWN" else "PUT"

        return when (config.strategy) {
            TradeStrategy.MOMENTUM_BREAKOUT -> {
                when {
                    telemetry.emaFast > telemetry.emaSlow && telemetry.velocity > 0 && lastTwoDelta >= 0 && telemetry.rsi in 48.0..76.0 -> {
                        EntrySignal(
                            contractType = upType(),
                            reason = "1s Momentum Breakout UP (RSI ${"%.1f".format(telemetry.rsi)})"
                        )
                    }
                    telemetry.emaFast < telemetry.emaSlow && telemetry.velocity < 0 && lastTwoDelta <= 0 && telemetry.rsi in 24.0..52.0 -> {
                        EntrySignal(
                            contractType = downType(),
                            reason = "1s Momentum Breakout DOWN (RSI ${"%.1f".format(telemetry.rsi)})"
                        )
                    }
                    else -> null
                }
            }

            TradeStrategy.HIGH_CONFLUENCE_SCALP -> {
                if (ticks.size < 15) return null
                val recentDeltas = (ticks.size - 3 until ticks.size).map { i ->
                    ticks[i].quote - ticks[i - 1].quote
                }
                val allUp = recentDeltas.all { it > 0.0 }
                val allDown = recentDeltas.all { it < 0.0 }

                // Reject sudden erratic spike jumps compared to average recent step size
                val startIdx = (ticks.size - 12).coerceAtLeast(1)
                val absSteps = (startIdx until ticks.size).map { i ->
                    abs(ticks[i].quote - ticks[i - 1].quote)
                }
                val avgStep = absSteps.average().coerceAtLeast(1e-6)
                val latestStep = abs(lastTwoDelta)
                val noExtremeSpike = latestStep <= avgStep * 2.5

                when {
                    noExtremeSpike && allUp && telemetry.emaFast > telemetry.emaSlow && telemetry.rsi in 52.0..68.0 -> {
                        EntrySignal(
                            contractType = upType(),
                            reason = "High-Confluence UP (3-tick + EMA + RSI ${"%.1f".format(telemetry.rsi)})"
                        )
                    }
                    noExtremeSpike && allDown && telemetry.emaFast < telemetry.emaSlow && telemetry.rsi in 32.0..48.0 -> {
                        EntrySignal(
                            contractType = downType(),
                            reason = "High-Confluence DOWN (3-tick + EMA + RSI ${"%.1f".format(telemetry.rsi)})"
                        )
                    }
                    else -> null
                }
            }

            TradeStrategy.MEAN_REVERSION -> {
                when {
                    telemetry.rsi <= 30.0 && lastTwoDelta >= 0 -> {
                        EntrySignal(
                            contractType = upType(),
                            reason = "Oversold 1s Snap-Back UP (RSI ${"%.1f".format(telemetry.rsi)})"
                        )
                    }
                    telemetry.rsi >= 70.0 && lastTwoDelta <= 0 -> {
                        EntrySignal(
                            contractType = downType(),
                            reason = "Overbought 1s Snap-Back DOWN (RSI ${"%.1f".format(telemetry.rsi)})"
                        )
                    }
                    else -> null
                }
            }

            TradeStrategy.CONTINUOUS_SCALP -> {
                val type = if (telemetry.emaFast >= telemetry.emaSlow) upType() else downType()
                val label = if (type == upType()) "UP" else "DOWN"
                EntrySignal(
                    contractType = type,
                    reason = "Continuous 1s Scalp $label (EMA5 bias)"
                )
            }
        }
    }

    fun computeBreakEvenWinRatePercent(takeProfitUsd: Double, stopLossUsd: Double): Double {
        val totalRange = abs(takeProfitUsd) + abs(stopLossUsd)
        if (totalRange <= 0.0) return 100.0
        return (abs(stopLossUsd) / totalRange) * 100.0
    }

    fun calculatePaperMultiplierProfit(
        position: OpenPosition,
        currentQuote: Double
    ): Double {
        if (position.entrySpot <= 0.0) return 0.0
        val direction = if (position.contractType == "MULTUP" || position.contractType == "CALL") 1.0 else -1.0
        val pctMove = (currentQuote - position.entrySpot) / position.entrySpot
        // Multiplier P&L reflects leveraged % move minus small synthetic commission/spread
        val rawPnl = position.buyPrice * position.multiplier * pctMove * direction
        val spreadFee = position.buyPrice * 0.003
        return (rawPnl - spreadFee).coerceAtLeast(-position.buyPrice)
    }

    fun evaluateOpenPosition(
        position: OpenPosition,
        nowMs: Long
    ): ExitAction {
        if (position.contractType == "CALL" || position.contractType == "PUT") {
            val expiresAt = position.expiresAtMs ?: (position.openedAtMs + 2000L)
            return if (nowMs >= expiresAt) ExitAction.RAPID_SETTLE else ExitAction.HOLD
        }
        return when {
            position.profitUsd >= position.takeProfitUsd -> ExitAction.TAKE_PROFIT
            position.profitUsd <= -abs(position.stopLossUsd) -> ExitAction.STOP_LOSS
            else -> ExitAction.HOLD
        }
    }

    private fun calculateEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val result = ArrayList<Double>(values.size)
        val k = 2.0 / (period + 1)
        var ema = values.first()
        result.add(ema)
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            result.add(ema)
        }
        return result
    }

    private fun calculateSmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val result = ArrayList<Double>(values.size)
        var runningSum = 0.0
        for (i in values.indices) {
            runningSum += values[i]
            if (i >= period) {
                runningSum -= values[i - period]
            }
            val windowSize = (i + 1).coerceAtMost(period)
            result.add(runningSum / windowSize)
        }
        return result
    }

    private fun calculateRsi(values: List<Double>, period: Int): Double {
        if (values.size <= period) return 50.0
        var gainSum = 0.0
        var lossSum = 0.0
        val start = values.size - period
        for (i in start until values.size) {
            val diff = values[i] - values[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += abs(diff)
        }
        if (lossSum == 0.0) return if (gainSum == 0.0) 50.0 else 95.0
        val rs = (gainSum / period) / (lossSum / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }
}
