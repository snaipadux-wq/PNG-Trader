package com.example

import com.example.data.model.BotConfig
import com.example.data.model.ContractMode
import com.example.data.model.OpenPosition
import com.example.data.model.TickPoint
import com.example.data.model.TradeStrategy
import com.example.domain.ExitAction
import com.example.domain.ScalpingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalpingEngineTest {

    private val engine = ScalpingEngine()

    @Test
    fun `momentum breakout generates MULTUP on rising 1s ticks`() {
        val baseTime = 1_700_000_000_000L
        val ticks = (0 until 15).map { idx ->
            TickPoint(
                epoch = 1_700_000_000L + idx,
                quote = 1000.0 + idx * 0.6,
                receivedAtMs = baseTime + idx * 1000L
            )
        }
        val telemetry = engine.analyzeTicks(ticks)
        val config = BotConfig(
            contractMode = ContractMode.MULTIPLIER_SCALP,
            strategy = TradeStrategy.CONTINUOUS_SCALP
        )
        val signal = engine.evaluateEntry(ticks, config, telemetry)
        assertNotNull(signal)
        assertEquals("MULTUP", signal?.contractType)
    }

    @Test
    fun `multiplier position triggers TAKE_PROFIT when profit meets target`() {
        val position = OpenPosition(
            contractId = "PAPER-1",
            symbol = "1HZ100V",
            contractType = "MULTUP",
            buyPrice = 10.0,
            entrySpot = 1000.0,
            currentSpot = 1001.0,
            currentPayout = 10.97,
            profitUsd = 0.97,
            takeProfitUsd = 0.50,
            stopLossUsd = 2.00,
            openedAtMs = 1_000L,
            isPaper = true,
            multiplier = 100
        )
        val calcProfit = engine.calculatePaperMultiplierProfit(position, 1001.0)
        assertTrue(calcProfit > 0.50)

        val action = engine.evaluateOpenPosition(position.copy(profitUsd = calcProfit), 2_000L)
        assertEquals(ExitAction.TAKE_PROFIT, action)
    }

    @Test
    fun `multiplier position triggers STOP_LOSS when drawdown hits guard`() {
        val position = OpenPosition(
            contractId = "PAPER-2",
            symbol = "1HZ100V",
            contractType = "MULTUP",
            buyPrice = 10.0,
            entrySpot = 1000.0,
            currentSpot = 997.5,
            currentPayout = 7.47,
            profitUsd = -2.53,
            takeProfitUsd = 0.50,
            stopLossUsd = 2.00,
            openedAtMs = 1_000L,
            isPaper = true,
            multiplier = 100
        )
        val action = engine.evaluateOpenPosition(position, 2_000L)
        assertEquals(ExitAction.STOP_LOSS, action)
    }

    @Test
    fun `dynamic compounding scales stake and take profit with equity`() {
        val config = BotConfig(
            stakeSizingMode = com.example.data.model.StakeSizingMode.COMPOUND_EQUITY,
            compoundRiskPercent = 15.0,
            takeProfitPercentOfStake = 10.0,
            stopLossPercentOfStake = 30.0
        )

        val atTen = engine.computeEffectiveTradeParams(config, 10.0)
        assertEquals(1.50, atTen.stakeUsd, 0.01)
        assertEquals(0.15, atTen.takeProfitUsd, 0.01)
        assertEquals(0.45, atTen.stopLossUsd, 0.01)

        val atTenThousand = engine.computeEffectiveTradeParams(config, 10000.0)
        assertEquals(1500.0, atTenThousand.stakeUsd, 0.01)
        assertEquals(150.0, atTenThousand.takeProfitUsd, 0.01)
    }

    @Test
    fun `break even win rate reflects stop loss to take profit ratio`() {
        val equalRisk = engine.computeBreakEvenWinRatePercent(takeProfitUsd = 1.0, stopLossUsd = 1.0)
        assertEquals(50.0, equalRisk, 0.01)

        val tightTpWideSl = engine.computeBreakEvenWinRatePercent(takeProfitUsd = 0.20, stopLossUsd = 2.0)
        assertEquals(90.91, tightTpWideSl, 0.1)
    }
}
