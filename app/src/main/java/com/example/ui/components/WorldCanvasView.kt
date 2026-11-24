package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.model.GraphicsQuality
import com.example.model.ParticleType
import com.example.model.WeatherType
import com.example.ui.RenderFrame
import com.example.ui.theme.DeepSpaceBg
import com.example.ui.theme.PixelDisplayFont
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun WorldCanvasView(
  frame: RenderFrame,
  isPanInspectMode: Boolean,
  brushRadius: Int,
  usesBrush: Boolean,
  zoomCommand: Int,
  cameraFocusRequest: Pair<Float, Float>?,
  onCameraFocused: () -> Unit,
  onViewportChanged: (tileLeft: Float, tileTop: Float, tileRight: Float, tileBottom: Float) -> Unit,
  onMapTapOrPaint: (tileX: Int, tileY: Int, isDrag: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current

  var animTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(48L)
      animTick++
    }
  }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().background(DeepSpaceBg).testTag("world_canvas")
  ) {
    val viewW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
    val viewH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

    var tilePx by remember { mutableFloatStateOf(14f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var initializedMapSize by remember { mutableIntStateOf(0) }
    var lastBrushTile by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(frame.mapWidth, viewW, viewH) {
      if (initializedMapSize != frame.mapWidth && frame.mapWidth > 0) {
        val fitTile = (minOf(viewW, viewH) / frame.mapWidth.toFloat()) * 1.15f
        tilePx = fitTile.coerceIn(3f, 22f)
        offsetX = (viewW - frame.mapWidth * tilePx) / 2f
        offsetY = (viewH - frame.mapHeight * tilePx) / 2f
        initializedMapSize = frame.mapWidth
      }
    }

    var lastHandledZoomCmd by remember { mutableIntStateOf(0) }
    LaunchedEffect(zoomCommand) {
      if (zoomCommand != lastHandledZoomCmd) {
        val delta = zoomCommand - lastHandledZoomCmd
        lastHandledZoomCmd = zoomCommand
        val factor = if (delta > 0) 1.28f else 0.78f
        val newTilePx = (tilePx * factor).coerceIn(2.5f, 44f)
        val cx = viewW / 2f
        val cy = viewH / 2f
        val ratio = newTilePx / tilePx
        val rawX = cx - (cx - offsetX) * ratio
        val rawY = cy - (cy - offsetY) * ratio
        tilePx = newTilePx
        offsetX = clampOffsetX(rawX, frame.mapWidth, newTilePx, viewW)
        offsetY = clampOffsetY(rawY, frame.mapHeight, newTilePx, viewH)
      }
    }

    LaunchedEffect(cameraFocusRequest) {
      if (cameraFocusRequest != null) {
        val (tx, ty) = cameraFocusRequest
        offsetX = clampOffsetX(viewW / 2f - tx * tilePx, frame.mapWidth, tilePx, viewW)
        offsetY = clampOffsetY(viewH / 2f - ty * tilePx, frame.mapHeight, tilePx, viewH)
        onCameraFocused()
      }
    }

    LaunchedEffect(offsetX, offsetY, tilePx, viewW, viewH, frame.mapWidth) {
      val left = (-offsetX / tilePx).coerceIn(0f, frame.mapWidth.toFloat())
      val top = (-offsetY / tilePx).coerceIn(0f, frame.mapHeight.toFloat())
      val right = ((viewW - offsetX) / tilePx).coerceIn(0f, frame.mapWidth.toFloat())
      val bottom = ((viewH - offsetY) / tilePx).coerceIn(0f, frame.mapHeight.toFloat())
      onViewportChanged(left, top, right, bottom)
    }

    val currentPanMode by rememberUpdatedState(isPanInspectMode)
    val currentUsesBrush by rememberUpdatedState(usesBrush)
    val currentFrame by rememberUpdatedState(frame)
    val currentOnTapOrPaint by rememberUpdatedState(onMapTapOrPaint)
    val cityLabelCache =
      remember(density) {
        object : LinkedHashMap<String, TextLayoutResult>(64, 0.75f, true) {
          override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextLayoutResult>?): Boolean {
            return size > 128
          }
        }
      }

    Canvas(
      modifier =
        Modifier.fillMaxSize().pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var isMultiTouch = false
            var movedPastSlop = false
            var brushActivated = false
            val startPos = down.position

            do {
              val event = awaitPointerEvent()
              val activePointers = event.changes.count { it.pressed }
              if (activePointers >= 2) {
                isMultiTouch = true
                lastBrushTile = null
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)

                val oldTilePx = tilePx
                val newTilePx = (oldTilePx * zoomChange).coerceIn(2.5f, 44f)
                val ratio = newTilePx / oldTilePx

                val rawX = centroid.x - (centroid.x - offsetX) * ratio + panChange.x
                val rawY = centroid.y - (centroid.y - offsetY) * ratio + panChange.y
                tilePx = newTilePx
                offsetX = clampOffsetX(rawX, currentFrame.mapWidth, newTilePx, viewW)
                offsetY = clampOffsetY(rawY, currentFrame.mapHeight, newTilePx, viewH)

                event.changes.forEach { if (it.positionChanged()) it.consume() }
              } else if (activePointers == 1 && !isMultiTouch) {
                val change = event.changes.firstOrNull { it.pressed } ?: event.changes.firstOrNull() ?: continue
                val distFromStart = hypot(change.position.x - startPos.x, change.position.y - startPos.y)
                if (distFromStart > 8f) movedPastSlop = true

                if (currentPanMode) {
                  val pan = event.calculatePan()
                  offsetX = clampOffsetX(offsetX + pan.x, currentFrame.mapWidth, tilePx, viewW)
                  offsetY = clampOffsetY(offsetY + pan.y, currentFrame.mapHeight, tilePx, viewH)
                  if (change.positionChanged()) change.consume()
                } else if (currentUsesBrush && movedPastSlop) {
                  if (!brushActivated) {
                    brushActivated = true
                    val sx = ((startPos.x - offsetX) / tilePx).toInt()
                    val sy = ((startPos.y - offsetY) / tilePx).toInt()
                    if (sx in 0 until currentFrame.mapWidth && sy in 0 until currentFrame.mapHeight) {
                      lastBrushTile = sx to sy
                      currentOnTapOrPaint(sx, sy, false)
                    }
                  }
                  val tx = ((change.position.x - offsetX) / tilePx).toInt()
                  val ty = ((change.position.y - offsetY) / tilePx).toInt()
                  if (tx in 0 until currentFrame.mapWidth && ty in 0 until currentFrame.mapHeight) {
                    if (lastBrushTile != (tx to ty)) {
                      lastBrushTile = tx to ty
                      currentOnTapOrPaint(tx, ty, true)
                    }
                  }
                  if (change.positionChanged()) change.consume()
                }
              }
            } while (event.changes.any { it.pressed })

            if (!isMultiTouch && !movedPastSlop) {
              val tx = ((startPos.x - offsetX) / tilePx).toInt()
              val ty = ((startPos.y - offsetY) / tilePx).toInt()
              currentOnTapOrPaint(tx, ty, false)
            }
            lastBrushTile = null
          }
        }
    ) {
      val mapPxW = (frame.mapWidth * tilePx).roundToInt().coerceAtLeast(1)
      val mapPxH = (frame.mapHeight * tilePx).roundToInt().coerceAtLeast(1)
      val ox = offsetX.roundToInt()
      val oy = offsetY.roundToInt()

      // 1. Crisp 16-bit Terrain Bitmap
      val bmp = frame.terrainBitmap
      if (bmp != null) {
        drawImage(
          image = bmp,
          dstOffset = IntOffset(ox, oy),
          dstSize = IntSize(mapPxW, mapPxH),
          filterQuality = FilterQuality.None,
        )
      }

      drawRect(
        color = Color(0xFF33476E),
        topLeft = Offset(offsetX, offsetY),
        size = Size(frame.mapWidth * tilePx, frame.mapHeight * tilePx),
        style = Stroke(width = 2f),
      )

      val nationColorMap = frame.nations.associate { it.id to Color(it.colorArgb) }
      val nightAlpha = frame.nightIntensity

      // 2. Detailed Pixel Buildings + PVO/EW Ranges
      drawBuildings(frame, offsetX, offsetY, tilePx, nationColorMap, nightAlpha, animTick)

      // 3. Wildlife (Deer, Wolves, Birds)
      drawAnimals(frame, offsetX, offsetY, tilePx, animTick)

      // 4. Citizens (Profession-styled with stride animation)
      drawCitizens(frame, offsetX, offsetY, tilePx, nationColorMap, animTick)

      // 5. Military Units (Infantry, Tanks, Trucks, Mobile PVO, Naval Gunboats)
      drawMilitaryUnits(frame, offsetX, offsetY, tilePx, nationColorMap, animTick)

      // 6. Civilian Vehicles
      drawVehicles(frame, offsetX, offsetY, tilePx, nightAlpha, animTick)

      // 7. City Air Raid Radar Rings
      for (city in frame.cities) {
        if (!city.underAirAlert) continue
        val cx = offsetX + (city.centerX + 0.5f) * tilePx
        val cy = offsetY + (city.centerY + 0.5f) * tilePx
        if (cx < -160f || cy < -160f || cx > size.width + 160f || cy > size.height + 160f) continue
        val pulse = ((animTick % 24) / 24f)
        val maxR = tilePx * 7.5f
        drawCircle(
          color = Color(0xFFEF4444).copy(alpha = (1f - pulse) * 0.7f),
          radius = maxR * pulse,
          center = Offset(cx, cy),
          style = Stroke(width = 2.5f),
        )
        drawCircle(
          color = Color(0xFFF59E0B).copy(alpha = 0.35f),
          radius = tilePx * 4.5f,
          center = Offset(cx, cy),
          style = Stroke(width = 1.5f),
        )
      }

      // 8. Day / Night & Dawn / Dusk Atmospheric Lighting
      if (nightAlpha > 0.02f) {
        drawRect(
          color = Color(0xFF070D21).copy(alpha = nightAlpha * 0.52f),
          topLeft = Offset(offsetX, offsetY),
          size = Size(frame.mapWidth * tilePx, frame.mapHeight * tilePx),
        )
      } else if (frame.dawnWarmth > 0.05f && frame.graphicsQuality != GraphicsQuality.LOW) {
        drawRect(
          color = Color(0xFFF97316).copy(alpha = frame.dawnWarmth * 0.14f),
          topLeft = Offset(offsetX, offsetY),
          size = Size(frame.mapWidth * tilePx, frame.mapHeight * tilePx),
        )
      }

      // 9. Distinct Pixel Models for All 9 Air Object Types
      drawAirObjects(frame, offsetX, offsetY, tilePx, animTick)

      // 10. Particles (Explosions, Interception Flashes, Shockwaves, Smoke, Lightning)
      for (p in frame.particles) {
        val px = offsetX + p.x * tilePx
        val py = offsetY + p.y * tilePx
        if (px < -60f || py < -60f || px > size.width + 60f || py > size.height + 60f) continue
        val ratio = (p.life / p.maxLife).coerceIn(0f, 1f)
        val pColor = Color(p.colorArgb).copy(alpha = ratio)
        when (p.type) {
          ParticleType.SHOCKWAVE -> {
            val rad = (1f - ratio) * p.size * tilePx
            drawCircle(pColor, radius = rad.coerceAtLeast(2f), center = Offset(px, py), style = Stroke(width = 3f))
          }
          ParticleType.INTERCEPT_FLASH -> {
            val rad = (1f - ratio) * p.size * tilePx * 1.4f
            drawCircle(Color.White.copy(alpha = ratio), radius = rad * 0.45f, center = Offset(px, py))
            drawCircle(pColor, radius = rad.coerceAtLeast(3f), center = Offset(px, py), style = Stroke(width = 3.2f))
          }
          ParticleType.LIGHTNING -> {
            drawLine(
              color = pColor,
              start = Offset(px - tilePx * 2f, py - tilePx * 10f),
              end = Offset(px, py),
              strokeWidth = 4f,
            )
          }
          ParticleType.EMP_WAVE -> {
            val rad = (1f - ratio) * p.size * tilePx
            drawCircle(
              color = pColor,
              radius = rad.coerceAtLeast(3f),
              center = Offset(px, py),
              style = Stroke(width = 3.5f),
            )
          }
          ParticleType.RADIATION_GLOW -> {
            val pulse = 0.75f + 0.25f * sin(animTick * 0.35f + p.id)
            val rad = (p.size * tilePx * pulse).coerceAtLeast(4f)
            drawCircle(
              color = pColor.copy(alpha = ratio * 0.35f),
              radius = rad,
              center = Offset(px, py),
            )
          }
          else -> {
            val sz = (p.size * tilePx * 0.45f).coerceAtLeast(3f)
            drawRect(pColor, Offset(px - sz * 0.5f, py - sz * 0.5f), Size(sz, sz))
          }
        }
      }

      // 11. Weather Overlay
      if (frame.weather == WeatherType.RAIN || frame.weather == WeatherType.STORM) {
        val dropCount = if (frame.weather == WeatherType.STORM) 95 else 55
        val rainColor = Color(0x88BAE6FD)
        for (i in 0 until dropCount) {
          val rx = ((i * 137 + animTick * 17) % size.width.toInt().coerceAtLeast(10)).toFloat()
          val ry = ((i * 251 + animTick * 31) % size.height.toInt().coerceAtLeast(10)).toFloat()
          drawLine(rainColor, Offset(rx, ry), Offset(rx - 7f, ry + 18f), strokeWidth = 1.6f)
        }
      } else if (frame.weather == WeatherType.FOG) {
        for (i in 0 until 8) {
          val fx = ((i * 240 + animTick * 2) % (size.width + 200f)) - 100f
          val fy = (i * 130f) % size.height
          drawCircle(Color(0x28CBD5E1), radius = 150f, center = Offset(fx, fy))
        }
      }

      // 12. City Labels
      if (tilePx >= 4.5f) {
        drawCityLabels(frame, offsetX, offsetY, tilePx, textMeasurer, cityLabelCache, animTick)
      }

      // 13. Brush Cursor Outline
      val brushTile = lastBrushTile
      if (!isPanInspectMode && usesBrush && brushTile != null) {
        val cx = offsetX + (brushTile.first + 0.5f) * tilePx
        val cy = offsetY + (brushTile.second + 0.5f) * tilePx
        drawCircle(
          color = Color(0xCC38BDF8),
          radius = (brushRadius + 0.5f) * tilePx,
          center = Offset(cx, cy),
          style = Stroke(width = 2f),
        )
      }
    }
  }
}

