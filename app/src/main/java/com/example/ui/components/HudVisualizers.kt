package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssistantCoreState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.HudGlowBorder
import com.example.ui.theme.IceCyan
import com.example.ui.theme.VoidNavy
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun GlassHudCard(
    modifier: Modifier = Modifier,
    borderColor: Color = HudGlowBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = GlassNavySurface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun JarvisCoreVisualizer(
    coreState: AssistantCoreState,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core")
    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8500, easing = LinearEasing)
        ),
        label = "outer_angle"
    )
    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing)
        ),
        label = "inner_angle"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringColor = when (coreState) {
        AssistantCoreState.LISTENING -> EmeraldOnline
        AssistantCoreState.WAKE_WORD_ACTIVE -> ElectricCyan
        AssistantCoreState.THINKING -> AmberWarning
        AssistantCoreState.SPEAKING -> IceCyan
        AssistantCoreState.STANDBY -> ArcBlue
    }
    val activeBoost = if (coreState == AssistantCoreState.LISTENING) rmsLevel * 0.16f else 0f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = this.size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = 0.34f + activeBoost),
                        ArcBlue.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Outer segmented orbital ring
            val outerR = maxR * 0.90f
            drawArc(
                color = ringColor.copy(alpha = 0.85f),
                startAngle = outerAngle,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(center.x - outerR, center.y - outerR),
                size = Size(outerR * 2, outerR * 2),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ElectricCyan.copy(alpha = 0.5f),
                startAngle = outerAngle + 180f,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = Offset(center.x - outerR, center.y - outerR),
                size = Size(outerR * 2, outerR * 2),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Middle counter-rotating ring
            val midR = maxR * (0.76f * (pulse + activeBoost * 0.4f)).coerceAtMost(0.86f)
            drawArc(
                color = IceCyan.copy(alpha = 0.75f),
                startAngle = innerAngle,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(center.x - midR, center.y - midR),
                size = Size(midR * 2, midR * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Box(
            modifier = Modifier
                .size(size * 0.62f)
                .clip(CircleShape)
                .border(1.5.dp, ringColor.copy(alpha = 0.7f), CircleShape)
                .background(VoidNavy),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_core_emblem),
                contentDescription = "Jarvis VIP Holographic AI Core",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (coreState == AssistantCoreState.STANDBY) 0.85f else 1f),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun AnimatedVoiceWaveform(
    coreState: AssistantCoreState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val barColor = when (coreState) {
        AssistantCoreState.LISTENING -> EmeraldOnline
        AssistantCoreState.SPEAKING -> ElectricCyan
        AssistantCoreState.THINKING -> AmberWarning
        AssistantCoreState.WAKE_WORD_ACTIVE -> IceCyan
        AssistantCoreState.STANDBY -> ArcBlue.copy(alpha = 0.55f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.6f)
        val barWidth = spacing * 0.9f
        val totalWidth = barCount * barWidth + (barCount - 1) * (spacing * 0.6f)
        val startX = (size.width - totalWidth) / 2f
        val centerY = size.height / 2f

        val intensity = when (coreState) {
            AssistantCoreState.LISTENING -> (0.35f + rmsLevel * 0.65f)
            AssistantCoreState.SPEAKING -> 0.78f
            AssistantCoreState.THINKING -> 0.55f
            AssistantCoreState.WAKE_WORD_ACTIVE -> 0.28f
            AssistantCoreState.STANDBY -> 0.14f
        }

        for (i in 0 until barCount) {
            val envelope = sin((i.toFloat() / (barCount - 1)) * PI).toFloat().coerceAtLeast(0.18f)
            val wave = ((sin(phase + i * 0.55f) + 1f) / 2f)
            val barH = (size.height * 0.86f * envelope * intensity * (0.35f + 0.65f * wave))
                .coerceAtLeast(4.dp.toPx())
            val x = startX + i * (barWidth + spacing * 0.6f)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
        }
    }
}
