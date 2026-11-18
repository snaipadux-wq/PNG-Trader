package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TradeRepository
import com.example.data.model.BotStatus
import com.example.data.model.ConnectionState
import com.example.data.model.DerivApiEvent
import com.example.data.model.TickPoint
import com.example.data.remote.MarketStreamClient
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

private class FakeMarketStreamClient : MarketStreamClient {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected(12L))
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _ticks = MutableStateFlow<List<TickPoint>>(emptyList())
    override val ticks: StateFlow<List<TickPoint>> = _ticks.asStateFlow()

    private val _tickEvents = MutableSharedFlow<TickPoint>(extraBufferCapacity = 64)
    override val tickEvents: SharedFlow<TickPoint> = _tickEvents.asSharedFlow()

    private val _apiEvents = MutableSharedFlow<DerivApiEvent>(extraBufferCapacity = 64)
    override val apiEvents: SharedFlow<DerivApiEvent> = _apiEvents.asSharedFlow()

    override fun connect(appId: String, symbol: String, apiToken: String) {
        _connectionState.value = ConnectionState.Connected(10L)
    }

    override fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun switchSymbol(symbol: String) = Unit
    override fun requestProposal(
        symbol: String,
        contractType: String,
        amount: Double,
        currency: String,
        multiplier: Int?,
        duration: Int?,
        durationUnit: String?
    ) = Unit

    override fun buyMultiplierContract(
        symbol: String,
        contractType: String,
        stakeUsd: Double,
        multiplier: Int,
        takeProfitUsd: Double,
        stopLossUsd: Double,
        currency: String
    ) = Unit

    override fun buyRapidTickContract(
        symbol: String,
        contractType: String,
        stakeUsd: Double,
        durationSeconds: Int,
        currency: String
    ) = Unit

    override fun sellContract(contractId: String) = Unit
    override fun fetchProfitTable(limit: Int) = Unit

    fun pushTick(quote: Double, epoch: Long, receivedAtMs: Long) {
        val point = TickPoint(epoch = epoch, quote = quote, receivedAtMs = receivedAtMs)
        _ticks.value = (_ticks.value + point).takeLast(90)
        _tickEvents.tryEmit(point)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainViewModelTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var fakeStream: FakeMarketStreamClient
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeStream = FakeMarketStreamClient()
        viewModel = MainViewModel(
            application = app,
            wsClient = fakeStream,
            settingsStore = SettingsDataStore(app),
            tradeRepo = TradeRepository(db.tradeDao())
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun drainMainAndIo() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(80)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `viewModel processes incoming ticks calculates moving averages and executes 1s take profit`() = runBlocking {
        drainMainAndIo()
        viewModel.toggleBot()
        drainMainAndIo()
        assertEquals(BotStatus.SCANNING, viewModel.uiState.value.botStatus)

        // Feed realistic rising 1-second ticks with minor pullbacks so RSI(7) stays in the 48..76 momentum band
        val baseEpoch = 1_700_000_000L
        val baseMs = 1_700_000_000_000L
        val deltas = listOf(0.30, 0.35, -0.25, 0.40, 0.35, -0.30, 0.45, -0.20, 0.40, 0.45, 0.35)
        var price = 1000.0
        var tickIndex = 0
        for (delta in deltas) {
            price += delta
            fakeStream.pushTick(quote = price, epoch = baseEpoch + tickIndex, receivedAtMs = baseMs + tickIndex * 1000L)
            drainMainAndIo()
            tickIndex++
            if (viewModel.uiState.value.openPosition != null) break
        }

        val stateWithSignal = viewModel.uiState.value
        assertTrue(stateWithSignal.telemetry.smaFast > 0.0)
        assertTrue(stateWithSignal.telemetry.smaSlow > 0.0)
        assertTrue(stateWithSignal.telemetry.emaFast > stateWithSignal.telemetry.emaSlow)
        assertTrue(stateWithSignal.telemetry.momentumPct > 0.0)
        assertNotNull("Expected bot to open a position on bullish 1s momentum", stateWithSignal.openPosition)
        assertEquals(BotStatus.IN_POSITION, stateWithSignal.botStatus)

        // Push a favorable 1-second tick (+1.2 on ~1001 @ x100 = ~$1.17 > $0.50 TP)
        val entrySpot = stateWithSignal.openPosition!!.entrySpot
        fakeStream.pushTick(quote = entrySpot + 1.2, epoch = baseEpoch + tickIndex, receivedAtMs = baseMs + tickIndex * 1000L)
        drainMainAndIo()

        val afterTakeProfit = viewModel.uiState.value
        assertNull("Position should close automatically after hitting 1s Take Profit", afterTakeProfit.openPosition)
        assertEquals(1, afterTakeProfit.sessionTradesCount)
        assertTrue("Session P&L should be positive after Take Profit", afterTakeProfit.sessionNetProfitUsd > 0.50)
    }
}
