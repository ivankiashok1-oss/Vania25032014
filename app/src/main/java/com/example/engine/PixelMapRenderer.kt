package com.example.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.GraphicsQuality
import com.example.model.TerrainType

class PixelMapRenderer {
  private var terrainBuffers: Array<Bitmap?> = arrayOfNulls(2)
  private var bufferIdx = 0
  private var terrainPixels: IntArray = IntArray(0)
  private var lastBw = 0
  private var lastBh = 0

  private var minimapBuffers: Array<Bitmap?> = arrayOfNulls(2)
  private var minimapIdx = 0
  private var minimapPixels: IntArray = IntArray(0)

  fun renderTerrainBitmap(
    state: WorldState,
    showTerritoryBorders: Boolean,
    waterAnimPhase: Int,
    quality: GraphicsQuality = GraphicsQuality.HIGH,
  ): Bitmap {
    val w = state.width
    val h = state.height
    val sub =
      when {
        quality == GraphicsQuality.LOW -> 2
        w >= 192 -> 2
        else -> 4
      }
    val bw = w * sub
    val bh = h * sub
    val totalPixels = bw * bh

    if (lastBw != bw || lastBh != bh || terrainBuffers[0] == null) {
      terrainBuffers[0] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
      terrainBuffers[1] = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
      terrainPixels = IntArray(totalPixels)
      lastBw = bw
      lastBh = bh
    }

    val nationColors = HashMap<Int, Int>(state.nations.size)
    for (n in state.nations) {
      nationColors[n.id] = n.colorArgb
    }

    val px = terrainPixels
    val terrainArr = state.terrain
    val ownerArr = state.nationOwner
    val fireArr = state.fireGrid
    val radArr = state.radiationGrid
    val varArr = state.variationGrid

    for (ty in 0 until h) {
      val rowOffset = ty * w
      for (tx in 0 until w) {
        val i = rowOffset + tx
        val tOrd = terrainArr[i]
        val v = varArr[i].toInt() and 0x0F
        val owner = ownerArr[i]
        val fire = fireArr[i]
        val rad = radArr[i]

        val baseX = tx * sub
        val baseY = ty * sub

        val leftDiff = tx > 0 && ownerArr[i - 1] != owner
        val rightDiff = tx < w - 1 && ownerArr[i + 1] != owner
        val topDiff = ty > 0 && ownerArr[i - w] != owner
        val botDiff = ty < h - 1 && ownerArr[i + w] != owner

        val nColor = if (owner > 0) nationColors[owner] ?: 0 else 0

        val roadMask: Int
        val roadBridge: Boolean
        if (tOrd == TerrainType.ROAD.ordinal) {
          val nConn = ty > 0 && isRoadConnectable(terrainArr[i - w])
          val eConn = tx < w - 1 && isRoadConnectable(terrainArr[i + 1])
          val sConn = ty < h - 1 && isRoadConnectable(terrainArr[i + w])
          val wConn = tx > 0 && isRoadConnectable(terrainArr[i - 1])
          roadMask =
            (if (nConn) 1 else 0) or
              (if (eConn) 2 else 0) or
              (if (sConn) 4 else 0) or
              (if (wConn) 8 else 0)
          val nWater = ty > 0 && isWaterOrRiverOrd(terrainArr[i - w])
          val eWater = tx < w - 1 && isWaterOrRiverOrd(terrainArr[i + 1])
          val sWater = ty < h - 1 && isWaterOrRiverOrd(terrainArr[i + w])
          val wWater = tx > 0 && isWaterOrRiverOrd(terrainArr[i - 1])
          roadBridge = (nWater && sWater) || (eWater && wWater) || ((if (nWater) 1 else 0) + (if (eWater) 1 else 0) + (if (sWater) 1 else 0) + (if (wWater) 1 else 0) >= 2)
        } else {
          roadMask = 0
          roadBridge = false
        }

        for (sy in 0 until sub) {
          val py = (baseY + sy) * bw
          val mappedSy = if (sub == 2) sy * 2 else sy
          for (sx in 0 until sub) {
            val mappedSx = if (sub == 2) sx * 2 else sx
            var c =
              if (tOrd == TerrainType.ROAD.ordinal) {
                sampleRoadSubpixel(sx, sy, sub, tx, ty, roadMask, roadBridge)
              } else {
                sampleSubpixel(tOrd, mappedSx, mappedSy, v, tx, ty, waterAnimPhase)
              }

            if (tOrd == TerrainType.WATER.ordinal) {
              val nearLand =
                (tx > 0 && !isWaterOrd(terrainArr[i - 1]) && sx == 0) ||
                  (tx < w - 1 && !isWaterOrd(terrainArr[i + 1]) && sx == sub - 1) ||
                  (ty > 0 && !isWaterOrd(terrainArr[i - w]) && sy == 0) ||
                  (ty < h - 1 && !isWaterOrd(terrainArr[i + w]) && sy == sub - 1)
              if (nearLand && ((tx + ty + waterAnimPhase) and 1) == 0) {
                c = 0xFF7DD3FC.toInt()
              }
            }

            if (fire > 0) {
              c = if (((sx + sy + waterAnimPhase) and 1) == 0) 0xFFEA580C.toInt() else 0xFFFACC15.toInt()
            } else if (rad > 0) {
              val radAlpha = ((rad / 100f) * 0.52f).coerceIn(0.14f, 0.56f)
              val shimmer = ((tx + ty + sx + sy + waterAnimPhase) and 3) == 0
              val radColor = if (shimmer) 0xFF84CC16.toInt() else 0xFF22C55E.toInt()
              c = blendColor(c, radColor, radAlpha)
              if (showTerritoryBorders && nColor != 0) {
                val isEdge =
                  (sx == 0 && leftDiff) ||
                    (sx == sub - 1 && rightDiff) ||
                    (sy == 0 && topDiff) ||
                    (sy == sub - 1 && botDiff)
                if (isEdge) c = blendColor(c, nColor, 0.78f)
              }
            } else if (showTerritoryBorders && nColor != 0) {
              val isEdge =
                (sx == 0 && leftDiff) ||
                  (sx == sub - 1 && rightDiff) ||
                  (sy == 0 && topDiff) ||
                  (sy == sub - 1 && botDiff)
              c =
                if (isEdge) {
                  blendColor(c, nColor, 0.85f)
                } else {
                  blendColor(c, nColor, 0.22f)
                }
            }

            px[py + baseX + sx] = c
          }
        }
      }
    }

    bufferIdx = 1 - bufferIdx
    val bmp = terrainBuffers[bufferIdx]!!
    bmp.setPixels(px, 0, bw, 0, 0, bw, bh)
    return bmp
  }

