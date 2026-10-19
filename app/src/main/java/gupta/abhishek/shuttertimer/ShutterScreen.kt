package gupta.abhishek.shuttertimer

import android.content.Intent
import android.provider.Settings
import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colours come from ShutterTheme so the app and the camera overlay always match.
private val accent: Color get() = Color(ShutterTheme.accent)
private val onAccent: Color get() = Color(ShutterTheme.onAccent)
private val glass: Color get() = Color(ShutterTheme.glass)
private val glassStroke: Color get() = Color(ShutterTheme.glassStroke)
private val textPrimary: Color get() = Color(ShutterTheme.textPrimary)
private val textDim: Color get() = Color(ShutterTheme.textDim)

// Service status dot
private val statusGreen = Color(0xFF34C759)     // timer activated
private val statusRed = Color(0xFFFF3B30)       // timer not activated

@Composable
fun ShutterScreen(viewModel: ShutterViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2C2C2E), Color.Black)))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Shutter Timer", color = textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        // Everything below the top title sits a little lower
        Spacer(Modifier.height(24.dp))

        // Service status: green dot = timer activated, red = needs to be switched on
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (state.serviceEnabled) statusGreen else statusRed)
                )
                Text(
                    text = if (state.serviceEnabled) "Timer Activated" else "Timer not activated. Turn it on in Accessibility.",
                    color = textPrimary,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
                .clickable {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Open Accessibility settings", color = onAccent, fontWeight = FontWeight.Bold)
        }

        // Defaults below are the same values the camera panel uses.
        Label("Timer before first photo")
        GlassRow(
            options = listOf("3s" to 3, "5s" to 5, "10s" to 10, "15s" to 15),
            selected = state.countdownSec,
            onSelect = viewModel::setCountdown
        )

        Label("Gap between photos")
        GlassRow(
            options = listOf("1s" to 1, "3s" to 3, "5s" to 5, "10s" to 10),
            selected = state.intervalSec,
            onSelect = viewModel::setInterval
        )

        Label("Number of photos")
        GlassRow(
            options = listOf("3" to 3, "5" to 5, "10" to 10, "15" to 15),
            selected = state.photoCount,
            onSelect = viewModel::setPhotoCount
        )

        // Beep sound: swipe (or tap) the switch to turn it on / off
        Label("Beep sound")
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (state.soundOn) "🔔 On" else "🔕 Off",
                    color = textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Switch(
                    checked = state.soundOn,
                    onCheckedChange = { viewModel.setSound(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = onAccent,
                        checkedTrackColor = accent,
                        checkedBorderColor = accent,
                        uncheckedThumbColor = textDim,
                        uncheckedTrackColor = glass,
                        uncheckedBorderColor = glassStroke
                    )
                )
            }
        }

        Label("How to use")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StepRow(1, "Turn on Shutter Timer in Accessibility")
                StepRow(2, "Open your camera app and tap ⏱ Timer (drag it anywhere)")
                StepRow(3, "Tap Set shutter, then tap your shutter button once")
                StepRow(4, "Press Start. The countdown shows on screen")

                // Battery tip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatteryIcon()
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Save battery", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "After your work is done, deactivate the app. On some phones it can use a little extra battery, so turning it off gives a better experience.",
                            color = textPrimary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }
}


/** One numbered step: yellow number badge + text. */
@Composable
private fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = onAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(
            text = text,
            color = textPrimary,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )
    }
}

/** Small battery icon drawn with shapes: outline, charge level and the tip on the right. */
@Composable
private fun BatteryIcon(
    modifier: Modifier = Modifier,
    color: Color = accent,
    level: Float = 0.6f              // how full the battery looks (0..1)
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 20.dp)
                .border(2.dp, color, RoundedCornerShape(5.dp))
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Box(
            Modifier
                .size(width = 3.dp, height = 9.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(color)
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = textDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp)
    )
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier.heightIn(min = 55.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(glass)
            .border(1.dp, glassStroke, shape)
            .padding(16.dp),
                contentAlignment = Alignment.CenterStart  // centres the Row vertically
    ) { content() }
}

/** Pill with equally sized options; the selected one is filled with the accent colour. */
@Composable
private fun GlassRow(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass)
            .border(1.dp, glassStroke, shape)
            .padding(4.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(shape)
                    .background(if (isSelected) accent else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) onAccent else textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}