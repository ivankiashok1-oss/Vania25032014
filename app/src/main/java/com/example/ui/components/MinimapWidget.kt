package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.FlagPattern
import com.example.model.NationFlag
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudSurfaceDark
import com.example.ui.theme.TacticalCyan
import kotlin.math.roundToInt

@Composable
fun MinimapWidget(
  minimapBitmap: ImageBitmap?,
  mapWidth: Int,
  mapHeight: Int,
  viewLeft: Float,
  viewTop: Float,
  viewRight: Float,
  viewBottom: Float,
  onJumpToTile: (Float, Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(8.dp)
  Box(
    modifier =
      modifier
        .size(96.dp)
        .clip(shape)
        .background(HudSurfaceDark.copy(alpha = 0.9f))
        .border(1.5.dp, HudBorderBright, shape)
        .testTag("minimap_widget")
  ) {
    Canvas(
      modifier =
        Modifier.matchParentSize()
          .pointerInput(mapWidth, mapHeight) {
            detectTapGestures { pos ->
              val tx = (pos.x / size.width) * mapWidth
              val ty = (pos.y / size.height) * mapHeight
              onJumpToTile(tx, ty)
            }
          }
          .pointerInput(mapWidth, mapHeight) {
            detectDragGestures { change, _ ->
              change.consume()
              val tx = (change.position.x / size.width).coerceIn(0f, 1f) * mapWidth
              val ty = (change.position.y / size.height).coerceIn(0f, 1f) * mapHeight
              onJumpToTile(tx, ty)
            }
          }
    ) {
      if (minimapBitmap != null) {
        drawImage(
          image = minimapBitmap,
          dstOffset = IntOffset.Zero,
          dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
          filterQuality = FilterQuality.None,
        )
      }

      if (mapWidth > 0 && mapHeight > 0) {
        val lx = (viewLeft / mapWidth) * size.width
        val ty = (viewTop / mapHeight) * size.height
        val rx = (viewRight / mapWidth) * size.width
        val by = (viewBottom / mapHeight) * size.height
        drawRect(
          color = TacticalCyan,
          topLeft = Offset(lx.coerceIn(0f, size.width), ty.coerceIn(0f, size.height)),
          size = Size((rx - lx).coerceAtLeast(6f), (by - ty).coerceAtLeast(6f)),
          style = Stroke(width = 2f),
        )
      }
    }
  }
}

@Composable
fun PixelFlagView(flag: NationFlag, modifier: Modifier = Modifier) {
  Canvas(
    modifier =
      modifier
        .size(width = 36.dp, height = 24.dp)
        .border(1.dp, Color(0xFF475569), RoundedCornerShape(2.dp))
  ) {
    val c1 = Color(flag.primaryColor)
    val c2 = Color(flag.secondaryColor)
    val c3 = Color(flag.accentColor)
    val w = size.width
    val h = size.height

    when (flag.pattern) {
      FlagPattern.TRICOLOR_H -> {
        drawRect(c1, Offset.Zero, Size(w, h / 3f))
        drawRect(c2, Offset(0f, h / 3f), Size(w, h / 3f))
        drawRect(c3, Offset(0f, h * 2f / 3f), Size(w, h / 3f))
      }
      FlagPattern.TRICOLOR_V -> {
        drawRect(c1, Offset.Zero, Size(w / 3f, h))
        drawRect(c2, Offset(w / 3f, 0f), Size(w / 3f, h))
        drawRect(c3, Offset(w * 2f / 3f, 0f), Size(w / 3f, h))
      }
      FlagPattern.CROSS -> {
        drawRect(c1, Offset.Zero, Size(w, h))
        drawRect(c2, Offset(w * 0.35f, 0f), Size(w * 0.16f, h))
        drawRect(c2, Offset(0f, h * 0.38f), Size(w, h * 0.24f))
      }
      FlagPattern.BICOLOR_DIAG -> {
        drawRect(c1, Offset.Zero, Size(w, h))
        drawRect(c2, Offset(0f, h * 0.5f), Size(w, h * 0.5f))
        drawRect(c3, Offset(w * 0.4f, h * 0.35f), Size(w * 0.2f, h * 0.3f))
      }
      FlagPattern.CANTON -> {
        drawRect(c1, Offset.Zero, Size(w, h))
        drawRect(c2, Offset(0f, h * 0.33f), Size(w, h * 0.33f))
        drawRect(c3, Offset.Zero, Size(w * 0.44f, h * 0.55f))
      }
      FlagPattern.STRIPE_STAR -> {
        drawRect(c1, Offset.Zero, Size(w, h))
        drawRect(c2, Offset(0f, h * 0.25f), Size(w, h * 0.5f))
        drawCircle(c3, radius = h * 0.18f, center = Offset(w * 0.5f, h * 0.5f))
      }
    }
  }
}
