package gupta.abhishek.shuttertimer
import gupta.abhishek.shuttertimer.core.ShutterTheme




import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colours come from ShutterTheme so the app and the camera overlay always match.
private val accent: Color get() = Color(ShutterTheme.accent)
private val onAccent: Color get() = Color(ShutterTheme.onAccent)
private val glass: Color get() = Color(ShutterTheme.glass)
private val glassStroke: Color get() = Color(ShutterTheme.glassStroke)
private val textPrimary: Color get() = Color(ShutterTheme.textPrimary)
private val textDim: Color get() = Color(ShutterTheme.textDim)
private val warning: Color get() = Color(ShutterTheme.warning)

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

        // Service status: yellow dot = running, orange = needs to be switched on
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (state.serviceEnabled) accent else warning)
                )
                Text(
                    text = if (state.serviceEnabled) "Service is on" else "Service is off. Turn it on in Accessibility.",
                    color = textPrimary,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

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
            options = listOf("⏱ Off" to 0, "3S" to 3, "6S" to 6, "10S" to 10),
            selected = state.countdownSec,
            onSelect = viewModel::setCountdown
        )

        Label("Gap between photos")
        GlassRow(
            options = listOf("2S" to 2, "3S" to 3, "5S" to 5),
            selected = state.intervalSec,
            onSelect = viewModel::setInterval
        )

        Label("Number of photos")
        GlassRow(
            options = listOf("1" to 1, "3" to 3, "6" to 6, "10" to 10),
            selected = state.photoCount,
            onSelect = viewModel::setPhotoCount
        )

        Label("Beep sound")
        GlassRow(
            options = listOf("🔔 On" to 1, "🔕 Off" to 0),
            selected = if (state.soundOn) 1 else 0,
            onSelect = { viewModel.setSound(it == 1) }
        )

        Label("How to use")
        GlassCard {
            Text(
                "1  Turn on Shutter Timer in Accessibility\n" +
                        "2  Open your camera app and tap ⏱ Timer (drag it anywhere)\n" +
                        "3  Tap Set shutter, then tap your shutter button once\n" +
                        "4  Press Start. The countdown shows on screen",
                color = textPrimary,
                lineHeight = 22.sp
            )
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass)
            .border(1.dp, glassStroke, shape)
            .padding(16.dp)
    ) { content() }
}

/** Pill with equally sized options; the selected one is filled with the accent colour. */
@Composable
private fun GlassRow(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
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