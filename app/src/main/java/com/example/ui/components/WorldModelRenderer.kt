package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.model.AirObjectType
import com.example.model.AnimalType
import com.example.model.BuildingType
import com.example.model.CitizenState
import com.example.model.Profession
import com.example.model.UnitType
import com.example.model.VehicleType
import com.example.ui.RenderFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal fun DrawScope.drawAnimals(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  animTick: Int,
) {
  if (tilePx < 3.5f) return
  val lowDetail = tilePx < 5.5f
  for (a in frame.animals) {
    val sx = offsetX + a.x * tilePx
    val sy = offsetY + a.y * tilePx
    if (sx < -20f || sy < -20f || sx > size.width + 20f || sy > size.height + 20f) continue

    val s = (tilePx * 0.42f).coerceAtLeast(3f)
    if (lowDetail) {
      val col =
        when (a.type) {
          AnimalType.DEER -> Color(0xFFD97706)
          AnimalType.WOLF -> Color(0xFF94A3B8)
          AnimalType.BIRD -> Color(0xFFF8FAFC)
        }
      drawRect(col, Offset(sx - s * 0.4f, sy - s * 0.35f), Size(s * 0.8f, s * 0.65f))
      continue
    }

    val facing = if (a.vx >= 0f) 1f else -1f
    val stride = sin(animTick * 0.4f + a.id) * s * 0.14f

    when (a.type) {
      AnimalType.DEER -> {
        drawRect(Color(0x44000000), Offset(sx - s * 0.45f, sy + s * 0.25f), Size(s * 0.9f, s * 0.22f))
        // Legs
        drawRect(Color(0xFF92400E), Offset(sx - s * 0.32f, sy + s * 0.05f + stride), Size(s * 0.14f, s * 0.32f))
        drawRect(Color(0xFF92400E), Offset(sx + s * 0.18f, sy + s * 0.05f - stride), Size(s * 0.14f, s * 0.32f))
        // Body + white tail
        drawRect(Color(0xFFD97706), Offset(sx - s * 0.38f, sy - s * 0.22f), Size(s * 0.76f, s * 0.34f))
        drawRect(Color(0xFFF8FAFC), Offset(sx - facing * s * 0.44f, sy - s * 0.24f), Size(s * 0.14f, s * 0.14f))
        // Neck, head & antlers
        val hx = sx + facing * s * 0.28f
        drawRect(Color(0xFFB45309), Offset(hx - s * 0.1f, sy - s * 0.48f), Size(s * 0.22f, s * 0.3f))
        drawRect(Color(0xFFD97706), Offset(hx - s * 0.12f, sy - s * 0.58f), Size(s * 0.3f, s * 0.18f))
        drawRect(Color(0xFFFDE68A), Offset(hx - s * 0.14f, sy - s * 0.8f), Size(s * 0.1f, s * 0.22f))
        drawRect(Color(0xFFFDE68A), Offset(hx + s * 0.06f, sy - s * 0.8f), Size(s * 0.1f, s * 0.22f))
      }
      AnimalType.WOLF -> {
        drawRect(Color(0x44000000), Offset(sx - s * 0.42f, sy + s * 0.22f), Size(s * 0.84f, s * 0.2f))
        // Legs
        drawRect(Color(0xFF475569), Offset(sx - s * 0.3f, sy + s * 0.05f - stride), Size(s * 0.14f, s * 0.26f))
        drawRect(Color(0xFF475569), Offset(sx + s * 0.16f, sy + s * 0.05f + stride), Size(s * 0.14f, s * 0.26f))
        // Bushy tail + body
        drawRect(Color(0xFF64748B), Offset(sx - facing * s * 0.5f, sy - s * 0.16f), Size(s * 0.22f, s * 0.14f))
        drawRect(Color(0xFF94A3B8), Offset(sx - s * 0.36f, sy - s * 0.2f), Size(s * 0.72f, s * 0.3f))
        // Head + pointed ears
        val hx = sx + facing * s * 0.28f
        drawRect(Color(0xFFCBD5E1), Offset(hx - s * 0.14f, sy - s * 0.34f), Size(s * 0.34f, s * 0.22f))
        drawRect(Color(0xFF475569), Offset(hx - s * 0.08f, sy - s * 0.44f), Size(s * 0.1f, s * 0.12f))
        drawRect(Color(0xFF475569), Offset(hx + s * 0.06f, sy - s * 0.44f), Size(s * 0.1f, s * 0.12f))
      }
      AnimalType.BIRD -> {
        val flap = sin(animTick * 0.55f + a.id) * s * 0.28f
        drawRect(Color(0x33000000), Offset(sx - s * 0.22f, sy + s * 0.45f), Size(s * 0.44f, s * 0.14f))
        drawRect(Color(0xFFF8FAFC), Offset(sx - s * 0.18f, sy - s * 0.12f), Size(s * 0.36f, s * 0.18f))
        drawLine(
          color = Color(0xFFE2E8F0),
          start = Offset(sx - s * 0.48f, sy - flap),
          end = Offset(sx, sy),
          strokeWidth = (s * 0.16f).coerceAtLeast(1.5f),
        )
        drawLine(
          color = Color(0xFFE2E8F0),
          start = Offset(sx, sy),
          end = Offset(sx + s * 0.48f, sy - flap),
          strokeWidth = (s * 0.16f).coerceAtLeast(1.5f),
        )
      }
    }
  }
}

