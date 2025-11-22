package it.palsoftware.pastiera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R

// Pastiera colors inspired by the dessert
private val PastieraBeige = Color(0xFF6B5435) // Much darker beige/brown
private val PastieraBeigeDark = Color(0xFF8B6F47) // Even darker for depth
private val PastieraOrange = Color(0xFFFFA366)
private val PastieraOrangeLight = Color(0xFFFFB84D)
private val PastieraYellow = Color(0xFFFFD700)

/**
 * Custom top bar with Pastiera lattice pattern.
 * Features diagonal beige stripes over an orange/yellow gradient.
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
                .background(color = PastieraOrange)
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
    stripeWidth: Dp = 50.dp,
    stripeSpacing: Dp = 90.dp
) {
    val density = LocalDensity.current
    val widthPx = with(density) { stripeWidth.toPx() }
    val spacingPx = with(density) { stripeSpacing.toPx() }

    Canvas(modifier = modifier) {
        drawPastieraPattern(stripeWidth = widthPx, stripeSpacing = spacingPx)
    }
}

private fun DrawScope.drawPastieraPattern(stripeWidth: Float, stripeSpacing: Float) {
    val halfWidth = stripeWidth / 2f
    val sqrt2 = kotlin.math.sqrt(2.0).toFloat()
    
    // Perpendicular offset for 45-degree lines
    val perpOffset = halfWidth / sqrt2
    
    // Draw first set: diagonal stripes from top-left to bottom-right (y = x + b)
    val maxIntercept = size.width + size.height
    var intercept = -maxIntercept
    while (intercept < maxIntercept) {
        val path = Path().apply {
            // Find where line y = x + intercept intersects screen edges
            var p1x = 0f
            var p1y = intercept
            var p2x = size.width
            var p2y = size.width + intercept
            
            // Adjust if line starts outside top edge
            if (p1y < 0f) {
                p1y = 0f
                p1x = -intercept
            }
            // Adjust if line starts outside bottom edge
            if (p1y > size.height) {
                p1y = size.height
                p1x = size.height - intercept
            }
            
            // Adjust if line ends outside top edge
            if (p2y < 0f) {
                p2y = 0f
                p2x = -intercept
            }
            // Adjust if line ends outside bottom edge
            if (p2y > size.height) {
                p2y = size.height
                p2x = size.height - intercept
            }
            
            // Ensure points are within bounds
            p1x = p1x.coerceIn(0f, size.width)
            p1y = p1y.coerceIn(0f, size.height)
            p2x = p2x.coerceIn(0f, size.width)
            p2y = p2y.coerceIn(0f, size.height)
            
            // Create parallelogram perpendicular to the diagonal line
            val perpX = -perpOffset
            val perpY = perpOffset
            
            moveTo(p1x + perpX, p1y + perpY)
            lineTo(p2x + perpX, p2y + perpY)
            lineTo(p2x - perpX, p2y - perpY)
            lineTo(p1x - perpX, p1y - perpY)
            close()
        }
        
        drawPath(path, color = PastieraBeige)
        intercept += stripeSpacing * sqrt2
    }
    
    // Draw second set: diagonal stripes from top-right to bottom-left (y = -x + b)
    intercept = -size.width
    while (intercept < size.width + size.height) {
        val path = Path().apply {
            // Find where line y = -x + intercept intersects screen edges
            var p1x = 0f
            var p1y = intercept
            var p2x = size.width
            var p2y = -size.width + intercept
            
            // Adjust if line starts outside top edge
            if (p1y < 0f) {
                p1y = 0f
                p1x = intercept
            }
            // Adjust if line starts outside bottom edge
            if (p1y > size.height) {
                p1y = size.height
                p1x = intercept - size.height
            }
            
            // Adjust if line ends outside top edge
            if (p2y < 0f) {
                p2y = 0f
                p2x = intercept
            }
            // Adjust if line ends outside bottom edge
            if (p2y > size.height) {
                p2y = size.height
                p2x = intercept - size.height
            }
            
            // Ensure points are within bounds
            p1x = p1x.coerceIn(0f, size.width)
            p1y = p1y.coerceIn(0f, size.height)
            p2x = p2x.coerceIn(0f, size.width)
            p2y = p2y.coerceIn(0f, size.height)
            
            // Create parallelogram perpendicular to the diagonal line
            val perpX = perpOffset
            val perpY = perpOffset
            
            moveTo(p1x + perpX, p1y + perpY)
            lineTo(p2x + perpX, p2y + perpY)
            lineTo(p2x - perpX, p2y - perpY)
            lineTo(p1x - perpX, p1y - perpY)
            close()
        }
        
        drawPath(path, color = PastieraBeige)
        intercept += stripeSpacing * sqrt2
    }
}

