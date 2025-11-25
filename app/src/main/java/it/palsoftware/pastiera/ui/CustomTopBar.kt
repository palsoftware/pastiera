package it.palsoftware.pastiera.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import kotlin.math.hypot

// Pastiera colors inspired by the dessert
private val PastieraBeige = Color(0xFF6B5435)
private val PastieraBeigeDark = Color(0xFF8B6F47)
private val PastieraOrangeLight = Color(0xFFFFB84D)
private val PastieraYellow = Color(0xFFF2B24C)

/**
 * Custom top bar with Pastiera lattice pattern.
 * Features light diagonal stripes over a dark toasted gradient.
 */
@Composable
fun CustomTopBar(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            ),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = PastieraBeigeDark)
        ) {
            PastieraPattern(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp)
            ) {
                // Centered title and subtitle
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pastiera",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "La Tastiera per la tua Tastiera",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Settings icon on the right
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            color = PastieraBeige.copy(alpha = 0.9f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_content_description),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PastieraPattern(
    modifier: Modifier = Modifier,
    stripeWidth: Dp = 36.dp,
    stripeSpacing: Dp = 96.dp
) {
    val density = LocalDensity.current
    val widthPx = with(density) { stripeWidth.toPx() }
    val spacingPx = with(density) { stripeSpacing.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "pastieraPatternScroll")
    val stripePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = spacingPx,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stripeOffset"
    )

    Canvas(modifier = modifier) {
        drawPastieraPattern(
            stripeWidth = widthPx,
            stripeSpacing = spacingPx,
            phase = stripePhase
        )
    }
}

private fun DrawScope.drawPastieraPattern(
    stripeWidth: Float,
    stripeSpacing: Float,
    phase: Float
) {
    // Base gradient now dark so the light stripes pop.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                PastieraBeigeDark.copy(alpha = 0.95f),
                PastieraBeige.copy(alpha = 0.9f),
                PastieraBeigeDark.copy(alpha = 0.98f)
            ),
            startY = 0f,
            endY = size.height
        )
    )

    val adjustedStripeWidth = stripeWidth * 1.2f
    val verticalStretch = 1.3f
    val diagonal = hypot(size.width, size.height)
    val stripeLength = diagonal * 1.4f
    val startX = -stripeLength - stripeSpacing
    val phaseWrapped = phase % stripeSpacing

    fun drawStripeSet(angle: Float, color: Color) {
        rotate(degrees = angle, pivot = center) {
            var x = startX - phaseWrapped
            while (x < stripeLength + stripeSpacing) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, -stripeLength / 2f),
                    size = Size(adjustedStripeWidth, stripeLength),
                    cornerRadius = CornerRadius(
                        x = adjustedStripeWidth / 2f,
                        y = adjustedStripeWidth / 2f
                    )
                )
                x += stripeSpacing
            }
        }
    }

    // Scale the stripe layer vertically to elongate the diamonds.
    withTransform({
        scale(scaleX = 1f, scaleY = verticalStretch, pivot = center)
    }) {
        drawStripeSet(angle = 45f, color = PastieraYellow.copy(alpha = 0.75f))
        drawStripeSet(angle = -45f, color = PastieraOrangeLight.copy(alpha = 0.7f))
    }
}