internal fun DrawScope.drawCitizens(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  nationColors: Map<Int, Color>,
  animTick: Int,
) {
  val lowDetail = tilePx < 4.5f
  for (c in frame.citizens) {
    val sx = offsetX + c.x * tilePx
    val sy = offsetY + c.y * tilePx
    if (sx < -24f || sy < -24f || sx > size.width + 24f || sy > size.height + 24f) continue

    val s = (tilePx * 0.38f).coerceAtLeast(3f)
    val bodyColor = nationColors[c.nationId] ?: Color(0xFF94A3B8)
    if (lowDetail) {
      drawRect(bodyColor, Offset(sx - s * 0.4f, sy - s * 0.4f), Size(s * 0.8f, s * 0.8f))
      continue
    }

    val bob = sin(c.animPhase) * (s * 0.18f)
    val legSwing = sin(c.animPhase) * (s * 0.16f)

    // Shadow
    drawRect(Color(0x55000000), Offset(sx - s * 0.45f, sy + s * 0.35f), Size(s * 0.9f, s * 0.26f))
    // Animated boots/legs
    drawRect(Color(0xFF1E293B), Offset(sx - s * 0.32f, sy + s * 0.14f + legSwing * 0.4f), Size(s * 0.25f, s * 0.28f))
    drawRect(Color(0xFF1E293B), Offset(sx + s * 0.08f, sy + s * 0.14f - legSwing * 0.4f), Size(s * 0.25f, s * 0.28f))

    // Torso coat/vest
    val coatColor =
      when (c.profession) {
        Profession.MEDIC -> Color(0xFFF8FAFC)
        Profession.WORKER -> Color(0xFFEA580C)
        else -> bodyColor
      }
    drawRect(coatColor, Offset(sx - s * 0.4f, sy - s * 0.32f + bob), Size(s * 0.8f, s * 0.52f))
    // Belt/accent
    drawRect(Color(0xFF334155), Offset(sx - s * 0.4f, sy + s * 0.05f + bob), Size(s * 0.8f, s * 0.12f))

    // Head
    drawRect(Color(0xFFFDBA74), Offset(sx - s * 0.26f, sy - s * 0.68f + bob), Size(s * 0.52f, s * 0.38f))

    // Profession headgear & held item
    when (c.profession) {
      Profession.BUILDER -> {
        drawRect(Color(0xFFFACC15), Offset(sx - s * 0.34f, sy - s * 0.85f + bob), Size(s * 0.68f, s * 0.24f))
        drawRect(Color(0xFF94A3B8), Offset(sx + s * 0.36f, sy - s * 0.2f + bob), Size(s * 0.22f, s * 0.16f))
      }
      Profession.FARMER -> {
        drawRect(Color(0xFFEAB308), Offset(sx - s * 0.44f, sy - s * 0.78f + bob), Size(s * 0.88f, s * 0.14f))
        drawRect(Color(0xFFCA8A04), Offset(sx - s * 0.26f, sy - s * 0.92f + bob), Size(s * 0.52f, s * 0.16f))
      }
      Profession.MEDIC -> {
        drawRect(Color(0xFFF8FAFC), Offset(sx - s * 0.3f, sy - s * 0.85f + bob), Size(s * 0.6f, s * 0.22f))
        drawRect(Color(0xFFEF4444), Offset(sx - s * 0.08f, sy - s * 0.82f + bob), Size(s * 0.16f, s * 0.16f))
      }
      Profession.SOLDIER -> {
        drawRect(Color(0xFF475569), Offset(sx - s * 0.32f, sy - s * 0.85f + bob), Size(s * 0.64f, s * 0.24f))
        drawRect(Color(0xFF1E293B), Offset(sx + s * 0.34f, sy - s * 0.28f + bob), Size(s * 0.34f, s * 0.12f))
      }
      Profession.TRADER -> {
        drawRect(Color(0xFF0284C7), Offset(sx - s * 0.3f, sy - s * 0.84f + bob), Size(s * 0.6f, s * 0.2f))
        drawRect(Color(0xFF92400E), Offset(sx + s * 0.34f, sy - s * 0.05f + bob), Size(s * 0.24f, s * 0.26f))
      }
      Profession.WORKER -> {
        drawRect(Color(0xFFFB923C), Offset(sx - s * 0.32f, sy - s * 0.84f + bob), Size(s * 0.64f, s * 0.22f))
      }
      Profession.CIVILIAN -> {
        drawRect(Color(0xFF475569), Offset(sx - s * 0.28f, sy - s * 0.78f + bob), Size(s * 0.56f, s * 0.16f))
      }
    }

    if ((c.state == CitizenState.EVACUATING || c.state == CitizenState.PANIC) && (animTick % 12) < 7) {
      drawRect(Color(0xFFEF4444), Offset(sx - 2f, sy - s * 1.45f), Size(4f, s * 0.45f))
      drawRect(Color(0xFFEF4444), Offset(sx - 2f, sy - s * 0.94f), Size(4f, s * 0.14f))
    }
  }
}

internal fun DrawScope.drawVehicles(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  nightAlpha: Float,
  animTick: Int,
) {
  val lowDetail = tilePx < 4.5f
  for (v in frame.vehicles) {
    val baseX = offsetX + v.x * tilePx
    val baseY = offsetY + v.y * tilePx
    if (baseX < -28f || baseY < -28f || baseX > size.width + 28f || baseY > size.height + 28f) continue

    val dx = v.targetX - v.x
    val dy = v.targetY - v.y
    val dist = hypot(dx, dy)
    val ux = if (dist > 0.001f) dx / dist else 1f
    val uy = if (dist > 0.001f) dy / dist else 0f
    // Right-hand lane offset perpendicular to travel vector
    val laneOffset = tilePx * 0.11f
    val sx = baseX - uy * laneOffset
    val sy = baseY + ux * laneOffset

    val w = (tilePx * 0.64f).coerceAtLeast(5.0f)
    val h = (tilePx * 0.38f).coerceAtLeast(3.2f)
    val isVertical = abs(dy) > abs(dx) * 1.15f
    if (lowDetail) {
      val c =
        when (v.type) {
          VehicleType.CAR -> Color(0xFF38BDF8)
          VehicleType.TRUCK -> Color(0xFFE2E8F0)
          VehicleType.BULLDOZER -> Color(0xFFF59E0B)
          VehicleType.PATROL_JEEP -> Color(0xFF4ADE80)
          VehicleType.AMBULANCE -> Color(0xFFF8FAFC)
        }
      val lw = if (isVertical) h else w
      val lh = if (isVertical) w else h
      drawRect(c, Offset(sx - lw * 0.5f, sy - lh * 0.5f), Size(lw, lh))
      continue
    }

    val rotDeg = if (isVertical) (if (dy >= 0f) 90f else -90f) else 0f
    val dir = if (isVertical || dx >= 0f) 1f else -1f

    withTransform({
      if (rotDeg != 0f) rotate(degrees = rotDeg, pivot = Offset(sx, sy))
    }) {
      // Ground shadow
      drawRect(Color(0x66000000), Offset(sx - w * 0.52f, sy + h * 0.22f), Size(w * 1.04f, h * 0.34f))

      when (v.type) {
        VehicleType.CAR -> {
          // Wheels
          drawRect(Color(0xFF0F172A), Offset(sx - w * 0.38f, sy + h * 0.18f), Size(w * 0.22f, h * 0.28f))
          drawRect(Color(0xFF0F172A), Offset(sx + w * 0.16f, sy + h * 0.18f), Size(w * 0.22f, h * 0.28f))
          // Body + roof cabin
          drawRect(Color(0xFF0284C7), Offset(sx - w * 0.48f, sy - h * 0.22f), Size(w * 0.96f, h * 0.46f))
          drawRect(Color(0xFF38BDF8), Offset(sx - w * 0.28f, sy - h * 0.52f), Size(w * 0.56f, h * 0.34f))
          // Windshield
          drawRect(Color(0xFFBAE6FD), Offset(sx + dir * w * 0.08f - w * 0.1f, sy - h * 0.46f), Size(w * 0.2f, h * 0.24f))
        }
        VehicleType.TRUCK -> {
          // 3 wheels
          drawRect(Color(0xFF0F172A), Offset(sx - w * 0.45f, sy + h * 0.18f), Size(w * 0.2f, h * 0.28f))
          drawRect(Color(0xFF0F172A), Offset(sx - w * 0.15f, sy + h * 0.18f), Size(w * 0.2f, h * 0.28f))
          drawRect(Color(0xFF0F172A), Offset(sx + w * 0.25f, sy + h * 0.18f), Size(w * 0.2f, h * 0.28f))
          // Trailer + Cab
          val trailerX = if (dir > 0) sx - w * 0.52f else sx - w * 0.18f
          val cabX = if (dir > 0) sx + w * 0.18f else sx - w * 0.52f
          drawRect(Color(0xFFE2E8F0), Offset(trailerX, sy - h * 0.52f), Size(w * 0.68f, h * 0.72f))
          drawRect(Color(0xFFCBD5E1), Offset(trailerX + w * 0.06f, sy - h * 0.4f), Size(w * 0.56f, h * 0.12f))
          drawRect(Color(0xFF475569), Offset(cabX, sy - h * 0.36f), Size(w * 0.34f, h * 0.56f))
          drawRect(Color(0xFF7DD3FC), Offset(cabX + (if (dir > 0) w * 0.14f else w * 0.04f), sy - h * 0.28f), Size(w * 0.16f, h * 0.22f))
        }
        VehicleType.BULLDOZER -> {
          // Caterpillar tracks
          drawRect(Color(0xFF1E293B), Offset(sx - w * 0.46f, sy + h * 0.05f), Size(w * 0.84f, h * 0.38f))
          // Cab & exhaust
          drawRect(Color(0xFFF59E0B), Offset(sx - w * 0.38f, sy - h * 0.35f), Size(w * 0.72f, h * 0.44f))
          drawRect(Color(0xFF0F172A), Offset(sx - w * 0.24f, sy - h * 0.62f), Size(w * 0.38f, h * 0.28f))
          drawRect(Color(0xFF78350F), Offset(sx - dir * w * 0.28f, sy - h * 0.68f), Size(w * 0.1f, h * 0.32f))
          // Front steel blade
          val bladeX = if (dir > 0) sx + w * 0.36f else sx - w * 0.54f
          drawRect(Color(0xFF94A3B8), Offset(bladeX, sy - h * 0.28f), Size(w * 0.18f, h * 0.68f))
        }
        VehicleType.PATROL_JEEP -> {
          drawRect(Color(0xFF0F172A), Offset(sx - w * 0.38f, sy + h * 0.18f), Size(w * 0.22f, h * 0.28f))
          drawRect(Color(0xFF0F172A), Offset(sx + w * 0.16f, sy + h * 0.18f), Size(w * 0.22f, h * 0.28f))
          drawRect(Color(0xFF15803D), Offset(sx - w * 0.46f, sy - h * 0.25f), Size(w * 0.92f, h * 0.46f))
          drawRect(Color(0xFF22C55E), Offset(sx - w * 0.28f, sy - h * 0.54f), Size(w * 0.54f, h * 0.32f))
          // Flashing police beacon
          val sirenColor = if ((animTick % 8) < 4) Color(0xFFEF4444) else Color(0xFF38BDF8)
          drawRect(sirenColor, Offset(sx - w * 0.08f, sy - h * 0.68f), Size(w * 0.18f, h * 0.16f))
        }
        VehicleType.AMBULANCE -> {
          drawRect(Color(0xFF0F172A), Offset(sx - w * 0.38f, sy + h * 0.18f), Size(w * 0.22f, h * 0.28f))
          drawRect(Color(0xFF0F172A), Offset(sx + w * 0.16f, sy + h * 0.18f), Size(w * 0.22f, h * 0.28f))
          drawRect(Color(0xFFF8FAFC), Offset(sx - w * 0.5f, sy - h * 0.48f), Size(w, h * 0.68f))
          drawRect(Color(0xFFEF4444), Offset(sx - w * 0.5f, sy + h * 0.02f), Size(w, h * 0.12f))
          // Red cross
          drawRect(Color(0xFFEF4444), Offset(sx - w * 0.06f, sy - h * 0.38f), Size(w * 0.12f, h * 0.34f))
          drawRect(Color(0xFFEF4444), Offset(sx - w * 0.18f, sy - h * 0.26f), Size(w * 0.36f, h * 0.12f))
          // Flashing roof beacon
          val ambBeacon = if ((animTick % 6) < 3) Color(0xFF38BDF8) else Color(0xFFF8FAFC)
          drawRect(ambBeacon, Offset(sx + dir * w * 0.15f, sy - h * 0.62f), Size(w * 0.16f, h * 0.14f))
        }
      }

      if (nightAlpha > 0.25f) {
        drawCircle(Color(0xAAFEF08A), radius = w * 0.32f, center = Offset(sx + dir * w * 0.52f, sy))
        drawRect(Color(0xFFEF4444), Offset(sx - dir * w * 0.48f, sy - h * 0.08f), Size(w * 0.08f, h * 0.14f))
      }
    }
  }
}