  fun renderMinimapBitmap(state: WorldState): Bitmap {
    val w = state.width
    val h = state.height
    if (minimapBuffers[0] == null || minimapBuffers[0]?.width != w || minimapBuffers[0]?.height != h) {
      minimapBuffers[0] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      minimapBuffers[1] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      minimapPixels = IntArray(w * h)
    }

    val nationColors = HashMap<Int, Int>(state.nations.size)
    for (n in state.nations) {
      nationColors[n.id] = n.colorArgb
    }

    val px = minimapPixels
    for (i in 0 until state.size) {
      val tOrd = state.terrain[i]
      val owner = state.nationOwner[i]
      var c =
        when (tOrd) {
          TerrainType.DEEP_WATER.ordinal -> 0xFF0E3A66.toInt()
          TerrainType.WATER.ordinal -> 0xFF1D6FA5.toInt()
          TerrainType.RIVER.ordinal -> 0xFF38BDF8.toInt()
          TerrainType.SAND.ordinal -> 0xFFE6C87A.toInt()
          TerrainType.GRASS.ordinal -> 0xFF4CA64C.toInt()
          TerrainType.FOREST.ordinal -> 0xFF276B35.toInt()
          TerrainType.MOUNTAIN.ordinal -> 0xFF8B95A5.toInt()
          TerrainType.ROAD.ordinal -> 0xFF64748B.toInt()
          TerrainType.CITY_ZONE.ordinal -> 0xFFA3B1C6.toInt()
          TerrainType.RUINS.ordinal -> 0xFF3F2E2A.toInt()
          else -> 0xFF4CA64C.toInt()
        }
      if (state.fireGrid[i] > 0) {
        c = 0xFFF97316.toInt()
      } else if (state.radiationGrid[i] > 10) {
        c = blendColor(c, 0xFF84CC16.toInt(), 0.65f)
      } else if (owner > 0) {
        val nc = nationColors[owner]
        if (nc != null) c = blendColor(c, nc, 0.55f)
      }
      px[i] = c
    }
    for (city in state.cities) {
      if (state.inBounds(city.centerX, city.centerY)) {
        px[state.idx(city.centerX, city.centerY)] =
          if (city.underAirAlert) 0xFFEF4444.toInt() else 0xFFFFFFFF.toInt()
      }
    }
    for (unit in state.militaryUnits) {
      val ux = unit.x.toInt()
      val uy = unit.y.toInt()
      if (state.inBounds(ux, uy)) {
        px[state.idx(ux, uy)] = 0xFFFACC15.toInt()
      }
    }

    minimapIdx = 1 - minimapIdx
    val bmp = minimapBuffers[minimapIdx]!!
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    return bmp
  }

