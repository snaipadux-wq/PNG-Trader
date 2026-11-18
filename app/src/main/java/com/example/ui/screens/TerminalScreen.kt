package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BotStatus
import com.example.data.model.ExecutionMode
import com.example.data.model.MarketSymbol
import com.example.data.model.OpenPosition
import com.example.data.model.StakeSizingMode
import com.example.ui.MainUiState
import kotlin.math.ln
import com.example.ui.components.BotStatusPill
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.ExecutionModeBadge
import com.example.ui.components.TickChart
import com.example.ui.components.formatQuote
import com.example.ui.components.formatUsd
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralLoss
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoLarge
import com.example.ui.theme.MonoMedium
import com.example.ui.theme.MonoSmall
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TerminalScreen(
    state: MainUiState,
    onToggleBot: () -> Unit,
    onTakeProfitNow: () -> Unit,
    onSwitchSymbol: (String) -> Unit,
    onRequestProposal: () -> Unit,
    onDismissBanner: () -> Unit,
    onNavigateToStrategy: () -> Unit,
    onApplyTenDollarCompound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSymbolInfo = remember(state.availableSymbols, state.config.symbol) {
        state.availableSymbols.find { it.symbol == state.config.symbol }
            ?: MarketSymbol(state.config.symbol, state.config.symbol, "Derived 1s", 2, true)
    }

    val latestTick = state.ticks.lastOrNull()
    val prevTick = if (state.ticks.size >= 2) state.ticks[state.ticks.size - 2] else null
    val tickDelta = if (latestTick != null && prevTick != null) latestTick.quote - prevTick.quote else 0.0
    val deltaPositive = tickDelta >= 0

    val displayedBalance = state.accountInfo?.balance ?: state.paperBalanceUsd
    val currencyLabel = state.accountInfo?.currency ?: "USD"
    val isBotActive = state.botStatus != BotStatus.IDLE && state.botStatus != BotStatus.CIRCUIT_BREAKER

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Status & Account Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionBadge(state = state.connectionState)
                    ExecutionModeBadge(
                        mode = state.executionMode,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToStrategy)
                            .testTag("execution_mode_badge")
                    )
                }
            }

            // Optional Status Banner
            if (state.statusBanner != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.statusBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissBanner,
                                modifier = Modifier.testTag("dismiss_banner_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notification"
                                )
                            }
                        }
                    }
                }
            }

            // Balance & Session Net P&L Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (state.executionMode == ExecutionMode.PAPER_LIVE_FEED) {
                                    "PAPER BALANCE ($currencyLabel)"
                                } else {
                                    "${state.accountInfo?.loginId ?: "ACCOUNT"} ($currencyLabel)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatUsd(displayedBalance),
                                style = MonoMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SESSION P&L (${state.sessionTradesCount} TRADES)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatUsd(state.sessionNetProfitUsd, signed = true),
                                style = MonoMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (state.sessionNetProfitUsd >= 0) EmeraldProfit else CoralLoss
                            )
                        }
                    }
                }
            }

            // Compounding Milestone Card (when Dynamic Equity Compounding is active)
            if (state.config.stakeSizingMode == StakeSizingMode.COMPOUND_EQUITY) {
                item {
                    val startCap = state.config.startingCapitalUsd.coerceAtLeast(1.0)
                    val goal = state.config.targetGoalUsd.coerceAtLeast(startCap + 1.0)
                    val current = displayedBalance.coerceAtLeast(0.01)
                    val logProgress = ((ln(current.coerceAtLeast(1.0)) - ln(startCap)) /
                        (ln(goal) - ln(startCap)).coerceAtLeast(0.01))
                        .toFloat()
                        .coerceIn(0f, 1f)
                    val growthMult = current / startCap

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "COMPOUNDING TRACKER (${state.config.compoundRiskPercent.toInt()}% EQUITY/TRADE)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPulse
                                )
                                Text(
                                    text = "${"%.2f".format(growthMult)}x",
                                    style = MonoSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (growthMult >= 1.0) EmeraldProfit else CoralLoss
                                )
                            }
                            LinearProgressIndicator(
                                progress = { logProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = CyanPulse,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Start ${formatUsd(startCap)}",
                                    style = MonoSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Milestone ${formatUsd(goal)}",
                                    style = MonoSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Symbol Quick Selector Strip
            item {
                val quickSymbols = remember(state.availableSymbols) {
                    state.availableSymbols.take(10)
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickSymbols, key = { it.symbol }) { item ->
                        val selected = item.symbol == state.config.symbol
                        FilterChip(
                            selected = selected,
                            onClick = { onSwitchSymbol(item.symbol) },
                            label = {
                                Text(
                                    text = if (item.isOneSecondIndex) "${item.symbol} • 1s" else item.symbol,
                                    style = MonoSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldProfit.copy(alpha = 0.18f),
                                selectedLabelColor = EmeraldProfit
                            ),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("symbol_chip_${item.symbol}")
                        )
                    }
                }
            }

            // Live 1-Second Market Ticker & Chart Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeSymbolInfo.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeSymbolInfo.marketDisplay} • Real-time Deriv Feed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            BotStatusPill(
                                status = state.botStatus,
                                cooldownSeconds = state.cooldownRemainingSec
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = latestTick?.let {
                                    formatQuote(it.quote, activeSymbolInfo.pipSize)
                                } ?: "--.--",
                                style = MonoLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (deltaPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = if (deltaPositive) "Tick up" else "Tick down",
                                    tint = if (deltaPositive) EmeraldProfit else CoralLoss,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = (if (tickDelta >= 0) "+" else "") +
                                        formatQuote(tickDelta, activeSymbolInfo.pipSize),
                                    style = MonoSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (deltaPositive) EmeraldProfit else CoralLoss
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TickChart(
                            ticks = state.ticks,
                            openPosition = state.openPosition,
                            pipSize = activeSymbolInfo.pipSize,
                            emaFastSeries = state.telemetry.emaFastSeries,
                            smaSlowSeries = state.telemetry.smaSlowSeries
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Quant Telemetry Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${state.telemetry.maCrossoverState} • ${state.telemetry.directionBias}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (state.telemetry.directionBias) {
                                        "BULLISH" -> EmeraldProfit
                                        "BEARISH" -> CoralLoss
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = state.telemetry.signalReason,
                                    style = MonoSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onRequestProposal,
                                modifier = Modifier.testTag("verify_proposal_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Verify live Deriv proposal quote",
                                    tint = CyanPulse
                                )
                            }
                        }

                        if (state.lastProposalPayout != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Live Deriv API Quote: Stake ${formatUsd(state.config.stakeUsd)} -> Max Payout ${formatUsd(state.lastProposalPayout)}",
                                style = MonoSmall,
                                color = CyanPulse
                            )
                        }
                    }
                }
            }

            // Open Position Card (Live per-second Take-Profit Monitor)
            item {
                AnimatedVisibility(visible = state.openPosition != null) {
                    state.openPosition?.let { pos ->
                        OpenPositionCard(
                            position = pos,
                            pipSize = activeSymbolInfo.pipSize,
                            onTakeProfitNow = onTakeProfitNow
                        )
                    }
                }
            }

            // Primary Bot Start/Stop Action & Quick Parameters
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            QuickParamTile(
                                label = if (state.config.stakeSizingMode == StakeSizingMode.COMPOUND_EQUITY) {
                                    "NEXT STAKE (${state.config.compoundRiskPercent.toInt()}%)"
                                } else {
                                    "STAKE"
                                },
                                value = formatUsd(state.effectiveParams.stakeUsd),
                                modifier = Modifier.weight(1f)
                            )
                            QuickParamTile(
                                label = "TAKE PROFIT",
                                value = formatUsd(state.effectiveParams.takeProfitUsd, signed = true),
                                valueColor = EmeraldProfit,
                                modifier = Modifier.weight(1f)
                            )
                            QuickParamTile(
                                label = "STOP LOSS",
                                value = "-${formatUsd(state.effectiveParams.stopLossUsd)}",
                                valueColor = CoralLoss,
                                modifier = Modifier.weight(1f)
                            )
                            QuickParamTile(
                                label = "MODE",
                                value = if (state.config.contractMode.name.startsWith("MULT")) {
                                    "x${state.config.multiplier}"
                                } else {
                                    "${state.config.tickDurationSeconds}s"
                                },
                                valueColor = CyanPulse,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (state.config.stakeSizingMode == StakeSizingMode.FIXED_STAKE) {
                            OutlinedButton(
                                onClick = onApplyTenDollarCompound,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("enable_ten_dollar_compound_button"),
                                border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Switch to $10 Equity Compounding Mode (Paper Test)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanPulse,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = onToggleBot,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("toggle_bot_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBotActive) CoralLoss else EmeraldProfit,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = if (isBotActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isBotActive) "Stop bot" else "Start bot"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBotActive) {
                                    "STOP 1s TAKE-PROFIT BOT"
                                } else {
                                    "START 1s TAKE-PROFIT BOT"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (state.executionMode == ExecutionMode.PAPER_LIVE_FEED) {
                            OutlinedButton(
                                onClick = onNavigateToStrategy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("connect_token_cta"),
                                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Connect Deriv Token",
                                    tint = AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Connect Deriv API Token for Demo / Real Execution",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AmberWarning,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Live Activity Console
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "LIVE BOT EXECUTION LOG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.activityLog.isEmpty()) {
                            Text(
                                text = "Awaiting first tick event...",
                                style = MonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                            state.activityLog.take(6).forEachIndexed { idx, entry ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = timeFmt.format(Date(entry.timestampMs)),
                                        style = MonoSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = entry.message,
                                        style = MonoSmall,
                                        color = when (entry.isPositive) {
                                            true -> EmeraldProfit
                                            false -> CoralLoss
                                            null -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenPositionCard(
    position: OpenPosition,
    pipSize: Int,
    onTakeProfitNow: () -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(position.contractId) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(250L)
        }
    }

    val elapsedSec = ((nowMs - position.openedAtMs).coerceAtLeast(0L)) / 1000.0
    val progress = if (position.takeProfitUsd > 0) {
        (position.profitUsd / position.takeProfitUsd).toFloat().coerceIn(0f, 1f)
    } else 0f
    val profitColor = if (position.profitUsd >= 0) EmeraldProfit else CoralLoss

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, profitColor.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${position.contractType} • ${position.symbol}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Entry ${formatQuote(position.entrySpot, pipSize)} • Hold ${"%.1fs".format(elapsedSec)}",
                        style = MonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatUsd(position.profitUsd, signed = true),
                    style = MonoMedium.copy(fontWeight = FontWeight.Bold),
                    color = profitColor
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EmeraldProfit,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SL -${formatUsd(position.stopLossUsd)}",
                    style = MonoSmall,
                    color = CoralLoss
                )
                Text(
                    text = "Target TP ${formatUsd(position.takeProfitUsd, signed = true)}",
                    style = MonoSmall,
                    color = EmeraldProfit
                )
            }

            Button(
                onClick = onTakeProfitNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("take_profit_now_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldProfit,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "CLOSE & LOCK PROFIT NOW",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun QuickParamTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