internal fun DrawScope.drawMilitaryUnits(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  nationColors: Map<Int, Color>,
  animTick: Int,
) {
  val lowDetail = tilePx < 4.5f
  for (u in frame.militaryUnits) {
    val sx = offsetX + u.x * tilePx
    val sy = offsetY + u.y * tilePx
    if (sx < -28f || sy < -28f || sx > size.width + 28f || sy > size.height + 28f) continue

    val nCol = nationColors[u.nationId] ?: Color(0xFF94A3B8)
    val s = (tilePx * 0.6f).coerceAtLeast(4.5f)
    val dir = if (u.targetX >= u.x) 1f else -1f

    if (lowDetail) {
      if (u.isOnWater) {
        val bw = s * 1.45f
        val bh = s * 0.85f
        drawRect(Color(0xFF475569), Offset(sx - bw * 0.5f, sy - bh * 0.5f), Size(bw, bh))
        drawRect(nCol, Offset(sx - bw * 0.25f, sy - bh * 0.35f), Size(bw * 0.5f, bh * 0.7f))
      } else {
        drawRect(nCol, Offset(sx - s * 0.45f, sy - s * 0.45f), Size(s * 0.9f, s * 0.9f))
      }
      continue
    }

    if (u.isOnWater) {
      // Detailed Naval Landing Gunboat with bow wake
      val boatW = s * 1.9f
      val boatH = s * 1.0f
      val wavePhase = ((animTick + u.id) % 8) / 8f
      drawRect(
        Color(0x99BAE6FD),
        Offset(sx - dir * boatW * (0.58f + wavePhase * 0.16f) - boatW * 0.15f, sy - boatH * 0.2f),
        Size(boatW * 0.34f, boatH * 0.4f),
      )
      val hullPath =
        Path().apply {
          moveTo(sx + dir * boatW * 0.58f, sy)
          lineTo(sx + dir * boatW * 0.22f, sy - boatH * 0.46f)
          lineTo(sx - dir * boatW * 0.48f, sy - boatH * 0.46f)
          lineTo(sx - dir * boatW * 0.48f, sy + boatH * 0.46f)
          lineTo(sx + dir * boatW * 0.22f, sy + boatH * 0.46f)
          close()
        }
      drawPath(hullPath, color = Color(0xFF334155))
      drawRect(Color(0xFF64748B), Offset(sx - boatW * 0.34f, sy - boatH * 0.3f), Size(boatW * 0.62f, boatH * 0.6f))
      // Bridge & deck turret
      drawRect(Color(0xFF1E293B), Offset(sx - dir * boatW * 0.22f - boatW * 0.14f, sy - boatH * 0.45f), Size(boatW * 0.28f, boatH * 0.48f))
      drawRect(nCol, Offset(sx - boatW * 0.32f, sy - boatH * 0.85f), Size(s * 0.48f, s * 0.3f))
    }

    when (u.type) {
      UnitType.INFANTRY -> {
        val bob = if (u.isOnWater) 0f else sin(u.animPhase) * (s * 0.16f)
        val stride = if (u.isOnWater) 0f else sin(u.animPhase) * (s * 0.18f)
        if (!u.isOnWater) {
          drawRect(Color(0x66000000), Offset(sx - s * 0.45f, sy + s * 0.28f), Size(s * 0.9f, s * 0.24f))
          // Boots
          drawRect(Color(0xFF1E293B), Offset(sx - s * 0.28f, sy + s * 0.12f + stride * 0.35f), Size(s * 0.22f, s * 0.24f))
          drawRect(Color(0xFF1E293B), Offset(sx + s * 0.08f, sy + s * 0.12f - stride * 0.35f), Size(s * 0.22f, s * 0.24f))
        }
        // Tactical backpack + body vest
        drawRect(Color(0xFF334155), Offset(sx - dir * s * 0.38f - s * 0.12f, sy - s * 0.3f + bob), Size(s * 0.24f, s * 0.42f))
        drawRect(nCol, Offset(sx - s * 0.34f, sy - s * 0.36f + bob), Size(s * 0.68f, s * 0.56f))
        // Helmet & visor
        drawRect(Color(0xFF334155), Offset(sx - s * 0.3f, sy - s * 0.72f + bob), Size(s * 0.6f, s * 0.36f))
        drawRect(Color(0xFF38BDF8), Offset(sx + dir * s * 0.12f - s * 0.12f, sy - s * 0.58f + bob), Size(s * 0.26f, s * 0.12f))
        // Assault rifle
        val gunStart = sx + dir * s * 0.15f
        drawRect(Color(0xFF0F172A), Offset(minOf(gunStart, gunStart + dir * s * 0.55f), sy - s * 0.22f + bob), Size(s * 0.55f, s * 0.14f))
        drawRect(Color(0xFFCBD5E1), Offset(sx + dir * s * 0.64f - s * 0.06f, sy - s * 0.22f + bob), Size(s * 0.12f, s * 0.1f))
      }

      UnitType.ARMOR -> {
        val w = s * 1.62f
        val h = s * 1.02f
        if (!u.isOnWater) {
          drawRect(Color(0x66000000), Offset(sx - w * 0.54f, sy + h * 0.15f), Size(w * 1.08f, h * 0.42f))
        }
        // Tracks & road wheels
        drawRect(Color(0xFF0F172A), Offset(sx - w * 0.54f, sy - h * 0.5f), Size(w * 1.08f, h))
        for (wi in -1..1) {
          val wx = sx + wi * w * 0.3f - w * 0.08f
          drawRect(Color(0xFF475569), Offset(wx, sy + h * 0.26f), Size(w * 0.16f, h * 0.2f))
          drawRect(Color(0xFF475569), Offset(wx, sy - h * 0.46f), Size(w * 0.16f, h * 0.2f))
        }
        // Sloped hull + reactive side skirts
        drawRect(nCol, Offset(sx - w * 0.44f, sy - h * 0.34f), Size(w * 0.88f, h * 0.68f))
        drawRect(Color(0xFF334155), Offset(sx - w * 0.4f, sy - h * 0.38f), Size(w * 0.8f, h * 0.1f))
        // Armored turret + commander cupola
        drawRect(Color(0xFF1E293B), Offset(sx - w * 0.24f, sy - h * 0.24f), Size(w * 0.48f, h * 0.48f))
        drawRect(Color(0xFFCBD5E1), Offset(sx - w * 0.18f, sy - h * 0.18f), Size(w * 0.36f, h * 0.36f))
        drawRect(nCol, Offset(sx - w * 0.12f, sy - h * 0.12f), Size(w * 0.14f, h * 0.14f))
        // Main cannon barrel + muzzle brake
        val barrelX = if (dir > 0) sx + w * 0.12f else sx - w * 0.72f
        drawRect(Color(0xFF64748B), Offset(barrelX, sy - h * 0.08f), Size(w * 0.6f, h * 0.16f))
        val muzzleX = if (dir > 0) sx + w * 0.66f else sx - w * 0.78f
        drawRect(Color(0xFFE2E8F0), Offset(muzzleX, sy - h * 0.12f), Size(w * 0.12f, h * 0.24f))
      }

      UnitType.MILITARY_TRUCK -> {
        val w = s * 1.48f
        val h = s * 0.92f
        drawRect(Color(0x55000000), Offset(sx - w * 0.5f, sy + h * 0.2f), Size(w, h * 0.35f))
        // 3 heavy tires
        drawRect(Color(0xFF0F172A), Offset(sx - w * 0.42f, sy + h * 0.22f), Size(w * 0.2f, h * 0.24f))
        drawRect(Color(0xFF0F172A), Offset(sx - w * 0.1f, sy + h * 0.22f), Size(w * 0.2f, h * 0.24f))
        drawRect(Color(0xFF0F172A), Offset(sx + w * 0.24f, sy + h * 0.22f), Size(w * 0.2f, h * 0.24f))
        // Bed + armored cab
        val bedX = if (dir > 0) sx - w * 0.5f else sx - w * 0.2f
        val cabX = if (dir > 0) sx + w * 0.2f else sx - w * 0.5f
        drawRect(nCol, Offset(bedX, sy - h * 0.44f), Size(w * 0.7f, h * 0.74f))
        drawRect(Color(0xFF334155), Offset(bedX + w * 0.14f, sy - h * 0.44f), Size(w * 0.08f, h * 0.74f))
        drawRect(Color(0xFF334155), Offset(bedX + w * 0.42f, sy - h * 0.44f), Size(w * 0.08f, h * 0.74f))
        drawRect(Color(0xFF475569), Offset(cabX, sy - h * 0.34f), Size(w * 0.3f, h * 0.64f))
        drawRect(Color(0xFF93C5FD), Offset(cabX + (if (dir > 0) w * 0.12f else w * 0.04f), sy - h * 0.24f), Size(w * 0.14f, h * 0.24f))
      }

      UnitType.AIR_DEFENSE_UNIT -> {
        val w = s * 1.52f
        val h = s * 1.0f
        // Tracked carrier
        drawRect(Color(0xFF0F172A), Offset(sx - w * 0.52f, sy - h * 0.46f), Size(w * 1.04f, h * 0.92f))
        drawRect(nCol, Offset(sx - w * 0.42f, sy - h * 0.34f), Size(w * 0.84f, h * 0.68f))
        // 4 SAM launch tubes
        for (ti in -1..2) {
          val tx = sx + ti * w * 0.13f - w * 0.05f
          drawRect(Color(0xFFE2E8F0), Offset(tx, sy - h * 0.64f), Size(w * 0.09f, h * 0.56f))
          drawRect(Color(0xFFEF4444), Offset(tx, sy - h * 0.68f), Size(w * 0.09f, h * 0.12f))
        }
        // Rotating target radar bar
        val ang = animTick * 0.32f + u.id
        drawLine(
          color = Color(0xFF38BDF8),
          start = Offset(sx - cos(ang) * w * 0.34f, sy - sin(ang) * w * 0.34f),
          end = Offset(sx + cos(ang) * w * 0.34f, sy + sin(ang) * w * 0.34f),
          strokeWidth = 2.4f,
        )
        drawCircle(Color(0xFF7DD3FC), radius = (s * 0.14f).coerceAtLeast(1.8f), center = Offset(sx, sy))
      }
    }

    // Mini HP bar when damaged
    if (u.hp < u.maxHp) {
      val ratio = (u.hp.toFloat() / u.maxHp).coerceIn(0f, 1f)
      val barW = s * 1.25f
      drawRect(Color(0xAA000000), Offset(sx - barW / 2f, sy - s * 1.05f), Size(barW, 3.5f))
      drawRect(Color(0xFF22C55E), Offset(sx - barW / 2f, sy - s * 1.05f), Size(barW * ratio, 3.5f))
    }

    // Blinking EMP sparks over disabled armor/units
    if (u.empDisabledTimer > 0f && (animTick % 6) < 4) {
      drawCircle(
        color = Color(0xFF22D3EE),
        radius = (s * 0.25f).coerceAtLeast(2f),
        center = Offset(sx - s * 0.28f, sy - s * 1.18f),
      )
      drawLine(
        color = Color(0xFFA855F7),
        start = Offset(sx - s * 0.36f, sy - s * 1.18f),
        end = Offset(sx + s * 0.36f, sy - s * 0.85f),
        strokeWidth = 2.2f,
      )
    }
  }
}

