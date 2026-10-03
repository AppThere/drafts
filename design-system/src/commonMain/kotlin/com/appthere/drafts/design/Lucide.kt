package com.appthere.drafts.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Lucide icons the chrome uses, copied from `lucide-static` 1.51.0 rather than depended on.
 *
 * Copied, because there are four of them. The published Compose wrapper ships every Lucide icon,
 * was last released against Compose 1.6, and an APK built without minification would carry all
 * ~1,500 for the sake of these. The path data below is Lucide's own, unchanged except that
 * `video`'s `<rect>` is written out as the equivalent path. Adding one is copying its `d`
 * attributes from the SVG.
 *
 * Lucide is ISC-licensed; the notice ships in the licences screen ([BundledLicences.icons]). None of
 * these four is among the icons Lucide marks as derived from Feather, so the ISC notice is the whole
 * of what they ship under.
 *
 * Drawn the way Lucide draws them -- a 24-unit square, 2-unit round-capped strokes, no fill -- in
 * black, for the caller to tint.
 */
object Lucide {
    /** `file-text`: a page of prose. Mirrored right to left, like the lines of text it shows. */
    val FileText =
        icon(
            "file-text",
            mirrored = true,
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12" +
                "a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )

    /** `video`: a camera. Not mirrored -- a camera has no reading direction. */
    val Video =
        icon(
            "video",
            mirrored = false,
            "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
            // <rect x="2" y="6" width="14" height="12" rx="2" />
            "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        )

    /** `list-tree`: headings and their children. Mirrored, so the tree hangs from the start edge. */
    val ListTree =
        icon(
            "list-tree",
            mirrored = true,
            "M8 5h13",
            "M13 12h8",
            "M13 19h8",
            "M3 10a2 2 0 0 0 2 2h3",
            "M3 5v12a2 2 0 0 0 2 2h3",
        )

    /** `menu`: three bars. Symmetrical, so mirroring would change nothing. */
    val Menu =
        icon(
            "menu",
            mirrored = false,
            "M4 5h16",
            "M4 12h16",
            "M4 19h16",
        )

    private fun icon(
        name: String,
        mirrored: Boolean,
        vararg paths: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = "lucide-$name",
                defaultWidth = SIZE.dp,
                defaultHeight = SIZE.dp,
                viewportWidth = SIZE,
                viewportHeight = SIZE,
                autoMirror = mirrored,
            ).apply {
                paths.forEach { d ->
                    addPath(
                        pathData = addPathNodes(d),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = STROKE,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    /** Lucide's grid. */
    private const val SIZE = 24f

    /** Lucide's default stroke. */
    private const val STROKE = 2f
}
