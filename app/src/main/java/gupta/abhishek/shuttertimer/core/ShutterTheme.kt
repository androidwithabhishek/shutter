package gupta.abhishek.shuttertimer

/**
 * Every colour used by the app (camera overlay + main screen) lives here.
 * Values are ARGB ints. Change one (or assign at runtime, e.g. ShutterTheme.accent = ...)
 * and both UIs follow. The Compose screen reads them as Color(ShutterTheme.accent).
 */
object ShutterTheme {
    var accent = 0xFFFFD60A.toInt()        // iPhone-camera yellow: selected items, set shutter, countdown
    var onAccent = 0xFF1C1C1E.toInt()      // text/icons drawn on top of the accent colour
    var glass = 0xB32C2C2E.toInt()         // translucent dark "glass" pill background
    var glassStroke = 0x33FFFFFF           // thin light border that gives the glass edge
    var textPrimary = 0xFFFFFFFF.toInt()
    var textDim = 0x99FFFFFF.toInt()       // disabled text
    var divider = 0x66FFFFFF               // thin line between a row's label and its options
    var warning = 0xFFFF9F0A.toInt()       // "set your shutter" reminder
    var shutterDot = 0x99FFD60A.toInt()        // dull yellow circle on the saved shutter position (panel open)
    var shutterDotSetting = 0xFFB8960C.toInt() // darker yellow circle while the position is being set
}