  private fun isWaterOrd(ord: Int): Boolean {
    return ord == TerrainType.WATER.ordinal || ord == TerrainType.DEEP_WATER.ordinal
  }

  private fun isWaterOrRiverOrd(ord: Int): Boolean {
    return ord == TerrainType.WATER.ordinal || ord == TerrainType.DEEP_WATER.ordinal || ord == TerrainType.RIVER.ordinal
  }

  private fun isRoadConnectable(ord: Int): Boolean {
    return ord == TerrainType.ROAD.ordinal || ord == TerrainType.CITY_ZONE.ordinal
  }

  private fun sampleRoadSubpixel(
    sx: Int,
    sy: Int,
    sub: Int,
    tx: Int,
    ty: Int,
    mask: Int,
    isBridge: Boolean,
  ): Int {
    val hasN = (mask and 1) != 0
    val hasE = (mask and 2) != 0
    val hasS = (mask and 4) != 0
    val hasW = (mask and 8) != 0

    if (sub == 2) {
      val inRoadBody =
        (sx == 1 && sy == 1) ||
          (sx == 0 && sy == 1 && (hasW || (!hasN && !hasS && !hasE))) ||
          (sx == 1 && sy == 0 && (hasN || (!hasE && !hasW && !hasS)))
      val asphalt = if (isBridge) 0xFF785A3C.toInt() else 0xFF475569.toInt()
      val rail = if (isBridge) 0xFFB45309.toInt() else 0xFF2D3646.toInt()
      return if (inRoadBody) {
        if (((tx + ty) and 1) == 0 && sx == 1 && sy == 1) 0xFFCBD5E1.toInt() else asphalt
      } else {
        rail
      }
    }

    // 4x4 subpixel autotiled road: center core is sx in 1..2, sy in 1..2
    val inCenter = sx in 1..2 && sy in 1..2
    val inNorthArm = hasN && sy == 0 && sx in 1..2
    val inSouthArm = hasS && sy == 3 && sx in 1..2
    val inWestArm = hasW && sx == 0 && sy in 1..2
    val inEastArm = hasE && sx == 3 && sy in 1..2

    val onAsphalt = inCenter || inNorthArm || inSouthArm || inWestArm || inEastArm
    if (onAsphalt) {
      val connCount = (if (hasN) 1 else 0) + (if (hasE) 1 else 0) + (if (hasS) 1 else 0) + (if (hasW) 1 else 0)
      val isVertLane = (hasN || hasS) && sx == 1 && ((ty * 2 + sy) and 1) == 0
      val isHorizLane = (hasE || hasW) && sy == 1 && ((tx * 2 + sx) and 1) == 0
      val isJunctionDot = connCount >= 3 && sx == 1 && sy == 1
      val isBridgePlank = isBridge && ((sx + sy) and 1) == 0
      return when {
        isBridge -> {
          when {
            isJunctionDot || isVertLane || isHorizLane -> 0xFFFDE68A.toInt()
            isBridgePlank -> 0xFF8C6239.toInt()
            else -> 0xFF734F2D.toInt()
          }
        }
        isJunctionDot || (connCount <= 2 && (isVertLane || isHorizLane)) -> 0xFFE2E8F0.toInt()
        (sx + sy + tx + ty) and 3 == 0 -> 0xFF3E4756.toInt()
        else -> 0xFF4B5563.toInt()
      }
    }

    // Curbs & shoulders on non-connected boundaries (bridges show water on outer corners & wooden/steel guardrails)
    val touchesRoadBody =
      (sx == 0 && (hasN && sy == 0 || hasS && sy == 3 || inCenter)) ||
        (sx == 3 && (hasN && sy == 0 || hasS && sy == 3 || inCenter)) ||
        (sy == 0 && (hasW && sx == 0 || hasE && sx == 3 || inCenter)) ||
        (sy == 3 && (hasW && sx == 0 || hasE && sx == 3 || inCenter))

    if (isBridge) {
      return if (touchesRoadBody) 0xFFB45309.toInt() else 0xFF217AB5.toInt()
    }
    return if (touchesRoadBody || (sx in 1..2) || (sy in 1..2)) 0xFF2D3542.toInt() else 0xFF3F6A3F.toInt()
  }

