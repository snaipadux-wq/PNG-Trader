package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BotStatus
import com.example.data.model.ConnectionState
import com.example.data.model.ExecutionMode
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralLoss
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoSmall
import java.util.Locale

@Composable
fun ConnectionBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val (dotColor, label) = when (state) {
        is ConnectionState.Connected -> {
            val ms = if (state.latencyMs > 0) "${state.latencyMs}ms" else "LIVE"
            EmeraldProfit to "DERIV WS • $ms"
        }
        ConnectionState.Connecting -> AmberWarning to "CONNECTING"
        ConnectionState.Disconnected -> CoralLoss to "OFFLINE"
        is ConnectionState.Error -> CoralLoss to "RETRYING"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(dotColor.copy(alpha = 0.14f))
            .border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(if (state is ConnectionState.Connected) pulseAlpha else 1f)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MonoSmall.copy(fontSize = 11.spValue(), fontWeight = FontWeight.Bold),
            color = dotColor
        )
    }
}

@Composable
fun ExecutionModeBadge(
    mode: ExecutionMode,
    modifier: Modifier = Modifier
) {
    val color = when (mode) {
        ExecutionMode.PAPER_LIVE_FEED -> AmberWarning
        ExecutionMode.DERIV_DEMO -> CyanPulse
        ExecutionMode.DERIV_REAL -> EmeraldProfit
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = mode.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
fun BotStatusPill(
    status: BotStatus,
    cooldownSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val targetColor: Color = when (status) {
        BotStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        BotStatus.SCANNING -> CyanPulse
        BotStatus.IN_POSITION -> EmeraldProfit
        BotStatus.TAKING_PROFIT -> EmeraldProfit
        BotStatus.COOLDOWN -> AmberWarning
        BotStatus.CIRCUIT_BREAKER -> CoralLoss
    }
    val color by animateColorAsState(targetColor, label = "botStatusColor")

    val text = if (status == BotStatus.COOLDOWN && cooldownSeconds > 0) {
        "COOLDOWN ${cooldownSeconds}s"
    } else {
        status.label
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

private fun Int.spValue() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

fun formatUsd(amount: Double, signed: Boolean = false): String {
    val absVal = String.format(Locale.US, "%,.2f", kotlin.math.abs(amount))
    return when {
        amount < 0 -> "-$$absVal"
        signed && amount > 0 -> "+$$absVal"
        else -> "$$absVal"
    }
}

fun formatQuote(quote: Double, pipSize: Int): String {
    val decimals = pipSize.coerceIn(2, 5)
    return String.format(Locale.US, "%,.${decimals}f", quote)
}
