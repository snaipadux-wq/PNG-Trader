package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.BotConfig
import com.example.data.model.ContractMode
import com.example.data.model.StakeSizingMode
import com.example.data.model.TradeStrategy
import com.example.ui.MainUiState
import com.example.ui.components.formatUsd
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralLoss
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoSmall
import kotlin.math.roundToInt

@Composable
fun StrategyConfigScreen(
    state: MainUiState,
    onConnectAndAuthorize: (appId: String, token: String) -> Unit,
    onDisconnectToken: () -> Unit,
    onUpdateConfig: (BotConfig) -> Unit,
    onResetPaperBalance: (Double) -> Unit,
    onApplyTenDollarCompound: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = state.config
    var appIdInput by remember { mutableStateOf(config.appId) }
    var tokenInput by remember { mutableStateOf(config.apiToken) }
    var showToken by remember { mutableStateOf(false) }

    LaunchedEffect(config.appId, config.apiToken) {
        if (appIdInput != config.appId) appIdInput = config.appId
        if (tokenInput != config.apiToken) tokenInput = config.apiToken
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Deriv WebSocket API Credentials Card
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Deriv API Credentials",
                                tint = EmeraldProfit
                            )
                            Text(
                                text = "Deriv Real-Time API Connection",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "Market ticks stream live via wss://ws.derivws.com/websockets/v3. Add a Deriv API Token (Read + Trade scopes) from app.deriv.com/account/api-token to execute orders on your Demo or Real account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = appIdInput,
                            onValueChange = { appIdInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Deriv App ID") },
                            supportingText = { Text("Default public App ID is 1089") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_id_input")
                        )

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Deriv API Token (Optional for Paper Feed)") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showToken = !showToken },
                                    modifier = Modifier.testTag("toggle_token_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle token visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_token_input")
                        )

                        if (state.accountInfo != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Authorized",
                                    tint = EmeraldProfit
                                )
                                Text(
                                    text = "Connected: ${state.accountInfo.loginId} • ${if (state.accountInfo.isVirtual) "Virtual Demo" else "Real Money"} (${formatUsd(state.accountInfo.balance)})",
                                    style = MonoSmall,
                                    color = EmeraldProfit
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onConnectAndAuthorize(appIdInput, tokenInput) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("connect_authorize_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldProfit,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Connect & Authorize", fontWeight = FontWeight.Bold)
                            }
                            if (state.accountInfo != null || config.apiToken.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        tokenInput = ""
                                        onDisconnectToken()
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("clear_token_button")
                                ) {
                                    Text("Clear")
                                }
                            }
                        }

                        // Real Account Safety Lock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Real Trading Lock",
                                    tint = AmberWarning
                                )
                                Column {
                                    Text(
                                        text = "Unlock Real-Money Execution",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "Required only if authorizing a live CR account",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = config.allowRealTrading,
                                onCheckedChange = { enabled ->
                                    onUpdateConfig(config.copy(allowRealTrading = enabled))
                                },
                                modifier = Modifier.testTag("real_trading_switch")
                            )
                        }
                    }
                }
            }

            // Contract Execution Mode
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Contract Execution Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        ContractMode.entries.forEach { mode ->
                            val selected = config.contractMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateConfig(config.copy(contractMode = mode)) }
                                    .padding(vertical = 6.dp)
                                    .testTag("contract_mode_${mode.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onUpdateConfig(config.copy(contractMode = mode)) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(mode.title, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        mode.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1s Scalping Strategy Engine
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1-Second Signal Strategy",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TradeStrategy.entries.forEach { strat ->
                            val selected = config.strategy == strat
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateConfig(config.copy(strategy = strat)) }
                                    .padding(vertical = 6.dp)
                                    .testTag("strategy_${strat.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onUpdateConfig(config.copy(strategy = strat)) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(strat.title, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        strat.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Take-Profit, Compounding & Risk Parameters
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Stake Sizing, Compounding & Guardrails",
                            style = MaterialTheme.typography.titleMedium
                        )

                        StakeSizingMode.entries.forEach { sizing ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUpdateConfig(config.copy(stakeSizingMode = sizing)) }
                                    .padding(vertical = 4.dp)
                                    .testTag("sizing_mode_${sizing.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = config.stakeSizingMode == sizing,
                                    onClick = { onUpdateConfig(config.copy(stakeSizingMode = sizing)) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(sizing.title, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        sizing.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (config.stakeSizingMode == StakeSizingMode.COMPOUND_EQUITY) {
                            Text(
                                text = "Current Effective Order: Stake ${formatUsd(state.effectiveParams.stakeUsd)} • TP ${formatUsd(state.effectiveParams.takeProfitUsd, signed = true)} • SL -${formatUsd(state.effectiveParams.stopLossUsd)}",
                                style = MonoSmall,
                                color = CyanPulse
                            )

                            SliderParam(
                                label = "Equity Allocation per Trade (%)",
                                valueDisplay = "${config.compoundRiskPercent.roundToInt()}%",
                                value = config.compoundRiskPercent.toFloat(),
                                range = 2f..50f,
                                steps = 47,
                                valueColor = CyanPulse,
                                onValueChange = {
                                    onUpdateConfig(config.copy(compoundRiskPercent = it.roundToInt().toDouble()))
                                },
                                testTag = "compound_risk_slider"
                            )

                            SliderParam(
                                label = "Take-Profit Target (% of Stake)",
                                valueDisplay = "+${config.takeProfitPercentOfStake.roundToInt()}%",
                                value = config.takeProfitPercentOfStake.toFloat(),
                                range = 5f..100f,
                                steps = 18,
                                valueColor = EmeraldProfit,
                                onValueChange = {
                                    val stepped = ((it / 5f).roundToInt() * 5).toDouble().coerceAtLeast(5.0)
                                    onUpdateConfig(config.copy(takeProfitPercentOfStake = stepped))
                                },
                                testTag = "tp_percent_slider"
                            )

                            SliderParam(
                                label = "Stop-Loss Guard (% of Stake)",
                                valueDisplay = "-${config.stopLossPercentOfStake.roundToInt()}%",
                                value = config.stopLossPercentOfStake.toFloat(),
                                range = 10f..100f,
                                steps = 17,
                                valueColor = CoralLoss,
                                onValueChange = {
                                    val stepped = ((it / 5f).roundToInt() * 5).toDouble().coerceAtLeast(10.0)
                                    onUpdateConfig(config.copy(stopLossPercentOfStake = stepped))
                                },
                                testTag = "sl_percent_slider"
                            )

                            Text(
                                text = "Target Milestone Goal",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(100.0, 1000.0, 10000.0, 100000.0).forEach { goal ->
                                    val label = when (goal) {
                                        100000.0 -> "$100k"
                                        10000.0 -> "$10k"
                                        1000.0 -> "$1k"
                                        else -> "$100"
                                    }
                                    FilterChip(
                                        selected = config.targetGoalUsd == goal,
                                        onClick = { onUpdateConfig(config.copy(targetGoalUsd = goal)) },
                                        label = { Text(label, style = MonoSmall) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("goal_chip_${goal.toInt()}")
                                    )
                                }
                            }

                            Text(
                                text = "Risk Notice: Turning $10 into $100,000 requires a 10,000x compounded return. Leveraged 1s trading amplifies losses as fast as gains; spreads and losing streaks can deplete a $10 balance rapidly. Test thoroughly in Paper or Demo first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AmberWarning
                            )
                        } else {
                            SliderParam(
                                label = "Stake per Trade",
                                valueDisplay = formatUsd(config.stakeUsd),
                                value = config.stakeUsd.toFloat(),
                                range = 1f..100f,
                                steps = 98,
                                onValueChange = {
                                    onUpdateConfig(config.copy(stakeUsd = it.roundToInt().toDouble()))
                                },
                                testTag = "stake_slider"
                            )

                            SliderParam(
                                label = "1s Take-Profit Target",
                                valueDisplay = formatUsd(config.takeProfitUsd, signed = true),
                                value = config.takeProfitUsd.toFloat(),
                                range = 0.10f..10.0f,
                                steps = 98,
                                valueColor = EmeraldProfit,
                                onValueChange = {
                                    val rounded = (it * 10f).roundToInt() / 10.0
                                    onUpdateConfig(config.copy(takeProfitUsd = rounded.coerceAtLeast(0.10)))
                                },
                                testTag = "take_profit_slider"
                            )

                            SliderParam(
                                label = "Stop-Loss Guard",
                                valueDisplay = "-${formatUsd(config.stopLossUsd)}",
                                value = config.stopLossUsd.toFloat(),
                                range = 0.50f..50.0f,
                                steps = 98,
                                valueColor = CoralLoss,
                                onValueChange = {
                                    val rounded = (it * 2f).roundToInt() / 2.0
                                    onUpdateConfig(config.copy(stopLossUsd = rounded.coerceAtLeast(0.50)))
                                },
                                testTag = "stop_loss_slider"
                            )
                        }

                        val breakEvenWinRate = remember(state.effectiveParams) {
                            val tp = state.effectiveParams.takeProfitUsd
                            val sl = state.effectiveParams.stopLossUsd
                            val denom = tp + sl
                            if (denom > 0.0) (sl / denom) * 100.0 else 100.0
                        }
                        val slToTpRatio = (state.effectiveParams.stopLossUsd / state.effectiveParams.takeProfitUsd.coerceAtLeast(0.01))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Break-Even Win Rate Required: ${"%.1f".format(breakEvenWinRate)}% (SL/TP Ratio ${"%.1f".format(slToTpRatio)}x)",
                                style = MonoSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (breakEvenWinRate > 75.0) AmberWarning else EmeraldProfit
                            )
                            Text(
                                text = "No trading algorithm can guarantee a 99% win rate in live markets. Tight take-profits with wide stop-losses increase hit rate per trade, but a single stop-loss hit can erase multiple wins.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (config.contractMode == ContractMode.MULTIPLIER_SCALP) {
                            Text(
                                text = "Multiplier Leverage",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(20, 50, 100, 200).forEach { mult ->
                                    FilterChip(
                                        selected = config.multiplier == mult,
                                        onClick = { onUpdateConfig(config.copy(multiplier = mult)) },
                                        label = { Text("x$mult", style = MonoSmall) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("multiplier_chip_$mult")
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Rapid Contract Duration",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(1, 2, 3, 5).forEach { sec ->
                                    FilterChip(
                                        selected = config.tickDurationSeconds == sec,
                                        onClick = { onUpdateConfig(config.copy(tickDurationSeconds = sec)) },
                                        label = { Text("${sec}s", style = MonoSmall) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("duration_chip_$sec")
                                    )
                                }
                            }
                        }

                        SliderParam(
                            label = "Daily Profit Cap (Auto-Stop)",
                            valueDisplay = formatUsd(config.dailyProfitCapUsd),
                            value = config.dailyProfitCapUsd.toFloat(),
                            range = 5f..250f,
                            steps = 48,
                            valueColor = CyanPulse,
                            onValueChange = {
                                val stepped = ((it / 5f).roundToInt() * 5).toDouble()
                                onUpdateConfig(config.copy(dailyProfitCapUsd = stepped.coerceAtLeast(5.0)))
                            },
                            testTag = "daily_cap_slider"
                        )

                        SliderParam(
                            label = "Re-Arm Cooldown",
                            valueDisplay = "${config.cooldownSeconds}s",
                            value = config.cooldownSeconds.toFloat(),
                            range = 0f..10f,
                            steps = 9,
                            onValueChange = {
                                onUpdateConfig(config.copy(cooldownSeconds = it.roundToInt()))
                            },
                            testTag = "cooldown_slider"
                        )
                    }
                }
            }

            // Utilities & Display Preferences
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Terminal Dark Mode", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "High-contrast obsidian palette",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.darkTheme,
                                onCheckedChange = { onToggleTheme() },
                                modifier = Modifier.testTag("theme_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onApplyTenDollarCompound,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("ten_dollar_challenge_button")
                            ) {
                                Text("$10 Compound Test")
                            }

                            OutlinedButton(
                                onClick = { onResetPaperBalance(10000.0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("reset_paper_balance_button")
                            ) {
                                Text("Reset $10,000")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderParam(
    label: String,
    valueDisplay: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    testTag: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                valueDisplay,
                style = MonoSmall.copy(fontWeight = FontWeight.Bold),
                color = valueColor
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.testTag(testTag)
        )
    }
}
