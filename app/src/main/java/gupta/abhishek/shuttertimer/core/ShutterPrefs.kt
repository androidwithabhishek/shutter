package gupta.abhishek.shuttertimer.core




import android.content.Context

/**
 * Settings shared by the camera overlay service and the main screen (same SharedPreferences file).
 */
class ShutterPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("shutter_prefs", Context.MODE_PRIVATE)

    var countdownSec: Int                       // 0 = no countdown
        get() = sp.getInt("countdown", 3)
        set(v) = sp.edit().putInt("countdown", v).apply()

    var intervalSec: Int
        get() = sp.getInt("interval", 2)
        set(v) = sp.edit().putInt("interval", v).apply()

    var photoCount: Int
        get() = sp.getInt("count", 3)
        set(v) = sp.edit().putInt("count", v).apply()

    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    /** Last position of the draggable timer pill (-1 = never moved, use default). */
    var panelX: Int
        get() = sp.getInt("panel_x", -1)
        set(v) = sp.edit().putInt("panel_x", v).apply()

    var panelY: Int
        get() = sp.getInt("panel_y", -1)
        set(v) = sp.edit().putInt("panel_y", v).apply()

    /**
     * Shutter position is stored as a fraction of the screen (0..1), per camera app and
     * per orientation, so it still lands on the button if the screen size differs slightly.
     */
    fun getShutter(pkg: String?, orientation: Int): Pair<Float, Float>? {
        val parts = sp.getString(shutterKey(pkg, orientation), null)?.split(",") ?: return null
        if (parts.size != 2) return null
        val fx = parts[0].toFloatOrNull() ?: return null
        val fy = parts[1].toFloatOrNull() ?: return null
        return fx to fy
    }

    fun setShutter(pkg: String?, orientation: Int, fx: Float, fy: Float) {
        sp.edit().putString(shutterKey(pkg, orientation), "$fx,$fy").apply()
    }

    private fun shutterKey(pkg: String?, orientation: Int) = "pos_${pkg}_$orientation"
}