package gupta.abhishek.shuttertimer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import gupta.abhishek.shuttertimer.core.ShutterPrefs
import gupta.abhishek.shuttertimer.core.ShutterTheme
import kotlin.math.abs

/**
 * Glass timer panel on top of camera apps.
 *
 *  - Collapsed "⏱ Timer" pill (drag it anywhere, tap to open the panel)
 *  - Panel: countdown, gap between photos, number of photos, shutter setup, Start, sound
 *  - Start stays off until the shutter position is set (Set shutter -> tap your shutter once)
 *  - After the countdown the service taps the saved position, once per photo
 *
 * The service never reads screen content; it only watches which app is in front.
 */
class ShutterAccessibilityService : AccessibilityService() {

    private enum class Ui { COLLAPSED, EXPANDED, RUNNING }

    private companion object {
        const val TAG = "ShutterTimer"      // filter Logcat by this tag
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private val prefs by lazy { ShutterPrefs(this) }
    private val cameraPackages = mutableSetOf<String>()
    private var cameraPkg: String? = null

    // Control window: the pill / panel
    private var control: LinearLayout? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var ui = Ui.COLLAPSED

    // Big on-screen countdown (touch-through)
    private var bigView: LinearLayout? = null
    private var bigNumber: TextView? = null
    private var bigLabel: TextView? = null

    // Full-screen "tap your shutter button" overlay
    private var calibrationView: View? = null

    // Burst state
    private var running = false
    private var taken = 0
    private var ticker: Runnable? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun orientation() = resources.configuration.orientation

    private fun isShutterSet() = prefs.getShutter(cameraPkg, orientation()) != null

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        loadCameraPackages()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        Log.d(TAG, "window changed: $pkg")   // shows the real package name of your camera app
        try {
            when {
                // Known camera apps, or any package with "camera" in its name (covers most phone makers)
                pkg in cameraPackages || pkg.contains("camera", ignoreCase = true) -> {
                    cameraPkg = pkg
                    showControl()
                }
                pkg == "com.android.systemui" -> Unit                   // notification shade
                pkg.contains("inputmethod") || pkg.contains("keyboard") -> Unit
                else -> hide()                                          // left the camera app
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not show the timer panel", e)             // never crash the service
        }
    }

    override fun onInterrupt() = hide()

    override fun onUnbind(intent: Intent?): Boolean {
        hide()
        return super.onUnbind(intent)
    }

    @Suppress("DEPRECATION")
    private fun loadCameraPackages() {
        cameraPackages.clear()
        listOf(MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .forEach { action ->
                packageManager.queryIntentActivities(Intent(action), 0)
                    .forEach { cameraPackages += it.activityInfo.packageName }
            }
        cameraPackages += listOf(
            "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2",
            "com.sec.android.app.camera", "com.miui.camera", "com.oneplus.camera",
            "com.oppo.camera", "com.coloros.camera", "com.vivo.camera", "com.motorola.camera3",
            "com.huawei.camera", "org.codeaurora.snapcam", "net.sourceforge.opencamera"
        )
        cameraPackages -= packageName
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            wm.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }

    // ---------------------------------------------------------------- view building blocks

    private fun glassPill() = GradientDrawable().apply {
        setColor(ShutterTheme.glass)
        cornerRadius = dp(50).toFloat()                  // clamps to a full pill
        setStroke(dp(1), ShutterTheme.glassStroke)
    }

    /** One option inside a pill. Selected = accent fill. weighted = share the row equally. */
    private fun cell(
        text: String,
        selected: Boolean = false,
        weighted: Boolean = true,
        color: Int = ShutterTheme.textPrimary,
        onClick: (() -> Unit)? = null
    ) = TextView(this).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (selected) ShutterTheme.onAccent else color)
        if (selected) {
            background = GradientDrawable().apply {
                setColor(ShutterTheme.accent)
                cornerRadius = dp(50).toFloat()
            }
        }
        layoutParams = if (weighted) LinearLayout.LayoutParams(0, dp(38), 1f)
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
        if (!weighted) setPadding(dp(16), 0, dp(16), 0)
        onClick?.let { fn -> setOnClickListener { fn() } }
    }