private fun clampOffsetX(rawX: Float, mapWidth: Int, tilePx: Float, viewW: Float): Float {
  val mapPxW = mapWidth * tilePx
  val minVisibleW = (mapPxW * 0.25f).coerceAtMost(viewW * 0.6f)
  val minX = minVisibleW - mapPxW
  val maxX = viewW - minVisibleW
  return if (minX <= maxX) rawX.coerceIn(minX, maxX) else (viewW - mapPxW) / 2f
}

private fun clampOffsetY(rawY: Float, mapHeight: Int, tilePx: Float, viewH: Float): Float {
  val mapPxH = mapHeight * tilePx
  val minVisibleH = (mapPxH * 0.25f).coerceAtMost(viewH * 0.6f)
  val minY = minVisibleH - mapPxH
  val maxY = viewH - minVisibleH
  return if (minY <= maxY) rawY.coerceIn(minY, maxY) else (viewH - mapPxH) / 2f
}

private val CityLabelTextStyle =
  TextStyle(fontFamily = PixelDisplayFont, fontWeight = FontWeight.Bold, fontSize = 11.sp)

private fun DrawScope.drawCityLabels(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  textMeasurer: TextMeasurer,
  labelCache: MutableMap<String, TextLayoutResult>,
  animTick: Int,
) {
  val nationColors = frame.nations.associate { it.id to Color(it.colorArgb) }
  for (city in frame.cities) {
    val cx = offsetX + (city.centerX + 0.5f) * tilePx
    val cy = offsetY + (city.centerY - 1.2f) * tilePx
    if (cx < -60f || cy < -40f || cx > size.width + 60f || cy > size.height + 40f) continue

    val prefix = if (city.underAirAlert && (animTick % 12 < 6)) "⚠ " else if (city.isCapital) "★ " else ""
    val label = "$prefix${city.name}"
    val measured =
      labelCache.getOrPut(label) {
        textMeasurer.measure(
          text = label,
          style = CityLabelTextStyle,
        )
      }
    val bgW = measured.size.width + 10f
    val bgH = measured.size.height + 4f
    val topLeft = Offset(cx - bgW / 2f, cy - bgH)

    val bgColor =
      if (city.underAirAlert) Color(0xDD991B1B)
      else Color(0xBB0B101B)
    val borderColor =
      if (city.underAirAlert) Color(0xFFEF4444)
      else nationColors[city.nationId] ?: Color(0xFF64748B)

    drawRect(bgColor, topLeft, Size(bgW, bgH))
    drawRect(borderColor, topLeft, Size(bgW, bgH), style = Stroke(width = 1.5f))
    drawText(
      textLayoutResult = measured,
      color = Color(0xFFF8FAFC),
      topLeft = Offset(topLeft.x + 5f, topLeft.y + 2f),
    )
  }
}