internal fun DrawScope.drawAirObjects(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  animTick: Int,
) {
  for (air in frame.airObjects) {
    val ax = offsetX + air.x * tilePx
    val ay = offsetY + air.y * tilePx
    if (ax < -60f || ay < -60f || ax > size.width + 60f || ay > size.height + 60f) continue
    val s = (tilePx * 1.05f).coerceAtLeast(9f)

    // Ground shadow
    drawRect(Color(0x55000000), Offset(ax - s * 0.36f, ay + s * 1.2f), Size(s * 0.72f, s * 0.28f))

    // PVO Lock-on reticle if detected
    if (air.detectedByPvo && (animTick % 10) < 6) {
      drawCircle(Color(0xFF38BDF8), radius = s * 1.05f, center = Offset(ax, ay), style = Stroke(width = 1.8f))
    }

    val dx = air.targetX - air.startX
    val dy = air.targetY - air.startY
    val angleDeg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()

    withTransform({ rotate(degrees = angleDeg, pivot = Offset(ax, ay)) }) {
      when (air.type) {
        AirObjectType.STRIKE_DRONE -> {
          // Shahed-style delta wing with spine & rear prop blur
          val path =
            Path().apply {
              moveTo(ax + s * 0.72f, ay)
              lineTo(ax - s * 0.5f, ay - s * 0.56f)
              lineTo(ax - s * 0.32f, ay)
              lineTo(ax - s * 0.5f, ay + s * 0.56f)
              close()
            }
          drawPath(path, color = Color(0xFF1E293B))
          drawPath(path, color = Color(0xFFF59E0B), style = Stroke(width = 1.6f))
          drawRect(Color(0xFF64748B), Offset(ax - s * 0.32f, ay - s * 0.1f), Size(s * 0.8f, s * 0.2f))
          // Rear prop & strobe
          if ((animTick % 4) < 2) {
            drawLine(Color(0xFFCBD5E1), Offset(ax - s * 0.44f, ay - s * 0.25f), Offset(ax - s * 0.44f, ay + s * 0.25f), strokeWidth = 2f)
          }
          if ((animTick % 6) < 3) {
            drawCircle(Color(0xFFEF4444), radius = s * 0.15f, center = Offset(ax - s * 0.3f, ay))
          }
        }

        AirObjectType.PVO_INTERCEPTOR -> {
          // Sleek supersonic interceptor missile with contrail & rocket flame
          drawCircle(Color(0xFFFB923C), radius = s * 0.28f, center = Offset(ax - s * 0.62f, ay))
          drawCircle(Color(0xFFFEF08A), radius = s * 0.16f, center = Offset(ax - s * 0.54f, ay))
          drawRect(Color(0xFFF8FAFC), Offset(ax - s * 0.5f, ay - s * 0.11f), Size(s * 1.0f, s * 0.22f))
          drawRect(Color(0xFF38BDF8), Offset(ax + s * 0.3f, ay - s * 0.1f), Size(s * 0.24f, s * 0.2f))
          drawRect(Color(0xFF38BDF8), Offset(ax - s * 0.3f, ay - s * 0.42f), Size(s * 0.26f, s * 0.84f))
        }

        AirObjectType.HEAVY_BOMBER -> {
          // Strategic quad-engine swept-wing bomber
          val wingPath =
            Path().apply {
              moveTo(ax + s * 0.25f, ay)
              lineTo(ax - s * 0.45f, ay - s * 1.05f)
              lineTo(ax - s * 0.65f, ay - s * 0.95f)
              lineTo(ax - s * 0.15f, ay)
              lineTo(ax - s * 0.65f, ay + s * 0.95f)
              lineTo(ax - s * 0.45f, ay + s * 1.05f)
              close()
            }
          drawPath(wingPath, color = Color(0xFF475569))
          drawRect(Color(0xFF64748B), Offset(ax - s * 0.8f, ay - s * 0.18f), Size(s * 1.6f, s * 0.36f))
          // Cockpit glass
          drawRect(Color(0xFF7DD3FC), Offset(ax + s * 0.44f, ay - s * 0.1f), Size(s * 0.22f, s * 0.2f))
          // Engine nacelles
          drawRect(Color(0xFFCBD5E1), Offset(ax - s * 0.25f, ay - s * 0.62f), Size(s * 0.28f, s * 0.16f))
          drawRect(Color(0xFFCBD5E1), Offset(ax - s * 0.25f, ay + s * 0.46f), Size(s * 0.28f, s * 0.16f))
          // Wingtip nav lights
          drawCircle(Color(0xFFEF4444), radius = s * 0.1f, center = Offset(ax - s * 0.55f, ay - s * 0.98f))
          drawCircle(Color(0xFF22C55E), radius = s * 0.1f, center = Offset(ax - s * 0.55f, ay + s * 0.98f))
        }

        AirObjectType.MILITARY_RECON_UAV -> {
          // Reaper-style high-aspect UAV with sensor pod & scan cone
          drawCircle(Color(0x3338BDF8), radius = s * 0.9f, center = Offset(ax + s * 0.35f, ay))
          drawRect(Color(0xFFCBD5E1), Offset(ax - s * 0.18f, ay - s * 0.78f), Size(s * 0.24f, s * 1.56f))
          drawRect(Color(0xFF94A3B8), Offset(ax - s * 0.52f, ay - s * 0.12f), Size(s * 1.08f, s * 0.24f))
          drawRect(Color(0xFF475569), Offset(ax + s * 0.28f, ay - s * 0.14f), Size(s * 0.3f, s * 0.28f))
          drawCircle(Color(0xFF38BDF8), radius = s * 0.14f, center = Offset(ax + s * 0.18f, ay))
        }

        AirObjectType.JET_PATROL -> {
          // Supersonic delta fighter with afterburner
          drawCircle(Color(0xAA38BDF8), radius = s * 0.22f, center = Offset(ax - s * 0.58f, ay))
          val jetPath =
            Path().apply {
              moveTo(ax + s * 0.68f, ay)
              lineTo(ax - s * 0.42f, ay - s * 0.62f)
              lineTo(ax - s * 0.48f, ay)
              lineTo(ax - s * 0.42f, ay + s * 0.62f)
              close()
            }
          drawPath(jetPath, color = Color(0xFF38BDF8))
          drawRect(Color(0xFFF8FAFC), Offset(ax - s * 0.4f, ay - s * 0.12f), Size(s * 0.95f, s * 0.24f))
          drawRect(Color(0xFF0F172A), Offset(ax + s * 0.12f, ay - s * 0.08f), Size(s * 0.26f, s * 0.16f))
        }

        AirObjectType.CARGO_PLANE -> {
          // Humanitarian / Trade heavy transport aircraft
          drawRect(Color(0xFFE2E8F0), Offset(ax - s * 0.16f, ay - s * 0.72f), Size(s * 0.34f, s * 1.44f))
          drawRect(Color(0xFFF8FAFC), Offset(ax - s * 0.62f, ay - s * 0.18f), Size(s * 1.24f, s * 0.36f))
          drawRect(Color(0xFF38BDF8), Offset(ax - s * 0.48f, ay - s * 0.06f), Size(s * 0.85f, s * 0.12f))
          drawRect(Color(0xFF0F172A), Offset(ax + s * 0.4f, ay - s * 0.11f), Size(s * 0.16f, s * 0.22f))
          drawRect(Color(0xFF64748B), Offset(ax - s * 0.64f, ay - s * 0.34f), Size(s * 0.16f, s * 0.68f))
        }

        AirObjectType.RECON_DRONE -> {
          // Quadcopter with 4 spinning rotors
          drawLine(Color(0xFF475569), Offset(ax - s * 0.4f, ay - s * 0.4f), Offset(ax + s * 0.4f, ay + s * 0.4f), strokeWidth = 2.2f)
          drawLine(Color(0xFF475569), Offset(ax - s * 0.4f, ay + s * 0.4f), Offset(ax + s * 0.4f, ay - s * 0.4f), strokeWidth = 2.2f)
          val rRad = s * 0.2f
          val rCol = if ((animTick % 2) == 0) Color(0xFF86EFAC) else Color(0xFF22C55E)
          drawCircle(rCol, radius = rRad, center = Offset(ax - s * 0.4f, ay - s * 0.4f), style = Stroke(width = 1.6f))
          drawCircle(rCol, radius = rRad, center = Offset(ax + s * 0.4f, ay - s * 0.4f), style = Stroke(width = 1.6f))
          drawCircle(rCol, radius = rRad, center = Offset(ax - s * 0.4f, ay + s * 0.4f), style = Stroke(width = 1.6f))
          drawCircle(rCol, radius = rRad, center = Offset(ax + s * 0.4f, ay + s * 0.4f), style = Stroke(width = 1.6f))
          drawCircle(Color(0xFF1E293B), radius = s * 0.22f, center = Offset(ax, ay))
          drawCircle(Color(0xFF22C55E), radius = s * 0.1f, center = Offset(ax, ay))
        }

        AirObjectType.ROCKET_STREAK -> {
          // Ballistic/cruise missile with glowing exhaust plume
          drawCircle(Color(0xFFEF4444), radius = s * 0.38f, center = Offset(ax - s * 0.65f, ay))
          drawCircle(Color(0xFFFEF08A), radius = s * 0.22f, center = Offset(ax - s * 0.5f, ay))
          drawRect(Color(0xFFE2E8F0), Offset(ax - s * 0.45f, ay - s * 0.14f), Size(s * 0.9f, s * 0.28f))
          drawRect(Color(0xFFEF4444), Offset(ax + s * 0.3f, ay - s * 0.12f), Size(s * 0.26f, s * 0.24f))
          drawRect(Color(0xFF94A3B8), Offset(ax - s * 0.45f, ay - s * 0.32f), Size(s * 0.22f, s * 0.64f))
        }

        AirObjectType.METEOR -> {
          drawCircle(Color(0x66EF4444), radius = s * 1.05f, center = Offset(ax - s * 0.3f, ay))
          drawCircle(Color(0xFFEA580C), radius = s * 0.8f, center = Offset(ax, ay))
          drawCircle(Color(0xFFFDE047), radius = s * 0.5f, center = Offset(ax + s * 0.12f, ay))
          drawCircle(Color(0xFF57534E), radius = s * 0.34f, center = Offset(ax + s * 0.2f, ay))
        }
      }
    }
  }
}

