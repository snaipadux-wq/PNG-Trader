package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.TradeEntity
import com.example.ui.MainUiState
import com.example.ui.components.formatUsd
import com.example.ui.theme.CoralLoss
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoMedium
import com.example.ui.theme.MonoSmall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LedgerFilter(val label: String) {
    ALL("All"),
    WINS("Wins"),
    LOSSES("Losses"),
    PAPER("Paper"),
    DERIV("Deriv Account")
}

@Composable
fun LedgerScreen(
    state: MainUiState,
    onSyncDerivProfitTable: () -> Unit,
    onClearPaperTrades: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(LedgerFilter.ALL) }

    val filteredTrades = remember(state.trades, filter) {
        when (filter) {
            LedgerFilter.ALL -> state.trades
            LedgerFilter.WINS -> state.trades.filter { it.profitUsd >= 0 }
            LedgerFilter.LOSSES -> state.trades.filter { it.profitUsd < 0 }
            LedgerFilter.PAPER -> state.trades.filter { it.executionMode.startsWith("PAPER") }
            LedgerFilter.DERIV -> state.trades.filter { it.executionMode.startsWith("DERIV") }
        }
    }

    val totalTrades = state.trades.size
    val wins = state.trades.count { it.profitUsd >= 0 }
    val winRate = if (totalTrades > 0) (wins * 100.0 / totalTrades) else 0.0
    val netProfit = state.trades.sumOf { it.profitUsd }
    val avgHoldSec = if (totalTrades > 0) state.trades.map { it.holdDurationMs }.average() / 1000.0 else 0.0

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Analytics Summary Grid
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "PERFORMANCE LEDGER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(
                                label = "NET P&L",
                                value = formatUsd(netProfit, signed = true),
                                color = if (netProfit >= 0) EmeraldProfit else CoralLoss,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "WIN RATE",
                                value = "${"%.1f".format(winRate)}%",
                                color = CyanPulse,
                                modifier = Modifier.weight(1f)
                            )
                            MetricBox(
                                label = "AVG HOLD",
                                value = "${"%.1f".format(avgHoldSec)}s",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Action Buttons (Sync Deriv Profit Table & Clear Paper)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSyncDerivProfitTable,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sync_profit_table_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Sync Deriv Profit Table",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Sync Deriv Table")
                    }

                    OutlinedButton(
                        onClick = onClearPaperTrades,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_paper_trades_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Paper Trades",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        Text("Clear Paper")
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LedgerFilter.entries) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("ledger_filter_${f.name}")
                        )
                    }
                }
            }

            // Empty State or Trade List
            if (filteredTrades.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = "No trades yet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No executed trades in this filter",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Start the 1-second Take-Profit Bot in the Terminal tab to record live scalping executions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTrades, key = { it.id }) { trade ->
                    TradeRowCard(trade = trade)
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MonoMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun TradeRowCard(trade: TradeEntity) {
    val isProfit = trade.profitUsd >= 0
    val accent = if (isProfit) EmeraldProfit else CoralLoss
    val timeFmt = remember { SimpleDateFormat("MMM dd • HH:mm:ss", Locale.US) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${trade.symbol} • ${trade.contractType}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = trade.closeReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "${timeFmt.format(Date(trade.closedAtMs))} • Hold ${"%.1fs".format(trade.holdDurationMs / 1000.0)} • ${trade.executionMode}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatUsd(trade.profitUsd, signed = true),
                    style = MonoMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
                Text(
                    text = "Stake ${formatUsd(trade.stakeUsd)}",
                    style = MonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