    private fun row(children: List<View>, matchWidth: Boolean = true) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = glassPill()
        setPadding(dp(4), dp(4), dp(4), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            if (matchWidth) LinearLayout.LayoutParams.MATCH_PARENT
            else LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = if (matchWidth) dp(8) else 0 }
        children.forEach { addView(it) }
    }

    private fun optionRow(
        label: String?,
        options: List<Pair<String, Int>>,
        selected: Int,
        onSelect: (Int) -> Unit
    ): View {
        val cells = mutableListOf<View>()
        if (label != null) cells += cell(label, color = ShutterTheme.textDim)
        options.forEach { (text, value) ->
            cells += cell(text, selected = value == selected) {
                onSelect(value)
                render()
            }
        }
        return row(cells)
    }

    private fun pillButton(text: String, weight: Float, bg: Int, fg: Int, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(fg)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(50).toFloat()
                setStroke(dp(1), ShutterTheme.glassStroke)
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), weight).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setOnClickListener { onClick() }
        }

    // ---------------------------------------------------------------- control window

    private fun showControl() {
        if (control != null) return
        val (sw, _) = screenSize()
        val root = LinearLayout(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs.panelX >= 0) prefs.panelX else (sw - dp(96)) / 2
            y = if (prefs.panelY >= 0) prefs.panelY else dp(56)
        }
        wm.addView(root, p)
        control = root
        controlParams = p
        ui = Ui.COLLAPSED
        render()
    }

    private fun hide() {
        stopSequence()
        endCalibration()
        clearBig()
        control?.let { runCatching { wm.removeView(it) } }
        control = null
        controlParams = null
    }

    /** Rebuilds whatever the current Ui state shows, then keeps the window on screen. */
    private fun render() {
        val root = control ?: return
        val p = controlParams ?: return
        val (sw, sh) = screenSize()
        val panelW = minOf(sw - dp(32), dp(340))

        root.removeAllViews()
        root.orientation = LinearLayout.VERTICAL

        when (ui) {
            Ui.COLLAPSED -> {
                val pill = row(
                    listOf(cell("⏱  Timer", weighted = false, color = ShutterTheme.accent)),
                    matchWidth = false
                )
                makeDraggable(pill) { ui = Ui.EXPANDED; render() }
                root.addView(pill)
            }

            Ui.RUNNING -> {
                val label = cell("$taken / ${prefs.photoCount}", weighted = false, color = ShutterTheme.accent)
                val stop = cell("■ Stop", selected = true, weighted = false) { stopSequence() }
                val pill = row(listOf(label, stop), matchWidth = false)
                makeDraggable(pill)
                root.addView(pill)
            }

            Ui.EXPANDED -> {
                // Grabber: drag the whole panel from here
                val grabber = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(22)
                    )
                    addView(View(this@ShutterAccessibilityService).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.argb(170, 255, 255, 255))
                            cornerRadius = dp(2).toFloat()
                        }
                        layoutParams = FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER)
                    })
                }
                makeDraggable(grabber)
                root.addView(grabber)

                root.addView(optionRow(null, listOf("⏱" to 0, "3S" to 3, "6S" to 6, "10S" to 10), prefs.countdownSec) { prefs.countdownSec = it })
                root.addView(optionRow("Gap", listOf("2S" to 2, "3S" to 3, "5S" to 5), prefs.intervalSec) { prefs.intervalSec = it })
                root.addView(optionRow("Shots", listOf("1" to 1, "3" to 3, "6" to 6, "10" to 10), prefs.photoCount) { prefs.photoCount = it })

                val shutterSet = isShutterSet()
                if (!shutterSet) {
                    // Reminder: Start is off until the shutter position is saved
                    root.addView(TextView(this).apply {
                        text = "⚠ Set your shutter position first"
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(ShutterTheme.warning)
                        setShadowLayer(6f, 0f, 0f, Color.BLACK)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(8) }
                    })
                }

                val soundOn = prefs.soundOn
                root.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    // Shutter button turns yellow once its position is saved
                    addView(pillButton(
                        if (shutterSet) "✓ Shutter set" else "◎ Set shutter", 3f,
                        bg = if (shutterSet) ShutterTheme.accent else ShutterTheme.glass,
                        fg = if (shutterSet) ShutterTheme.onAccent else ShutterTheme.textPrimary
                    ) { beginCalibration() })
                    // Start looks "off" (dim) until the shutter is set
                    addView(pillButton(
                        "▶ Start", 2.2f,
                        bg = if (shutterSet) ShutterTheme.textPrimary else ShutterTheme.glass,
                        fg = if (shutterSet) ShutterTheme.onAccent else ShutterTheme.textDim
                    ) { startSequence() })
                    addView(pillButton(
                        if (soundOn) "🔔" else "🔕", 1f,
                        bg = if (soundOn) ShutterTheme.accent else ShutterTheme.glass,
                        fg = if (soundOn) ShutterTheme.onAccent else ShutterTheme.textPrimary
                    ) { prefs.soundOn = !soundOn; render() })
                    addView(pillButton("✕", 1f, ShutterTheme.glass, ShutterTheme.textPrimary) {
                        ui = Ui.COLLAPSED
                        render()
                    })
                })
            }
        }

        // Fixed width for the panel, then clamp so it never leaves the screen
        p.width = if (ui == Ui.EXPANDED) panelW else WindowManager.LayoutParams.WRAP_CONTENT
        val wSpec = if (ui == Ui.EXPANDED) View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY)
        else View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.AT_MOST)
        root.measure(wSpec, View.MeasureSpec.makeMeasureSpec(sh, View.MeasureSpec.AT_MOST))
        p.x = p.x.coerceIn(0, (sw - root.measuredWidth).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (sh - root.measuredHeight).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(root, p) }
    }

    /** Drag the control window by [handle]; a tap (no movement) calls [onTap]. Position is saved. */
    private fun makeDraggable(handle: View, onTap: (() -> Unit)? = null) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false

        handle.setOnTouchListener { _, e ->
            val p = controlParams ?: return@setOnTouchListener false
            val c = control ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x; startY = p.y
                    downX = e.rawX; downY = e.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        val (sw, sh) = screenSize()
                        p.x = (startX + dx.toInt()).coerceIn(0, (sw - c.width).coerceAtLeast(0))
                        p.y = (startY + dy.toInt()).coerceIn(0, (sh - c.height).coerceAtLeast(0))
                        wm.updateViewLayout(c, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.panelX = p.x
                        prefs.panelY = p.y
                    } else {
                        onTap?.invoke()
                    }
                }
            }
            true
        }
    }

    // ---------------------------------------------------------------- big countdown

    private fun setBig(number: String, label: String) {
        if (bigView == null) {
            val num = TextView(this).apply {
                textSize = 120f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ShutterTheme.accent)
                gravity = Gravity.CENTER
                setShadowLayer(14f, 0f, 0f, Color.BLACK)
            }
            val lab = TextView(this).apply {
                textSize = 22f
                setTextColor(ShutterTheme.textPrimary)
                gravity = Gravity.CENTER
                setShadowLayer(10f, 0f, 0f, Color.BLACK)
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(num)
                addView(lab)
            }
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or      // touches pass through
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            wm.addView(box, p)
            bigView = box
            bigNumber = num
            bigLabel = lab
        }
        bigNumber?.text = number
        bigLabel?.text = label
    }

    private fun clearBig() {
        bigView?.let { runCatching { wm.removeView(it) } }
        bigView = null
        bigNumber = null
        bigLabel = null
    }

    // ---------------------------------------------------------------- burst sequence

    private fun startSequence() {
        if (running) return
        if (!isShutterSet()) {
            toast("Set your shutter position first")
            return
        }
        running = true
        taken = 0
        ui = Ui.RUNNING
        render()
        if (prefs.countdownSec > 0) {
            tick(prefs.countdownSec, "Get ready", sound = true) { takeNext() }
        } else {
            takeNext()
        }
    }

    private fun stopSequence() {
        running = false
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        clearBig()
        if (ui == Ui.RUNNING) {
            ui = Ui.COLLAPSED
            render()
        }
    }

    /** Counts down [seconds] on screen (1 s steps), then calls [then]. */
    private fun tick(seconds: Int, label: String, sound: Boolean, then: () -> Unit) {
        var left = seconds
        val r = object : Runnable {
            override fun run() {
                if (!running) return
                if (left > 0) {
                    setBig("$left", label)
                    if (sound && left <= 3) tone(ToneGenerator.TONE_PROP_BEEP, 100)
                    left--
                    handler.postDelayed(this, 1000)
                } else {
                    clearBig()
                    then()
                }
            }
        }
        ticker = r
        handler.post(r)
    }

    private fun takeNext() {
        if (!running) return
        tapShutter { ok ->
            if (!running) return@tapShutter
            if (!ok) {
                toast("Couldn't press the shutter. Set the shutter position again.")
                stopSequence()
                return@tapShutter
            }
            taken++
            tone(ToneGenerator.TONE_PROP_ACK, 150)       // confirmation after each photo
            render()
            if (taken >= prefs.photoCount) {
                toast("Done: $taken photos")
                stopSequence()
            } else {
                tick(prefs.intervalSec, "Next photo in", sound = false) { takeNext() }
            }
        }
    }

    private fun tone(type: Int, ms: Int) {
        if (!prefs.soundOn) return
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(type, ms)
            handler.postDelayed({ tg.release() }, ms + 200L)
        }
    }

    // ---------------------------------------------------------------- pressing the shutter

    /** Makes the control window ignore touches so a tap can pass through it to the camera. */
    private fun setControlTouchable(on: Boolean) {
        val c = control ?: return
        val p = controlParams ?: return
        p.flags = if (on) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(c, p) }
    }

    private fun tapShutter(done: (Boolean) -> Unit) {
        val saved = prefs.getShutter(cameraPkg, orientation())
        if (saved == null) {
            done(false)
            return
        }
        val (w, h) = screenSize()
        val path = Path().apply { moveTo(saved.first * w, saved.second * h) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()

        setControlTouchable(false)
        handler.postDelayed({
            if (!running) {
                setControlTouchable(true)
                return@postDelayed
            }
            val sent = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    setControlTouchable(true)
                    done(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    setControlTouchable(true)
                    done(false)
                }
            }, null)
            if (!sent) {
                setControlTouchable(true)
                done(false)
            }
        }, 100)
    }

    // ---------------------------------------------------------------- shutter position setup

    private fun beginCalibration() {
        if (calibrationView != null) return
        ui = Ui.COLLAPSED
        render()

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            addView(TextView(this@ShutterAccessibilityService).apply {
                text = "Tap exactly on your camera's shutter button"
                textSize = 20f
                setTextColor(ShutterTheme.textPrimary)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            })
            addView(pillButton("Cancel", 1f, ShutterTheme.glass, ShutterTheme.textPrimary) {
                endCalibration()
            }.apply {
                layoutParams = FrameLayout.LayoutParams(
                    dp(160), dp(44), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = dp(80) }
            })
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    val (w, h) = screenSize()
                    prefs.setShutter(cameraPkg, orientation(), e.rawX / w, e.rawY / h)
                    flashMarker(e.rawX, e.rawY)
                    endCalibration()
                }
                true
            }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(overlay, p)
        calibrationView = overlay
    }

    /** Closes the setup overlay and returns to the panel (shutter button is now yellow if saved). */
    private fun endCalibration() {
        val v = calibrationView ?: return
        runCatching { wm.removeView(v) }
        calibrationView = null
        ui = Ui.EXPANDED
        render()
    }

    /** Shows a yellow ring for a moment at the saved point so you can see where it landed. */
    private fun flashMarker(px: Float, py: Float) {
        val size = dp(72)
        val ring = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(4), ShutterTheme.accent)
            }
        }
        val p = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (px - size / 2).toInt()
            this.y = (py - size / 2).toInt()
        }
        wm.addView(ring, p)
        handler.postDelayed({ runCatching { wm.removeView(ring) } }, 1200)
    }
}