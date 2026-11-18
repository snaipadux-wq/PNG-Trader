package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BotConfig
import com.example.data.model.ContractMode
import com.example.data.model.StakeSizingMode
import com.example.data.model.TradeStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deriv_pulse_prefs")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val APP_ID = stringPreferencesKey("app_id")
        val API_TOKEN = stringPreferencesKey("api_token")
        val SYMBOL = stringPreferencesKey("symbol")
        val CONTRACT_MODE = stringPreferencesKey("contract_mode")
        val STRATEGY = stringPreferencesKey("strategy")
        val STAKE_SIZING_MODE = stringPreferencesKey("stake_sizing_mode")
        val STAKE_USD = doublePreferencesKey("stake_usd")
        val TAKE_PROFIT_USD = doublePreferencesKey("take_profit_usd")
        val STOP_LOSS_USD = doublePreferencesKey("stop_loss_usd")
        val COMPOUND_RISK_PCT = doublePreferencesKey("compound_risk_pct")
        val TP_PCT_OF_STAKE = doublePreferencesKey("tp_pct_of_stake")
        val SL_PCT_OF_STAKE = doublePreferencesKey("sl_pct_of_stake")
        val STARTING_CAPITAL_USD = doublePreferencesKey("starting_capital_usd")
        val TARGET_GOAL_USD = doublePreferencesKey("target_goal_usd")
        val MULTIPLIER = intPreferencesKey("multiplier")
        val TICK_DURATION_SECONDS = intPreferencesKey("tick_duration_seconds")
        val DAILY_PROFIT_CAP_USD = doublePreferencesKey("daily_profit_cap_usd")
        val MAX_CONSECUTIVE_LOSSES = intPreferencesKey("max_consecutive_losses")
        val COOLDOWN_SECONDS = intPreferencesKey("cooldown_seconds")
        val ALLOW_REAL_TRADING = booleanPreferencesKey("allow_real_trading")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val PAPER_BALANCE = doublePreferencesKey("paper_balance")
    }

    val configFlow: Flow<BotConfig> = context.dataStore.data.map { prefs ->
        BotConfig(
            appId = prefs[Keys.APP_ID]?.ifBlank { "1089" } ?: "1089",
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            symbol = prefs[Keys.SYMBOL] ?: "1HZ100V",
            contractMode = prefs[Keys.CONTRACT_MODE]?.let {
                runCatching { ContractMode.valueOf(it) }.getOrNull()
            } ?: ContractMode.MULTIPLIER_SCALP,
            strategy = prefs[Keys.STRATEGY]?.let {
                runCatching { TradeStrategy.valueOf(it) }.getOrNull()
            } ?: TradeStrategy.MOMENTUM_BREAKOUT,
            stakeSizingMode = prefs[Keys.STAKE_SIZING_MODE]?.let {
                runCatching { StakeSizingMode.valueOf(it) }.getOrNull()
            } ?: StakeSizingMode.FIXED_STAKE,
            stakeUsd = prefs[Keys.STAKE_USD] ?: 10.0,
            takeProfitUsd = prefs[Keys.TAKE_PROFIT_USD] ?: 0.50,
            stopLossUsd = prefs[Keys.STOP_LOSS_USD] ?: 2.00,
            compoundRiskPercent = prefs[Keys.COMPOUND_RISK_PCT] ?: 15.0,
            takeProfitPercentOfStake = prefs[Keys.TP_PCT_OF_STAKE] ?: 10.0,
            stopLossPercentOfStake = prefs[Keys.SL_PCT_OF_STAKE] ?: 30.0,
            startingCapitalUsd = prefs[Keys.STARTING_CAPITAL_USD] ?: 10.0,
            targetGoalUsd = prefs[Keys.TARGET_GOAL_USD] ?: 100000.0,
            multiplier = prefs[Keys.MULTIPLIER] ?: 100,
            tickDurationSeconds = prefs[Keys.TICK_DURATION_SECONDS] ?: 2,
            dailyProfitCapUsd = prefs[Keys.DAILY_PROFIT_CAP_USD] ?: 1000.0,
            maxConsecutiveLosses = prefs[Keys.MAX_CONSECUTIVE_LOSSES] ?: 4,
            cooldownSeconds = prefs[Keys.COOLDOWN_SECONDS] ?: 1,
            allowRealTrading = prefs[Keys.ALLOW_REAL_TRADING] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    val paperBalanceFlow: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.PAPER_BALANCE] ?: 10000.0
    }

    suspend fun saveConfig(config: BotConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_ID] = config.appId.ifBlank { "1089" }
            prefs[Keys.API_TOKEN] = config.apiToken
            prefs[Keys.SYMBOL] = config.symbol
            prefs[Keys.CONTRACT_MODE] = config.contractMode.name
            prefs[Keys.STRATEGY] = config.strategy.name
            prefs[Keys.STAKE_SIZING_MODE] = config.stakeSizingMode.name
            prefs[Keys.STAKE_USD] = config.stakeUsd
            prefs[Keys.TAKE_PROFIT_USD] = config.takeProfitUsd
            prefs[Keys.STOP_LOSS_USD] = config.stopLossUsd
            prefs[Keys.COMPOUND_RISK_PCT] = config.compoundRiskPercent
            prefs[Keys.TP_PCT_OF_STAKE] = config.takeProfitPercentOfStake
            prefs[Keys.SL_PCT_OF_STAKE] = config.stopLossPercentOfStake
            prefs[Keys.STARTING_CAPITAL_USD] = config.startingCapitalUsd
            prefs[Keys.TARGET_GOAL_USD] = config.targetGoalUsd
            prefs[Keys.MULTIPLIER] = config.multiplier
            prefs[Keys.TICK_DURATION_SECONDS] = config.tickDurationSeconds
            prefs[Keys.DAILY_PROFIT_CAP_USD] = config.dailyProfitCapUsd
            prefs[Keys.MAX_CONSECUTIVE_LOSSES] = config.maxConsecutiveLosses
            prefs[Keys.COOLDOWN_SECONDS] = config.cooldownSeconds
            prefs[Keys.ALLOW_REAL_TRADING] = config.allowRealTrading
            prefs[Keys.DARK_THEME] = config.darkTheme
        }
    }

    suspend fun updatePaperBalance(newBalance: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PAPER_BALANCE] = newBalance
        }
    }

    suspend fun resetPaperBalance(amountUsd: Double = 10000.0) {
        updatePaperBalance(amountUsd)
    }
}
