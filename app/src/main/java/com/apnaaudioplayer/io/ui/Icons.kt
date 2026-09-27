package com.apnaaudioplayer.io.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons from the design, drawn on a 24×24 grid. Tint them with the `Icon` composable. */
object AppIcons {
    val Music = icon("M9 18V5l12-2v13 M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0 M15 16a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    val Storage = icon(
        "M22 12H2 M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"
    )
    val StorageDrive = icon(
        "M22 12H2 M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z M6 16h.01 M10 16h.01"
    )
    val Equalizer = icon("M6 20v-8 M12 20V5 M18 20V10", strokeWidth = 3f)
    val Disc = icon("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0 M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0")
    val Person = icon("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")
    val Folder = icon("M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z")
    val ChevronLeft = icon("M15 18l-6-6 6-6")
    val Shuffle = icon("M16 3h5v5 M4 20L21 3 M21 16v5h-5 M15 15l6 6 M4 4l5 5")
    val Repeat = icon("M17 1l4 4-4 4 M3 11V9a4 4 0 0 1 4-4h14 M7 23l-4-4 4-4 M21 13v2a4 4 0 0 1-4 4H3")
    val Previous = icon("M5 19V5", fill = "M19 20L9 12l10-8z")
    val Next = icon("M19 5v14", fill = "M5 4l10 8-10 8z")
    val Play = icon(null, fill = "M7 4.5v15a1 1 0 0 0 1.52.85l12-7.5a1 1 0 0 0 0-1.7l-12-7.5A1 1 0 0 0 7 4.5z")
    val Pause = icon(
        null,
        fill = "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z " +
            "M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
    )

    private fun icon(stroke: String?, fill: String? = null, strokeWidth: Float = 2f): ImageVector {
        val black = SolidColor(Color.Black)
        return ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                if (fill != null) addPath(pathData = addPathNodes(fill), fill = black)
                if (stroke != null) addPath(
                    pathData = addPathNodes(stroke),
                    stroke = black,
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()
    }
}
