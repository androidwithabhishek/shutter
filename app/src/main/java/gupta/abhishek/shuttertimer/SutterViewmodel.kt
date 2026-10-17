package gupta.abhishek.shuttertimer

import gupta.abhishek.shuttertimer.core.ShutterPrefs




import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the main screen needs to draw itself. */
data class ShutterUiState(
    val serviceEnabled: Boolean = false,
    val countdownSec: Int = 3,
    val intervalSec: Int = 2,
    val photoCount: Int = 3,
    val soundOn: Boolean = true
)

class ShutterViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = ShutterPrefs(app)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<ShutterUiState> = _state.asStateFlow()

    /** Call from onResume: the user may have toggled the service in system settings. */
    fun refresh() {
        _state.value = readState()
    }

    fun setCountdown(sec: Int) { prefs.countdownSec = sec; refresh() }
    fun setInterval(sec: Int) { prefs.intervalSec = sec; refresh() }
    fun setPhotoCount(count: Int) { prefs.photoCount = count; refresh() }
    fun setSound(on: Boolean) { prefs.soundOn = on; refresh() }

    private fun readState() = ShutterUiState(
        serviceEnabled = isServiceEnabled(),
        countdownSec = prefs.countdownSec,
        intervalSec = prefs.intervalSec,
        photoCount = prefs.photoCount,
        soundOn = prefs.soundOn
    )

    private fun isServiceEnabled(): Boolean {
        val app = getApplication<Application>()
        val am = app.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == app.packageName }
    }
}