internal fun DrawScope.drawBuildings(
  frame: RenderFrame,
  offsetX: Float,
  offsetY: Float,
  tilePx: Float,
  nationColors: Map<Int, Color>,
  nightAlpha: Float,
  animTick: Int,
) {
  val lowDetail = tilePx < 4.5f
  val blackoutCityIds = frame.cities.filter { it.blackoutTimer > 0f }.map { it.id }.toSet()
  for (b in frame.buildings) {
    val bx = offsetX + b.x * tilePx
    val by = offsetY + b.y * tilePx
    if (bx < -tilePx * 2.5f || by < -tilePx * 2.5f || bx > size.width + tilePx * 2.5f || by > size.height + tilePx * 2.5f) continue

    val pad = tilePx * 0.08f
    val bw = tilePx - pad * 2f
    val nCol = nationColors[b.nationId] ?: Color(0xFF94A3B8)
    val inBlackout = b.cityId in blackoutCityIds

    if (lowDetail) {
      val lodColor =
        when (b.type) {
          BuildingType.CAPITOL -> Color(0xFFF8FAFC)
          BuildingType.RADAR -> Color(0xFF38BDF8)
          BuildingType.EW_STATION -> Color(0xFFA855F7)
          BuildingType.FARM -> Color(0xFF65A30D)
          BuildingType.RUINED_BUILDING -> Color(0xFF57534E)
          else -> nCol
        }
      drawRect(lodColor, Offset(bx + pad, by + pad), Size(bw, bw))
      continue
    }

    // Subtle ground drop shadow
    if (b.type != BuildingType.FARM && b.type != BuildingType.RUINED_BUILDING) {
      drawRect(Color(0x4D000000), Offset(bx + pad + tilePx * 0.06f, by + tilePx * 0.16f), Size(bw, bw * 0.92f))
    }

    when (b.type) {
      BuildingType.CAPITOL -> {
        // Grand stone plinth, colonnade facade, marble dome & waving nation flag
        drawRect(Color(0xFF334155), Offset(bx + pad * 0.6f, by + tilePx * 0.78f), Size(tilePx - pad * 1.2f, tilePx * 0.18f))
        drawRect(Color(0xFFCBD5E1), Offset(bx + pad, by + tilePx * 0.25f), Size(bw, tilePx * 0.56f))
        drawRect(Color(0xFFF8FAFC), Offset(bx + tilePx * 0.16f, by + tilePx * 0.28f), Size(tilePx * 0.68f, tilePx * 0.48f))
        // Pillars
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.2f, by + tilePx * 0.32f), Size(tilePx * 0.09f, tilePx * 0.44f))
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.71f, by + tilePx * 0.32f), Size(tilePx * 0.09f, tilePx * 0.44f))
        // Dome + spire
        val domeColor = if (b.level >= 2) Color(0xFFFACC15) else nCol
        drawCircle(domeColor, radius = tilePx * 0.24f, center = Offset(bx + tilePx * 0.5f, by + tilePx * 0.2f))
        drawRect(Color(0xFFE2E8F0), Offset(bx + tilePx * 0.47f, by - tilePx * 0.22f), Size(tilePx * 0.06f, tilePx * 0.26f))
        val wave = if ((animTick % 10) < 5) 0f else tilePx * 0.05f
        drawRect(nCol, Offset(bx + tilePx * 0.52f, by - tilePx * 0.22f + wave), Size(tilePx * 0.28f, tilePx * 0.16f))
        // Arched gate + lit windows
        drawRect(Color(0xFF1E293B), Offset(bx + tilePx * 0.42f, by + tilePx * 0.55f), Size(tilePx * 0.16f, tilePx * 0.25f))
        if (nightAlpha > 0.2f && !inBlackout) {
          drawRect(Color(0xFFFDE047), Offset(bx + tilePx * 0.24f, by + tilePx * 0.38f), Size(tilePx * 0.14f, tilePx * 0.16f))
          drawRect(Color(0xFFFDE047), Offset(bx + tilePx * 0.62f, by + tilePx * 0.38f), Size(tilePx * 0.14f, tilePx * 0.16f))
        }
      }

      BuildingType.HOUSE -> {
        val storiesExtra = if (b.level >= 2) tilePx * 0.14f else 0f
        val topY = by + tilePx * 0.18f - storiesExtra
        // Walls
        drawRect(Color(0xFFE2E8F0), Offset(bx + pad, topY + tilePx * 0.22f), Size(bw, tilePx * 0.7f + storiesExtra))
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.66f, topY + tilePx * 0.22f), Size(tilePx * 0.24f, tilePx * 0.7f + storiesExtra))
        // Pitched tiled roof with chimney
        drawRect(Color(0xFF475569), Offset(bx + tilePx * 0.64f, topY - tilePx * 0.06f), Size(tilePx * 0.14f, tilePx * 0.24f))
        val roofPath =
          Path().apply {
            moveTo(bx + tilePx * 0.5f, topY - tilePx * 0.04f)
            lineTo(bx + tilePx * 0.04f, topY + tilePx * 0.28f)
            lineTo(bx + tilePx * 0.96f, topY + tilePx * 0.28f)
            close()
          }
        drawPath(roofPath, color = nCol.copy(alpha = 0.92f))
        // Door & windows
        drawRect(Color(0xFF78350F), Offset(bx + tilePx * 0.42f, by + tilePx * 0.64f), Size(tilePx * 0.16f, tilePx * 0.26f))
        val winColor = if (nightAlpha > 0.22f && !inBlackout) Color(0xFFFACC15) else Color(0xFF334155)
        drawRect(winColor, Offset(bx + tilePx * 0.22f, topY + tilePx * 0.42f), Size(tilePx * 0.16f, tilePx * 0.16f))
        drawRect(winColor, Offset(bx + tilePx * 0.56f, topY + tilePx * 0.42f), Size(tilePx * 0.16f, tilePx * 0.16f))
        if (b.level >= 2) {
          drawRect(winColor, Offset(bx + tilePx * 0.22f, topY + tilePx * 0.26f), Size(tilePx * 0.16f, tilePx * 0.12f))
          drawRect(winColor, Offset(bx + tilePx * 0.56f, topY + tilePx * 0.26f), Size(tilePx * 0.16f, tilePx * 0.12f))
        }
      }

      BuildingType.FACTORY -> {
        // Industrial hall with sawtooth roof, side silo & twin smokestacks
        drawRect(Color(0xFF334155), Offset(bx + pad, by + tilePx * 0.34f), Size(bw * 0.8f, tilePx * 0.56f))
        // Sawtooth roof
        val sawPath =
          Path().apply {
            moveTo(bx + pad, by + tilePx * 0.34f)
            lineTo(bx + tilePx * 0.28f, by + tilePx * 0.16f)
            lineTo(bx + tilePx * 0.42f, by + tilePx * 0.34f)
            lineTo(bx + tilePx * 0.58f, by + tilePx * 0.16f)
            lineTo(bx + tilePx * 0.72f, by + tilePx * 0.34f)
            close()
          }
        drawPath(sawPath, color = Color(0xFF475569))
        // Side fuel silo
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.72f, by + tilePx * 0.42f), Size(tilePx * 0.2f, tilePx * 0.48f))
        // Twin smokestacks
        drawRect(Color(0xFF64748B), Offset(bx + tilePx * 0.58f, by - tilePx * 0.04f), Size(tilePx * 0.12f, tilePx * 0.36f))
        drawRect(Color(0xFF64748B), Offset(bx + tilePx * 0.76f, by + tilePx * 0.02f), Size(tilePx * 0.12f, tilePx * 0.32f))
        // Glowing furnace bay
        val bayColor = if (!inBlackout) Color(0xFFF97316) else Color(0xFF1E293B)
        drawRect(bayColor, Offset(bx + tilePx * 0.2f, by + tilePx * 0.62f), Size(tilePx * 0.24f, tilePx * 0.26f))
        if (!inBlackout && (animTick + b.id) % 10 < 5) {
          drawCircle(Color(0x99CBD5E1), radius = (tilePx * 0.18f).coerceAtLeast(2f), center = Offset(bx + tilePx * 0.64f, by - tilePx * 0.18f))
        }
      }

      BuildingType.FARM -> {
        // Furrowed wheat field + red barn & grain silo
        drawRect(Color(0xFF4D7C0F), Offset(bx + pad, by + pad), Size(bw, bw))
        drawRect(Color(0xFFFACC15), Offset(bx + tilePx * 0.45f, by + tilePx * 0.2f), Size(tilePx * 0.44f, tilePx * 0.16f))
        drawRect(Color(0xFFEAB308), Offset(bx + tilePx * 0.45f, by + tilePx * 0.46f), Size(tilePx * 0.44f, tilePx * 0.16f))
        drawRect(Color(0xFFFACC15), Offset(bx + tilePx * 0.45f, by + tilePx * 0.72f), Size(tilePx * 0.44f, tilePx * 0.16f))
        // Red barn
        drawRect(Color(0xFFB91C1C), Offset(bx + tilePx * 0.1f, by + tilePx * 0.32f), Size(tilePx * 0.34f, tilePx * 0.52f))
        drawRect(Color(0xFF78350F), Offset(bx + tilePx * 0.18f, by + tilePx * 0.54f), Size(tilePx * 0.18f, tilePx * 0.3f))
        // Silo
        drawRect(Color(0xFFCBD5E1), Offset(bx + tilePx * 0.16f, by + tilePx * 0.1f), Size(tilePx * 0.22f, tilePx * 0.22f))
      }

      BuildingType.RADAR -> {
        // Bunker base + rotating radar sweep cone + parabolic dish
        drawRect(Color(0xFF0F172A), Offset(bx + pad, by + tilePx * 0.3f), Size(bw, bw * 0.7f))
        drawRect(Color(0xFF334155), Offset(bx + tilePx * 0.22f, by + tilePx * 0.44f), Size(tilePx * 0.56f, tilePx * 0.42f))
        val cx = bx + tilePx * 0.5f
        val cy = by + tilePx * 0.46f
        val angleDeg = ((animTick * 12 + b.id * 37) % 360).toFloat()
        if (!inBlackout) {
          drawArc(
            color = Color(0x4438BDF8),
            startAngle = angleDeg - 28f,
            sweepAngle = 56f,
            useCenter = true,
            topLeft = Offset(cx - tilePx * 1.4f, cy - tilePx * 1.4f),
            size = Size(tilePx * 2.8f, tilePx * 2.8f),
          )
        }
        drawCircle(Color(0x4438BDF8), radius = tilePx * 1.4f, center = Offset(cx, cy), style = Stroke(width = 1.2f))
        val radAng = angleDeg * (PI.toFloat() / 180f)
        drawLine(
          color = Color(0xFF38BDF8),
          start = Offset(cx, cy),
          end = Offset(cx + cos(radAng) * tilePx * 0.52f, cy + sin(radAng) * tilePx * 0.52f),
          strokeWidth = 2.4f,
        )
        drawCircle(Color(0xFFE0F2FE), radius = (tilePx * 0.13f).coerceAtLeast(1.8f), center = Offset(cx, cy))
      }

      BuildingType.EW_STATION -> {
        // Armored electronic warfare bunker + phased-array mast + dual pulse rings
        drawRect(Color(0xFF1E1B4B), Offset(bx + pad, by + tilePx * 0.32f), Size(bw, bw * 0.68f))
        drawRect(Color(0xFF4C1D95), Offset(bx + tilePx * 0.22f, by + tilePx * 0.42f), Size(tilePx * 0.56f, tilePx * 0.44f))
        drawRect(Color(0xFFA855F7), Offset(bx + tilePx * 0.42f, by - tilePx * 0.02f), Size(tilePx * 0.16f, tilePx * 0.5f))
        drawRect(Color(0xFF22D3EE), Offset(bx + tilePx * 0.26f, by + tilePx * 0.08f), Size(tilePx * 0.48f, tilePx * 0.14f))
        val cx = bx + tilePx * 0.5f
        val cy = by + tilePx * 0.3f
        if (!inBlackout) {
          val pulse = ((animTick + b.id) % 16) / 16f
          drawCircle(
            color = Color(0xFF22D3EE).copy(alpha = (1f - pulse) * 0.6f),
            radius = tilePx * (0.55f + pulse * 1.25f),
            center = Offset(cx, cy),
            style = Stroke(width = 1.8f),
          )
          drawCircle(
            color = Color(0xFFA855F7).copy(alpha = (1f - pulse) * 0.45f),
            radius = tilePx * (0.35f + pulse * 0.75f),
            center = Offset(cx, cy),
            style = Stroke(width = 1.4f),
          )
        }
      }

      BuildingType.SHELTER -> {
        // Heavy concrete civil defense bunker with blast door & amber beacon
        drawRect(Color(0xFF334155), Offset(bx + pad, by + tilePx * 0.22f), Size(bw, tilePx * 0.7f))
        drawRect(Color(0xFF475569), Offset(bx + tilePx * 0.16f, by + tilePx * 0.14f), Size(tilePx * 0.68f, tilePx * 0.24f))
        drawRect(Color(0xFF1E293B), Offset(bx + tilePx * 0.28f, by + tilePx * 0.46f), Size(tilePx * 0.44f, tilePx * 0.42f))
        drawRect(Color(0xFFF59E0B), Offset(bx + tilePx * 0.28f, by + tilePx * 0.46f), Size(tilePx * 0.44f, tilePx * 0.12f))
        val beacon = if ((animTick % 14) < 7) Color(0xFFFACC15) else Color(0xFFB45309)
        drawCircle(beacon, radius = (tilePx * 0.11f).coerceAtLeast(1.8f), center = Offset(bx + tilePx * 0.5f, by + tilePx * 0.22f))
      }

      BuildingType.POWER_PLANT -> {
        // Turbine hall + twin cooling towers + glowing cyan reactor core
        drawRect(Color(0xFF334155), Offset(bx + pad, by + tilePx * 0.44f), Size(bw, tilePx * 0.46f))
        // Twin cooling towers
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.16f, by + tilePx * 0.12f), Size(tilePx * 0.24f, tilePx * 0.52f))
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.56f, by + tilePx * 0.12f), Size(tilePx * 0.24f, tilePx * 0.52f))
        val coreColor = if (!inBlackout) Color(0xFF22D3EE) else Color(0xFF475569)
        drawCircle(coreColor, radius = (tilePx * 0.16f).coerceAtLeast(2f), center = Offset(bx + tilePx * 0.48f, by + tilePx * 0.68f))
        if (!inBlackout && (animTick + b.id) % 12 < 6) {
          drawCircle(Color(0x88E2E8F0), radius = (tilePx * 0.14f).coerceAtLeast(1.8f), center = Offset(bx + tilePx * 0.28f, by - tilePx * 0.04f))
          drawCircle(Color(0x88E2E8F0), radius = (tilePx * 0.14f).coerceAtLeast(1.8f), center = Offset(bx + tilePx * 0.68f, by - tilePx * 0.04f))
        }
      }

      BuildingType.MILITARY_BASE -> {
        // Fortified walls, armored command roof, helipad cross & watchtower
        drawRect(Color(0xFF1E293B), Offset(bx + pad, by + pad), Size(bw, bw))
        drawRect(nCol.copy(alpha = 0.78f), Offset(bx + pad, by + pad), Size(bw, bw * 0.22f))
        drawRect(Color(0xFF475569), Offset(bx + tilePx * 0.18f, by + tilePx * 0.34f), Size(tilePx * 0.64f, tilePx * 0.52f))
        // Tactical H-pad / star emblem
        drawRect(Color(0xFFFACC15), Offset(bx + tilePx * 0.36f, by + tilePx * 0.48f), Size(tilePx * 0.08f, tilePx * 0.26f))
        drawRect(Color(0xFFFACC15), Offset(bx + tilePx * 0.56f, by + tilePx * 0.48f), Size(tilePx * 0.08f, tilePx * 0.26f))
        drawRect(Color(0xFFFACC15), Offset(bx + tilePx * 0.36f, by + tilePx * 0.58f), Size(tilePx * 0.28f, tilePx * 0.07f))
        // Corner watchtower
        drawRect(Color(0xFF94A3B8), Offset(bx + tilePx * 0.74f, by + tilePx * 0.18f), Size(tilePx * 0.18f, tilePx * 0.24f))
      }

      BuildingType.RUINED_BUILDING -> {
        drawRect(Color(0xFF292524), Offset(bx + pad, by + tilePx * 0.48f), Size(bw * 0.9f, bw * 0.52f))
        drawRect(Color(0xFF57534E), Offset(bx + tilePx * 0.16f, by + tilePx * 0.26f), Size(tilePx * 0.24f, tilePx * 0.42f))
        drawRect(Color(0xFF44403C), Offset(bx + tilePx * 0.56f, by + tilePx * 0.36f), Size(tilePx * 0.28f, tilePx * 0.34f))
        // Exposed rebar spikes
        drawRect(Color(0xFF78716C), Offset(bx + tilePx * 0.22f, by + tilePx * 0.16f), Size(tilePx * 0.06f, tilePx * 0.16f))
        drawRect(Color(0xFF78716C), Offset(bx + tilePx * 0.68f, by + tilePx * 0.24f), Size(tilePx * 0.06f, tilePx * 0.16f))
      }
    }

    if (b.level >= 2 && b.type != BuildingType.RUINED_BUILDING) {
      val pipSize = (tilePx * 0.14f).coerceAtLeast(2f)
      drawRect(Color(0xFFFACC15), Offset(bx + pad, by - pipSize * 0.6f), Size(pipSize, pipSize))
      if (b.level >= 3) {
        drawRect(Color(0xFF38BDF8), Offset(bx + pad + pipSize * 1.4f, by - pipSize * 0.6f), Size(pipSize, pipSize))
      }
    }
  }
}