  private fun sampleSubpixel(
    tOrd: Int,
    sx: Int,
    sy: Int,
    v: Int,
    tx: Int,
    ty: Int,
    animPhase: Int,
  ): Int {
    return when (tOrd) {
      TerrainType.DEEP_WATER.ordinal -> {
        val shimmer = ((tx + ty + sx + animPhase) % 11 == 0) && sy == 1
        if (shimmer) 0xFF1A5A96.toInt() else if (((sx + sy + v) and 3) == 0) 0xFF0C3159.toInt() else 0xFF0F3D6E.toInt()
      }
      TerrainType.WATER.ordinal -> {
        val shimmer = ((tx * 3 + ty + sx + animPhase) % 9 == 0) && sy == 2
        if (shimmer) 0xFF53B6E8.toInt() else if (((sx + sy) and 1) == 0) 0xFF1D6FA5.toInt() else 0xFF217AB5.toInt()
      }
      TerrainType.RIVER.ordinal -> {
        val flow = ((tx + ty + sy + animPhase) % 4 == 0)
        if (flow) 0xFF7DD3FC.toInt() else 0xFF2B99D8.toInt()
      }
      TerrainType.SAND.ordinal -> {
        when {
          ((sx * 3 + sy + v) and 7) == 0 -> 0xFFD4B062.toInt()
          ((sx + sy * 2 + v) and 5) == 0 -> 0xFFF2D98D.toInt()
          else -> 0xFFE6C87A.toInt()
        }
      }
      TerrainType.GRASS.ordinal -> {
        when {
          v == 1 && sx == 2 && sy == 1 -> 0xFFFDE047.toInt()
          v == 2 && sx == 1 && sy == 2 -> 0xFF86EFAC.toInt()
          ((sx + sy * 3 + v) and 3) == 0 -> 0xFF429642.toInt()
          ((sx * 2 + sy + v) and 5) == 0 -> 0xFF5AB854.toInt()
          else -> 0xFF4CA64C.toInt()
        }
      }
      TerrainType.FOREST.ordinal -> {
        val isTrunk = sy == 3 && (sx == 1 || sx == 2)
        val isTop = sy == 0 && (sx == 1 || sx == 2)
        val isMid = (sy == 1 || sy == 2)
        when {
          isTrunk -> 0xFF4A321F.toInt()
          isTop -> 0xFF3BA148.toInt()
          isMid && sx == 0 -> 0xFF1B5227.toInt()
          isMid -> if ((v and 1) == 0) 0xFF277337.toInt() else 0xFF2E8540.toInt()
          else -> 0xFF2E6E33.toInt()
        }
      }
      TerrainType.MOUNTAIN.ordinal -> {
        when {
          sy == 0 && (sx == 1 || sx == 2) -> 0xFFF1F5F9.toInt()
          sy == 1 && sx == 2 -> 0xFFCBD5E1.toInt()
          sx == 0 || sy == 3 -> 0xFF475569.toInt()
          else -> 0xFF64748B.toInt()
        }
      }
      TerrainType.ROAD.ordinal -> 0xFF4B5563.toInt()
      TerrainType.CITY_ZONE.ordinal -> {
        when {
          sx == 0 || sy == 0 -> 0xFF64748B.toInt()
          sx == 3 || sy == 3 -> 0xFF4F5D73.toInt()
          ((sx + sy + tx + ty) and 1) == 0 -> 0xFF8796AC.toInt()
          else -> 0xFF7B8BA1.toInt()
        }
      }
      TerrainType.RUINS.ordinal -> {
        when {
          ((sx + sy + v) and 3) == 0 -> 0xFF1C1917.toInt()
          ((sx * 2 + sy) and 2) == 0 -> 0xFF57534E.toInt()
          else -> 0xFF3F332E.toInt()
        }
      }
      else -> 0xFF4CA64C.toInt()
    }
  }

  private fun blendColor(base: Int, overlay: Int, factor: Float): Int {
    val inv = 1f - factor
    val r = (Color.red(base) * inv + Color.red(overlay) * factor).toInt().coerceIn(0, 255)
    val g = (Color.green(base) * inv + Color.green(overlay) * factor).toInt().coerceIn(0, 255)
    val b = (Color.blue(base) * inv + Color.blue(overlay) * factor).toInt().coerceIn(0, 255)
    return Color.rgb(r, g, b)
  }
}
