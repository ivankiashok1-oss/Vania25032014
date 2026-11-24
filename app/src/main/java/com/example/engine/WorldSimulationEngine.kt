package com.example.engine

import com.example.audio.SirenSoundEngine
import com.example.model.AirObject
import com.example.model.AirObjectType
import com.example.model.Animal
import com.example.model.AnimalType
import com.example.model.Building
import com.example.model.BuildingType
import com.example.model.Citizen
import com.example.model.CitizenState
import com.example.model.City
import com.example.model.GodTool
import com.example.model.MilitaryUnit
import com.example.model.Nation
import com.example.model.ParticleType
import com.example.model.Profession
import com.example.model.RelationType
import com.example.model.TerrainType
import com.example.model.UnitOrder
import com.example.model.UnitType
import com.example.model.Vehicle
import com.example.model.VehicleType
import com.example.model.WarConflict
import com.example.model.WeatherType
import com.example.model.WorldEventCategory
import com.example.model.WorldNewsItem
import com.example.model.WorldParticle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class WorldSimulationEngine {
  private val rng = Random(System.currentTimeMillis())

  fun step(
    state: WorldState,
    dt: Float,
    speedMultiplier: Int,
    soundEngine: SirenSoundEngine,
    maxParticles: Int = 180,
  ) {
    if (speedMultiplier <= 0) return

    val simDt = (dt * speedMultiplier).coerceAtMost(0.25f)
    state.tickCount += speedMultiplier

    // 1. Time of Day & Calendar
    state.timeOfDay = (state.timeOfDay + simDt * 0.018f) % 1f
    if (state.tickCount % 45L < speedMultiplier) {
      state.month++
      if (state.month > 12) {
        state.month = 1
        state.year++
        advanceYear(state)
      }
    }

    // 2. Weather & Lightning
    updateWeather(state, simDt, soundEngine, maxParticles)

    // 3. Fire spread, smoke & radiation decay/damage
    if (state.tickCount % 3L < speedMultiplier) {
      updateFires(state, maxParticles)
    }
    if (state.tickCount % 4L < speedMultiplier) {
      updateRadiation(state, maxParticles)
    }

    // 4. PVO Radar & EW Detection/Interception + Air Objects
    updatePvoDefense(state, simDt, soundEngine, maxParticles)
    updateAirAlertsAndObjects(state, simDt, soundEngine, maxParticles)

    // 5. Particles
    updateParticles(state, simDt)

    // 6. Citizens, Military Units, Vehicles & Wildlife (staggered throttling on giant maps)
    updateCitizens(state, simDt)
    updateMilitaryUnits(state, simDt, maxParticles)
    updateVehicles(state, simDt)
    if (state.width < 192 || state.tickCount % 2L == 0L) {
      updateAnimals(state, simDt)
    }

    // 7. Nation AI (Economy, Tech, Upgrades, Stability, Recruitment, Diplomacy, War, Collapse)
    if (state.tickCount % 18L < speedMultiplier) {
      updateNationsAndCities(state, soundEngine)
    }

    state.worldVersion++
  }

  private fun advanceYear(state: WorldState) {
    val it = state.citizens.iterator()
    while (it.hasNext()) {
      val c = it.next()
      c.age++
      if (c.age > 78 && rng.nextFloat() < 0.28f) {
        it.remove()
      }
    }

    val popCap = if (state.width >= 192) 550 else 420
    if (state.citizens.size < popCap) {
      for (city in state.cities) {
        if (city.underAirAlert) continue
        val cityPop = state.citizens.count { it.cityId == city.id }
        val houseCapacity =
          state.buildings
            .filter { it.cityId == city.id && it.type == BuildingType.HOUSE }
            .sumOf { it.level * 5 }
        val nation = state.nations.find { it.id == city.nationId }
        val maxCap = (houseCapacity + 8).coerceAtMost(45)
        if (cityPop < maxCap && (nation == null || nation.food > 30) && rng.nextFloat() < 0.55f) {
          val prof = Profession.entries[rng.nextInt(Profession.entries.size)]
          state.citizens.add(
            Citizen(
              id = state.allocId(),
              name = NameGenerator.randomCitizenName(rng),
              x = city.centerX + 0.5f,
              y = city.centerY + 0.5f,
              targetX = city.centerX + 0.5f,
              targetY = city.centerY + 0.5f,
              age = 18,
              hp = 100,
              nationId = city.nationId,
              cityId = city.id,
              familyId = city.id * 100 + rng.nextInt(1, 50),
              profession = prof,
              homeX = city.centerX,
              homeY = city.centerY,
              workX = city.centerX,
              workY = city.centerY,
            )
          )
        }
      }
    }

    if (state.year % 2 == 0) {
      state.recordHistorySnapshot()
    }
  }

  private fun updateWeather(
    state: WorldState,
    simDt: Float,
    soundEngine: SirenSoundEngine,
    maxParticles: Int,
  ) {
    state.weatherTimer -= simDt
    if (state.weatherTimer <= 0f) {
      state.weatherTimer = rng.nextFloat() * 35f + 25f
      val nextWeather =
        when (rng.nextInt(10)) {
          0, 1, 2, 3, 4 -> WeatherType.CLEAR
          5, 6, 7 -> WeatherType.RAIN
          8 -> WeatherType.FOG
          else -> WeatherType.STORM
        }
      if (nextWeather != state.weather) {
        state.weather = nextWeather
        addNews(
          state,
          WorldEventCategory.WEATHER,
          "Погода: ${nextWeather.titleRu}",
          "Над миром установилась погода: ${nextWeather.titleRu.lowercase()}.",
          state.width / 2,
          state.height / 2,
          0xFF38BDF8.toInt(),
        )
      }
    }

    if (state.weather == WeatherType.RAIN || state.weather == WeatherType.STORM) {
      for (i in 0 until state.size) {
        if (state.fireGrid[i] > 0) {
          state.fireGrid[i] = (state.fireGrid[i] - 8).coerceAtLeast(0)
        }
      }
      if (state.weather == WeatherType.STORM && rng.nextFloat() < simDt * 0.35f) {
        val lx = rng.nextInt(2, state.width - 2)
        val ly = rng.nextInt(2, state.height - 2)
        spawnExplosion(state, lx, ly, radius = 1, ignite = true, soundEngine = soundEngine, playSound = false, maxParticles = maxParticles)
        if (state.particles.size < maxParticles) {
          state.particles.add(
            WorldParticle(
              id = state.allocId(),
              x = lx + 0.5f,
              y = ly + 0.5f,
              vx = 0f,
              vy = 0f,
              life = 0.28f,
              maxLife = 0.28f,
              size = 4f,
              colorArgb = 0xFFE0F2FE.toInt(),
              type = ParticleType.LIGHTNING,
            )
          )
        }
      }
    }
  }

  private fun updateFires(state: WorldState, maxParticles: Int) {
    val w = state.width
    val h = state.height
    val buildingByTile = HashMap<Int, Int>(state.buildings.size * 2 + 1)
    for (idx in state.buildings.indices) {
      val b = state.buildings[idx]
      buildingByTile[state.idx(b.x, b.y)] = idx
    }
    for (y in 1 until h - 1) {
      for (x in 1 until w - 1) {
        val i = state.idx(x, y)
        val fire = state.fireGrid[i]
        if (fire <= 0) continue

        if (state.particles.size < maxParticles && rng.nextFloat() < 0.22f) {
          state.particles.add(
            WorldParticle(
              id = state.allocId(),
              x = x + rng.nextFloat(),
              y = y + rng.nextFloat(),
              vx = (rng.nextFloat() - 0.5f) * 0.4f,
              vy = -0.7f - rng.nextFloat() * 0.6f,
              life = 1.1f,
              maxLife = 1.1f,
              size = 1.2f,
              colorArgb = 0xFF475569.toInt(),
              type = ParticleType.SMOKE,
            )
          )
        }

        val bIdx = buildingByTile[i] ?: -1
        if (bIdx >= 0) {
          val b = state.buildings[bIdx]
          val newHp = b.hp - 4
          if (newHp <= 0 && b.type != BuildingType.RUINED_BUILDING) {
            state.buildings[bIdx] = b.copy(type = BuildingType.RUINED_BUILDING, hp = 30, isBurning = false)
            state.setTerrain(x, y, TerrainType.RUINS)
            state.totalDestroyedBuildings++
          } else if (newHp > 0) {
            state.buildings[bIdx] = b.copy(hp = newHp, isBurning = true)
          }
        }

        if (fire > 25 && rng.nextFloat() < 0.14f) {
          val nx = x + rng.nextInt(-1, 2)
          val ny = y + rng.nextInt(-1, 2)
          val nt = state.getTerrain(nx, ny)
          if (nt.isFlammable && state.fireGrid[state.idx(nx, ny)] == 0) {
            state.fireGrid[state.idx(nx, ny)] = 55
          }
        }

        val decayed = fire - 3
        if (decayed <= 0) {
          state.fireGrid[i] = 0
          val curT = state.getTerrain(x, y)
          if (curT == TerrainType.FOREST || curT == TerrainType.CITY_ZONE) {
            state.setTerrain(x, y, TerrainType.RUINS)
          }
        } else {
          state.fireGrid[i] = decayed
        }
      }
    }
  }

  private fun updateRadiation(state: WorldState, maxParticles: Int) {
    var anyRad = false
    for (i in 0 until state.size) {
      val rad = state.radiationGrid[i]
      if (rad > 0) {
        anyRad = true
        val next = (rad - 1).coerceAtLeast(0)
        state.radiationGrid[i] = next
        if (next == 0) {
          state.terrainDirty = true
        }
      }
    }
    if (!anyRad) return
    if (state.tickCount % 16L < 4L) {
      state.terrainDirty = true
    }

    for (c in state.citizens) {
      val tx = c.x.toInt()
      val ty = c.y.toInt()
      if (state.inBounds(tx, ty) && state.radiationGrid[state.idx(tx, ty)] > 10) {
        c.hp -= 4
        if (c.hp <= 0) {
          state.nations.find { it.id == c.nationId }?.let { it.totalCasualties++ }
        }
      }
    }

    val unitIt = state.militaryUnits.iterator()
    while (unitIt.hasNext()) {
      val u = unitIt.next()
      val tx = u.x.toInt()
      val ty = u.y.toInt()
      if (state.inBounds(tx, ty) && state.radiationGrid[state.idx(tx, ty)] > 10) {
        u.hp -= if (u.type.isArmor) 3 else 6
        if (u.hp <= 0) {
          state.nations.find { it.id == u.nationId }?.let {
            it.totalCasualties++
            it.losses++
          }
          unitIt.remove()
        }
      }
    }

    if (state.particles.size < maxParticles && rng.nextFloat() < 0.35f) {
      val rx = rng.nextInt(1, state.width - 1)
      val ry = rng.nextInt(1, state.height - 1)
      if (state.radiationGrid[state.idx(rx, ry)] > 25) {
        state.particles.add(
          WorldParticle(
            id = state.allocId(),
            x = rx + rng.nextFloat(),
            y = ry + rng.nextFloat(),
            vx = 0f,
            vy = -0.35f,
            life = 0.9f,
            maxLife = 0.9f,
            size = 1.4f,
            colorArgb = 0xFF84CC16.toInt(),
            type = ParticleType.RADIATION_GLOW,
          )
        )
      }
    }
  }

  private fun updatePvoDefense(
    state: WorldState,
    simDt: Float,
    soundEngine: SirenSoundEngine,
    maxParticles: Int,
  ) {
    val blackoutCityIds = state.cities.filter { it.blackoutTimer > 0f }.map { it.id }.toSet()
    val techByNation = state.nations.associate { it.id to it.techLevel }

    // Decrement radar and EW station cooldowns (2x slower in blackout)
    for (idx in state.buildings.indices) {
      val b = state.buildings[idx]
      if ((b.type == BuildingType.RADAR || b.type == BuildingType.EW_STATION) && b.pvoCooldown > 0f) {
        val rate = if (b.cityId in blackoutCityIds) 0.5f else 1f
        state.buildings[idx] = b.copy(pvoCooldown = (b.pvoCooldown - simDt * rate).coerceAtLeast(0f))
      }
    }

    // Check EW_STATION electronic jamming against hostile and recon drones
    val suppressedIds = HashSet<Int>()
    for (idx in state.buildings.indices) {
      val ew = state.buildings[idx]
      if (ew.type != BuildingType.EW_STATION || ew.hp <= 0 || ew.pvoCooldown > 0f) continue
      if (ew.cityId in blackoutCityIds) continue

      val ewRange = 15f + (ew.level - 1) * 2f
      val targetDrone =
        state.airObjects.firstOrNull { air ->
          air.id !in suppressedIds &&
            air.nationId != ew.nationId &&
            (air.type == AirObjectType.STRIKE_DRONE ||
              air.type == AirObjectType.RECON_DRONE ||
              air.type == AirObjectType.MILITARY_RECON_UAV) &&
            hypot(air.x - ew.x, air.y - ew.y) <= ewRange
        } ?: continue

      suppressedIds.add(targetDrone.id)
      state.buildings[idx] = ew.copy(pvoCooldown = 2.2f)
      state.totalPvoIntercepts++
      state.nations.find { it.id == ew.nationId }?.let { it.airDefenseIntercepts++ }
      state.warConflicts.filter { it.isActive && (it.nationAId == ew.nationId || it.nationBId == ew.nationId) }
        .forEach { it.pvoInterceptsCount++ }

      soundEngine.playIntercept()
      if (state.particles.size < maxParticles) {
        state.particles.add(
          WorldParticle(
            id = state.allocId(),
            x = targetDrone.x,
            y = targetDrone.y,
            vx = 0f,
            vy = 0f,
            life = 0.5f,
            maxLife = 0.5f,
            size = 2.4f,
            colorArgb = 0xFFA855F7.toInt(),
            type = ParticleType.EMP_WAVE,
          )
        )
      }
      addNews(
        state,
        WorldEventCategory.PVO,
        "РЭБ: подавлен дрон",
        "Станция РЭБ подавила навигацию цели «${targetDrone.type.titleRu}».",
        targetDrone.x.toInt(),
        targetDrone.y.toInt(),
        0xFFA855F7.toInt(),
      )
    }
    if (suppressedIds.isNotEmpty()) {
      state.airObjects.removeAll { it.id in suppressedIds }
    }

    val threats = state.airObjects.filter { it.type.isHostileThreat && it.type != AirObjectType.PVO_INTERCEPTOR }
    if (threats.isEmpty()) return

    val activeInterceptorsTargets =
      state.airObjects.filter { it.type == AirObjectType.PVO_INTERCEPTOR }.map { it.targetAirObjectId }.toSet()

    // Check fixed RADAR buildings
    for (idx in state.buildings.indices) {
      val radar = state.buildings[idx]
      if (radar.type != BuildingType.RADAR || radar.hp <= 0 || radar.pvoCooldown > 0f) continue

      val nationTech = techByNation[radar.nationId] ?: 1
      val radarRange = 22f + (nationTech - 1) * 2f + (radar.level - 1) * 3f
      val threat =
        threats.firstOrNull { air ->
          air.nationId != radar.nationId &&
            air.id !in activeInterceptorsTargets &&
            hypot(air.x - radar.x, air.y - radar.y) <= radarRange
        } ?: continue

      // Detection!
      if (!threat.detectedByPvo) {
        threat.detectedByPvo = true
        val city = state.cities.find { it.id == radar.cityId }
        if (city != null && !city.underAirAlert) {
          city.underAirAlert = true
          city.airAlertTimer = 12f
          soundEngine.playAirRaidSiren()
          addNews(
            state,
            WorldEventCategory.PVO,
            "ПВО: обнаружена цель",
            "Радар города ${city.name} обнаружил ${threat.type.titleRu.lowercase()} и поднял тревогу.",
            city.centerX,
            city.centerY,
            0xFF38BDF8.toInt(),
          )
        }
      }

      // Launch PVO Interceptor!
      val baseCooldown = 3.2f / (1f + (radar.level - 1) * 0.2f)
      val cooldown = if (radar.cityId in blackoutCityIds) baseCooldown * 2f else baseCooldown
      state.buildings[idx] = radar.copy(pvoCooldown = cooldown)
      state.airObjects.add(
        AirObject(
          id = state.allocId(),
          x = radar.x + 0.5f,
          y = radar.y + 0.5f,
          startX = radar.x + 0.5f,
          startY = radar.y + 0.5f,
          targetX = threat.x,
          targetY = threat.y,
          speed = 0.85f,
          type = AirObjectType.PVO_INTERCEPTOR,
          nationId = radar.nationId,
          targetAirObjectId = threat.id,
        )
      )
      if (state.particles.size < maxParticles) {
        state.particles.add(
          WorldParticle(
            id = state.allocId(),
            x = radar.x + 0.5f,
            y = radar.y + 0.5f,
            vx = 0f,
            vy = 0f,
            life = 0.45f,
            maxLife = 0.45f,
            size = 2.2f,
            colorArgb = 0xFF38BDF8.toInt(),
            type = ParticleType.INTERCEPT_FLASH,
          )
        )
        if (state.particles.size < maxParticles) {
          val tdx = threat.x - (radar.x + 0.5f)
          val tdy = threat.y - (radar.y + 0.5f)
          val tdist = hypot(tdx, tdy).coerceAtLeast(0.1f)
          state.particles.add(
            WorldParticle(
              id = state.allocId(),
              x = radar.x + 0.5f,
              y = radar.y + 0.5f,
              vx = (tdx / tdist) * 14f,
              vy = (tdy / tdist) * 14f,
              life = 0.28f,
              maxLife = 0.28f,
              size = 1.3f,
              colorArgb = 0xFF7DD3FC.toInt(),
              type = ParticleType.SPARK,
            )
          )
        }
      }
      break
    }

    // Mobile Air Defense Units also fire interceptors (when not EMP-disabled)
    for (unit in state.militaryUnits) {
      if (unit.type != UnitType.AIR_DEFENSE_UNIT || unit.empDisabledTimer > 0f) continue
      val unitTech = techByNation[unit.nationId] ?: 1
      val mobileRange = 18f + (unitTech - 1) * 2f
      val threat =
        threats.firstOrNull { air ->
          air.nationId != unit.nationId &&
            air.id !in activeInterceptorsTargets &&
            hypot(air.x - unit.x, air.y - unit.y) <= mobileRange
        } ?: continue

      if (rng.nextFloat() < simDt * 0.65f) {
        state.airObjects.add(
          AirObject(
            id = state.allocId(),
            x = unit.x,
            y = unit.y,
            startX = unit.x,
            startY = unit.y,
            targetX = threat.x,
            targetY = threat.y,
            speed = 0.85f,
            type = AirObjectType.PVO_INTERCEPTOR,
            nationId = unit.nationId,
            targetAirObjectId = threat.id,
          )
        )
        if (state.particles.size < maxParticles) {
          val tdx = threat.x - unit.x
          val tdy = threat.y - unit.y
          val tdist = hypot(tdx, tdy).coerceAtLeast(0.1f)
          state.particles.add(
            WorldParticle(
              id = state.allocId(),
              x = unit.x,
              y = unit.y,
              vx = (tdx / tdist) * 13f,
              vy = (tdy / tdist) * 13f,
              life = 0.26f,
              maxLife = 0.26f,
              size = 1.2f,
              colorArgb = 0xFF38BDF8.toInt(),
              type = ParticleType.SPARK,
            )
          )
        }
        break
      }
    }
  }

  private fun updateAirAlertsAndObjects(
    state: WorldState,
    simDt: Float,
    soundEngine: SirenSoundEngine,
    maxParticles: Int,
  ) {
    for (city in state.cities) {
      if (city.underAirAlert) {
        city.airAlertTimer -= simDt
        if (city.airAlertTimer <= 0f) {
          city.underAirAlert = false
          city.airAlertTimer = 0f
        }
      }
      if (city.blackoutTimer > 0f) {
        city.blackoutTimer = (city.blackoutTimer - simDt).coerceAtLeast(0f)
      }
      if (city.smokeLevel > 0f) {
        city.smokeLevel = (city.smokeLevel - simDt * 0.08f).coerceAtLeast(0f)
      }
    }

    val removedIds = HashSet<Int>()
    val byId = state.airObjects.associateBy { it.id }

    for (obj in state.airObjects) {
      if (obj.id in removedIds) continue

      if (obj.type == AirObjectType.PVO_INTERCEPTOR && obj.targetAirObjectId > 0) {
        val target = byId[obj.targetAirObjectId]
        if (target == null || target.id in removedIds) {
          removedIds.add(obj.id)
          continue
        }
        val dx = target.x - obj.x
        val dy = target.y - obj.y
        val dist = hypot(dx, dy)
        val step = 18f * simDt
        if (dist <= step + 1.2f) {
          // Intercepted!
          removedIds.add(obj.id)
          removedIds.add(target.id)
          state.totalPvoIntercepts++
          state.nations.find { it.id == obj.nationId }?.let { it.airDefenseIntercepts++ }
          state.warConflicts.filter { it.isActive && (it.nationAId == obj.nationId || it.nationBId == obj.nationId) }
            .forEach { it.pvoInterceptsCount++ }

          soundEngine.playIntercept()
          spawnInterceptBurst(state, target.x, target.y, maxParticles)
          addNews(
            state,
            WorldEventCategory.PVO,
            "ПВО: цель перехвачена",
            "Расчёт ПВО перехватил объект «${target.type.titleRu}» в воздухе.",
            target.x.toInt(),
            target.y.toInt(),
            0xFF22C55E.toInt(),
          )
        } else {
          obj.x += (dx / dist) * step
          obj.y += (dy / dist) * step
        }
        continue
      }

      obj.progress += obj.speed * simDt
      val t = obj.progress.coerceIn(0f, 1f)
      obj.x = obj.startX + (obj.targetX - obj.startX) * t
      obj.y = obj.startY + (obj.targetY - obj.startY) * t

      // Visual trails
      val hasTrail =
        obj.type == AirObjectType.ROCKET_STREAK ||
          obj.type == AirObjectType.METEOR ||
          obj.type == AirObjectType.STRIKE_DRONE ||
          obj.type == AirObjectType.HEAVY_BOMBER
      if (hasTrail && state.particles.size < maxParticles && rng.nextFloat() < 0.55f) {
        state.particles.add(
          WorldParticle(
            id = state.allocId(),
            x = obj.x,
            y = obj.y,
            vx = (rng.nextFloat() - 0.5f) * 0.3f,
            vy = (rng.nextFloat() - 0.5f) * 0.3f,
            life = 0.7f,
            maxLife = 0.7f,
            size = if (obj.type == AirObjectType.METEOR) 1.6f else 1.0f,
            colorArgb =
              when (obj.type) {
                AirObjectType.METEOR -> 0xFFF97316.toInt()
                AirObjectType.STRIKE_DRONE -> 0xFFEF4444.toInt()
                else -> 0xFFCBD5E1.toInt()
              },
            type = ParticleType.SMOKE,
          )
        )
      }

      // Heavy bomber drops periodic arcade impact bursts mid-flight
      if (obj.type == AirObjectType.HEAVY_BOMBER && !obj.payloadTriggered && obj.progress >= 0.5f) {
        obj.payloadTriggered = true
        spawnExplosion(state, obj.x.toInt(), obj.y.toInt(), 2, ignite = true, soundEngine = soundEngine, maxParticles = maxParticles)
      }

      if (obj.progress >= 1f) {
        if (!obj.payloadTriggered && obj.type == AirObjectType.CARGO_PLANE) {
          obj.payloadTriggered = true
          val destCity = findNearestCity(state, obj.targetX.toInt(), obj.targetY.toInt())
          val destNation = state.nations.find { it.id == (destCity?.nationId ?: obj.nationId) }
          if (destNation != null) {
            destNation.food = (destNation.food + 18).coerceAtMost(9999)
            destNation.materials = (destNation.materials + 15).coerceAtMost(9999)
          }
        } else if (!obj.payloadTriggered &&
          (obj.type == AirObjectType.ROCKET_STREAK ||
            obj.type == AirObjectType.METEOR ||
            obj.type == AirObjectType.STRIKE_DRONE)
        ) {
          obj.payloadTriggered = true
          val rad =
            when (obj.type) {
              AirObjectType.METEOR -> 4
              AirObjectType.STRIKE_DRONE -> 2
              else -> 2
            }
          spawnExplosion(state, obj.targetX.toInt(), obj.targetY.toInt(), rad, ignite = true, soundEngine = soundEngine, maxParticles = maxParticles)
        }
        removedIds.add(obj.id)
      }
    }

    if (removedIds.isNotEmpty()) {
      state.airObjects.removeAll { it.id in removedIds }
    }
  }

  private fun spawnInterceptBurst(state: WorldState, x: Float, y: Float, maxParticles: Int) {
    if (state.particles.size >= maxParticles) return
    state.particles.add(
      WorldParticle(
        id = state.allocId(),
        x = x,
        y = y,
        vx = 0f,
        vy = 0f,
        life = 0.5f,
        maxLife = 0.5f,
        size = 2.6f,
        colorArgb = 0xFF38BDF8.toInt(),
        type = ParticleType.INTERCEPT_FLASH,
      )
    )
    repeat(8) {
      if (state.particles.size >= maxParticles) return@repeat
      val ang = rng.nextFloat() * 6.283f
      val spd = rng.nextFloat() * 4f + 2f
      state.particles.add(
        WorldParticle(
          id = state.allocId(),
          x = x,
          y = y,
          vx = cos(ang) * spd,
          vy = sin(ang) * spd,
          life = 0.45f,
          maxLife = 0.45f,
          size = 1.2f,
          colorArgb = if (it % 2 == 0) 0xFFE0F2FE.toInt() else 0xFF38BDF8.toInt(),
          type = ParticleType.SPARK,
        )
      )
    }
  }

  private fun updateParticles(state: WorldState, simDt: Float) {
    val it = state.particles.iterator()
    while (it.hasNext()) {
      val p = it.next()
      p.life -= simDt
      if (p.life <= 0f) {
        it.remove()
        continue
      }
      p.x += p.vx * simDt
      p.y += p.vy * simDt
    }
  }

  private fun updateCitizens(state: WorldState, simDt: Float) {
    val isNight = state.isNight()
    val cityById = state.cities.associateBy { it.id }
    val nationById = state.nations.associateBy { it.id }
    val sheltersByCity = HashMap<Int, Building>()
    val ruinedByCity = HashMap<Int, MutableList<Int>>()
    for (idx in state.buildings.indices) {
      val b = state.buildings[idx]
      when (b.type) {
        BuildingType.SHELTER -> sheltersByCity[b.cityId] = b
        BuildingType.CAPITOL -> sheltersByCity.putIfAbsent(b.cityId, b)
        BuildingType.RUINED_BUILDING -> ruinedByCity.getOrPut(b.cityId) { mutableListOf() }.add(idx)
        else -> Unit
      }
    }
    val it = state.citizens.iterator()

    while (it.hasNext()) {
      val c = it.next()
      if (c.hp <= 0) {
        it.remove()
        continue
      }
      c.animPhase += simDt * 7f
      c.hunger = (c.hunger + simDt * 0.008f).coerceAtMost(1f)
      if (c.hunger > 0.85f) {
        val nation = nationById[c.nationId]
        if (nation != null && nation.food > 0) {
          nation.food--
          c.hunger = 0.1f
        }
      }

      val homeCity = cityById[c.cityId]
      val nearbyFire = isFireNear(state, c.x.toInt(), c.y.toInt(), 4)
      val cityAlert = homeCity?.underAirAlert == true

      if (cityAlert || nearbyFire) {
        if (c.profession == Profession.SOLDIER && !nearbyFire) {
          c.state = CitizenState.PATROL
        } else {
          val shelter = sheltersByCity[c.cityId]
          if (shelter != null) {
            c.targetX = shelter.x + 0.5f
            c.targetY = shelter.y + 0.5f
            val dist = hypot(c.targetX - c.x, c.targetY - c.y)
            c.state = if (dist < 1.2f) CitizenState.IN_SHELTER else CitizenState.EVACUATING
          } else {
            c.state = CitizenState.PANIC
            if (rng.nextFloat() < simDt * 1.5f) {
              c.targetX = (c.x + rng.nextInt(-5, 6)).coerceIn(1f, state.width - 2f)
              c.targetY = (c.y + rng.nextInt(-5, 6)).coerceIn(1f, state.height - 2f)
            }
          }
        }
      } else if (c.profession == Profession.BUILDER) {
        val ruinedIndices = ruinedByCity[c.cityId]
        val ruinedIdx = ruinedIndices?.firstOrNull { state.buildings[it].type == BuildingType.RUINED_BUILDING }
        val ruined = if (ruinedIdx != null) state.buildings[ruinedIdx] else null
        if (ruined != null && ruinedIdx != null) {
          c.state = CitizenState.REBUILDING
          c.targetX = ruined.x + 0.5f
          c.targetY = ruined.y + 0.5f
          if (hypot(c.targetX - c.x, c.targetY - c.y) < 1.1f && rng.nextFloat() < simDt * 0.65f) {
            state.buildings[ruinedIdx] =
              ruined.copy(type = BuildingType.HOUSE, hp = BuildingType.HOUSE.maxHp, maxHp = BuildingType.HOUSE.maxHp)
            state.setTerrain(ruined.x, ruined.y, TerrainType.CITY_ZONE)
            state.totalRebuiltBuildings++
            ruinedIndices.remove(ruinedIdx)
          }
        } else {
          normalSchedule(state, c, isNight)
        }
      } else {
        normalSchedule(state, c, isNight)
      }

      val dx = c.targetX - c.x
      val dy = c.targetY - c.y
      val dist = hypot(dx, dy)
      if (dist > 0.15f) {
        val baseSpeed =
          when (c.state) {
            CitizenState.EVACUATING, CitizenState.PANIC -> 3.6f
            CitizenState.TRAVELING -> 2.6f
            else -> 1.8f
          }
        val onRoad = state.getTerrain(c.x.toInt(), c.y.toInt()) == TerrainType.ROAD
        val stepDist = baseSpeed * (if (onRoad) 1.35f else 1f) * simDt
        val curOnWater = state.getTerrain(c.x.toInt(), c.y.toInt()).isWater
        val nextPos =
          stepAroundObstacle(
            state = state,
            x = c.x,
            y = c.y,
            dx = dx,
            dy = dy,
            dist = dist,
            stepDist = stepDist,
            allowWater = curOnWater,
            minBound = 0.5f,
            maxOffset = 1.5f,
          )
        if (nextPos != null) {
          c.x = nextPos.first
          c.y = nextPos.second
        } else if (c.state != CitizenState.EVACUATING) {
          c.targetX = (c.x + rng.nextInt(-3, 4)).coerceIn(1f, state.width - 2f)
          c.targetY = (c.y + rng.nextInt(-3, 4)).coerceIn(1f, state.height - 2f)
        }
      } else if (rng.nextFloat() < simDt * 0.35f) {
        if (c.profession == Profession.TRADER && state.cities.size > 1 && rng.nextFloat() < 0.25f) {
          val destCity = state.cities[rng.nextInt(state.cities.size)]
          c.state = CitizenState.TRAVELING
          c.targetX = destCity.centerX + 0.5f
          c.targetY = destCity.centerY + 0.5f
          nationById[c.nationId]?.let { it.treasury += 4 }
        } else if (c.state == CitizenState.TRAVELING && homeCity != null) {
          c.state = CitizenState.GOING_HOME
          c.targetX = homeCity.centerX + 0.5f
          c.targetY = homeCity.centerY + 0.5f
        }
      }

      if (c.profession == Profession.MEDIC && rng.nextFloat() < simDt * 0.6f) {
        val patient = state.citizens.firstOrNull { other -> other.hp < other.maxHp && hypot(other.x - c.x, other.y - c.y) < 3.5f }
        if (patient != null) {
          patient.hp = (patient.hp + 15).coerceAtMost(patient.maxHp)
        }
      }
    }
  }

  private fun updateMilitaryUnits(state: WorldState, simDt: Float, maxParticles: Int) {
    val nationMap = state.nations.associateBy { it.id }
    val unitsToRemove = HashSet<Int>()

    for (u in state.militaryUnits) {
      if (u.hp <= 0 || u.id in unitsToRemove) {
        unitsToRemove.add(u.id)
        continue
      }
      if (u.empDisabledTimer > 0f) {
        u.empDisabledTimer = (u.empDisabledTimer - simDt).coerceAtLeast(0f)
        continue
      }
      u.animPhase += simDt * 6f

      val nation = nationMap[u.nationId]
      val isAtWar = nation?.isAtWar() == true && state.warsEnabled
      val homeCity =
        state.cities.find { it.id == u.cityId && it.nationId == u.nationId }
          ?: state.cities.firstOrNull { it.nationId == u.nationId }

      // Retreat when critically damaged
      if (u.hp < u.maxHp * 0.30f && homeCity != null) {
        u.order = UnitOrder.RETREAT
        u.targetX = homeCity.centerX + 0.5f
        u.targetY = homeCity.centerY + 0.5f
        if (hypot(u.targetX - u.x, u.targetY - u.y) < 2.2f) {
          u.hp = (u.hp + 12 * simDt).toInt().coerceAtMost(u.maxHp)
        }
      } else if (homeCity?.underAirAlert == true && u.type != UnitType.AIR_DEFENSE_UNIT) {
        u.order = UnitOrder.DEFEND_CITY
        u.targetX = homeCity.centerX + 0.5f
        u.targetY = homeCity.centerY + 0.5f
      } else if (isAtWar && nation != null) {
        val enemyNationIds = nation.relations.filterValues { it == RelationType.WAR }.keys
        // Look for nearby enemy unit first
        val nearEnemy =
          state.militaryUnits.firstOrNull { other ->
            other.id !in unitsToRemove &&
              other.nationId in enemyNationIds &&
              hypot(other.x - u.x, other.y - u.y) < 16f
          }
        if (nearEnemy != null) {
          u.order = UnitOrder.ADVANCE
          u.targetX = nearEnemy.x
          u.targetY = nearEnemy.y
        } else {
          // March toward nearest enemy city
          val enemyCity =
            state.cities.filter { it.nationId in enemyNationIds }
              .minByOrNull { hypot((it.centerX - u.x), (it.centerY - u.y)) }
          if (enemyCity != null) {
            u.order = UnitOrder.ADVANCE
            u.targetX = enemyCity.centerX + 0.5f
            u.targetY = enemyCity.centerY + 0.5f
          }
        }
      } else {
        u.order = UnitOrder.PATROL
        if (hypot(u.targetX - u.x, u.targetY - u.y) < 1.2f || rng.nextFloat() < simDt * 0.25f) {
          val cx = homeCity?.centerX ?: u.x.toInt()
          val cy = homeCity?.centerY ?: u.y.toInt()
          u.targetX = (cx + rng.nextInt(-6, 7)).coerceIn(1, state.width - 2) + 0.5f
          u.targetY = (cy + rng.nextInt(-6, 7)).coerceIn(1, state.height - 2) + 0.5f
        }
      }

      // Move unit (with naval landing craft state on water and obstacle avoidance)
      val curOnWater = state.getTerrain(u.x.toInt(), u.y.toInt()).isWater
      u.isOnWater = curOnWater
      val dx = u.targetX - u.x
      val dy = u.targetY - u.y
      val dist = hypot(dx, dy)
      if (dist > 0.2f) {
        val step = u.type.speed * (if (curOnWater) 0.78f else 1f) * simDt
        val allowWater = u.order == UnitOrder.ADVANCE || curOnWater
        val nextPos =
          stepAroundObstacle(
            state = state,
            x = u.x,
            y = u.y,
            dx = dx,
            dy = dy,
            dist = dist,
            stepDist = step,
            allowWater = allowWater,
            minBound = 1f,
            maxOffset = 2f,
          )
        if (nextPos != null) {
          u.x = nextPos.first
          u.y = nextPos.second
          u.isOnWater = state.getTerrain(u.x.toInt(), u.y.toInt()).isWater
        }
      }

      // Territory capture while advancing in war
      if (isAtWar && nation != null && u.order == UnitOrder.ADVANCE) {
        val tx = u.x.toInt()
        val ty = u.y.toInt()
        val curOwner = state.getOwner(tx, ty)
        val enemyIds = nation.relations.filterValues { it == RelationType.WAR }.keys
        if ((curOwner in enemyIds || curOwner == 0) && !state.getTerrain(tx, ty).isWater) {
          state.setOwner(tx, ty, nation.id)
          nation.territoriesCaptured++
          if (curOwner in enemyIds) {
            recordWarTileCapture(state, nation.id, curOwner)
          }
        }
      }

      // Unit vs Unit Tactical Skirmish
      if (isAtWar && nation != null && rng.nextFloat() < simDt * 3.5f) {
        val enemyIds = nation.relations.filterValues { it == RelationType.WAR }.keys
        val foe =
          state.militaryUnits.firstOrNull { other ->
            other.id !in unitsToRemove &&
              other.nationId in enemyIds &&
              hypot(other.x - u.x, other.y - u.y) <= 2.3f
          }
        if (foe != null) {
          val dmgToFoe = (u.strength * (0.7f + rng.nextFloat() * 0.6f)).toInt()
          val dmgToSelf = (foe.strength * (0.5f + rng.nextFloat() * 0.5f)).toInt()
          foe.hp -= dmgToFoe
          u.hp -= dmgToSelf

          if (state.particles.size < maxParticles) {
            val sdx = foe.x - u.x
            val sdy = foe.y - u.y
            val sdist = hypot(sdx, sdy).coerceAtLeast(0.1f)
            val vx = (sdx / sdist) * 9f
            val vy = (sdy / sdist) * 9f
            state.particles.add(
              WorldParticle(
                id = state.allocId(),
                x = u.x,
                y = u.y,
                vx = vx,
                vy = vy,
                life = 0.22f,
                maxLife = 0.22f,
                size = 1.15f,
                colorArgb = 0xFFFACC15.toInt(),
                type = ParticleType.SPARK,
              )
            )
            if (state.particles.size < maxParticles) {
              state.particles.add(
                WorldParticle(
                  id = state.allocId(),
                  x = foe.x,
                  y = foe.y,
                  vx = -vx * 0.85f,
                  vy = -vy * 0.85f,
                  life = 0.20f,
                  maxLife = 0.20f,
                  size = 1.05f,
                  colorArgb = 0xFFFB923C.toInt(),
                  type = ParticleType.SPARK,
                )
              )
            }
          }

          if (foe.hp <= 0) {
            unitsToRemove.add(foe.id)
            nation.wins++
            nationMap[foe.nationId]?.let {
              it.losses++
              it.totalCasualties++
            }
            recordWarCasualty(state, nation.id, foe.nationId)
          }
          if (u.hp <= 0) {
            unitsToRemove.add(u.id)
            nation.losses++
            nation.totalCasualties++
            nationMap[foe.nationId]?.let { it.wins++ }
            recordWarCasualty(state, foe.nationId, u.nationId)
          }
        } else {
          // Capture undefended enemy city!
          val enemyCity =
            state.cities.firstOrNull { c ->
              c.nationId in enemyIds &&
                hypot(c.centerX - u.x, c.centerY - u.y) < 1.8f &&
                state.militaryUnits.none { def -> def.id !in unitsToRemove && def.nationId == c.nationId && hypot(def.x - c.centerX, def.y - c.centerY) < 6f }
            }
          if (enemyCity != null) {
            val oldNationId = enemyCity.nationId
            enemyCity.nationId = nation.id
            enemyCity.isCapital = false
            nation.wins++
            nation.territoriesCaptured += 12
            for (bIdx in state.buildings.indices) {
              val b = state.buildings[bIdx]
              if (b.cityId == enemyCity.id) {
                state.buildings[bIdx] = b.copy(nationId = nation.id)
              }
            }
            for (c in state.citizens) {
              if (c.cityId == enemyCity.id) {
                c.nationId = nation.id
              }
            }
            for (vIdx in state.vehicles.indices) {
              val v = state.vehicles[vIdx]
              if (v.cityId == enemyCity.id) {
                state.vehicles[vIdx] = v.copy(nationId = nation.id)
              }
            }
            val oldNation = nationMap[oldNationId]
            if (oldNation != null && oldNation.capitalCityId == enemyCity.id) {
              val nextCapital = state.cities.firstOrNull { it.nationId == oldNationId && it.id != enemyCity.id }
              if (nextCapital != null) {
                nextCapital.isCapital = true
                oldNation.capitalCityId = nextCapital.id
              } else {
                oldNation.capitalCityId = 0
              }
            }
            for (dy in -4..4) {
              for (dx in -4..4) {
                val tx = enemyCity.centerX + dx
                val ty = enemyCity.centerY + dy
                if (state.inBounds(tx, ty) && state.getOwner(tx, ty) == oldNationId) {
                  state.setOwner(tx, ty, nation.id)
                }
              }
            }
            addNews(
              state,
              WorldEventCategory.WAR,
              "Захват города ${enemyCity.name}",
              "Подразделения ${nation.name} заняли город ${enemyCity.name}.",
              enemyCity.centerX,
              enemyCity.centerY,
              nation.colorArgb,
            )
          }
        }
      }
    }

    if (unitsToRemove.isNotEmpty()) {
      state.militaryUnits.removeAll { it.id in unitsToRemove }
    }
  }

  private fun recordWarCasualty(state: WorldState, winnerId: Int, loserId: Int) {
    val conflict =
      state.warConflicts.firstOrNull {
        it.isActive && ((it.nationAId == winnerId && it.nationBId == loserId) || (it.nationAId == loserId && it.nationBId == winnerId))
      } ?: return
    if (conflict.nationAId == loserId) conflict.casualtiesA++ else conflict.casualtiesB++
  }

  private fun recordWarTileCapture(state: WorldState, capturerId: Int, loserId: Int) {
    val conflict =
      state.warConflicts.firstOrNull {
        it.isActive && ((it.nationAId == capturerId && it.nationBId == loserId) || (it.nationAId == loserId && it.nationBId == capturerId))
      } ?: return
    if (conflict.nationAId == capturerId) conflict.tilesCapturedA++ else conflict.tilesCapturedB++
  }

  private fun normalSchedule(state: WorldState, c: Citizen, isNight: Boolean) {
    if (isNight) {
      c.state = CitizenState.GOING_HOME
      c.targetX = c.homeX + 0.5f
      c.targetY = c.homeY + 0.5f
    } else if (c.state != CitizenState.TRAVELING) {
      c.state = if (c.profession == Profession.SOLDIER) CitizenState.PATROL else CitizenState.WORKING
      c.targetX = c.workX + 0.5f
      c.targetY = c.workY + 0.5f
    }
  }

  private fun updateVehicles(state: WorldState, simDt: Float) {
    if (simDt <= 0f) return
    for (v in state.vehicles) {
      val startTile = state.getTerrain(v.x.toInt(), v.y.toInt())
      val baseSpeed = if (v.type == VehicleType.BULLDOZER) 2.3f else 4.1f
      val roadMult =
        when (startTile) {
          TerrainType.ROAD -> 1.4f
          TerrainType.CITY_ZONE -> 1.15f
          else -> 1.0f
        }
      var remaining = baseSpeed * roadMult * simDt
      var steps = 0

      while (remaining > 0.001f && steps < 4) {
        steps++
        val curTx = v.x.toInt().coerceIn(1, state.width - 2)
        val curTy = v.y.toInt().coerceIn(1, state.height - 2)
        val curTile = state.getTerrain(curTx, curTy)
        val targetTx = v.targetX.toInt().coerceIn(0, state.width - 1)
        val targetTy = v.targetY.toInt().coerceIn(0, state.height - 1)
        val targetTile = state.getTerrain(targetTx, targetTy)
        val dx = v.targetX - v.x
        val dy = v.targetY - v.y
        val dist = hypot(dx, dy)

        val bulldozerRuins =
          if (v.type == VehicleType.BULLDOZER) {
            state.buildings.firstOrNull { it.cityId == v.cityId && it.type == BuildingType.RUINED_BUILDING }
          } else {
            null
          }
        val headingToAdjacentRuins =
          bulldozerRuins != null && targetTx == bulldozerRuins.x && targetTy == bulldozerRuins.y

        val needsWaypoint =
          dist <= 0.14f ||
            targetTile.isWater ||
            targetTile == TerrainType.MOUNTAIN ||
            (curTile == TerrainType.ROAD && targetTile != TerrainType.ROAD && !headingToAdjacentRuins) ||
            (curTile != TerrainType.ROAD && targetTile != TerrainType.ROAD && dist > 1.6f && !headingToAdjacentRuins)

        if (needsWaypoint) {
          assignNextVehicleWaypoint(state, v, curTx, curTy, curTile, bulldozerRuins)
        }

        val ndx = v.targetX - v.x
        val ndy = v.targetY - v.y
        val ndist = hypot(ndx, ndy)
        if (ndist <= 0.01f) {
          remaining = 0f
        } else if (ndist <= remaining) {
          // Snap cleanly to road tile center and continue on next road segment without corner-cutting
          v.x = v.targetX.coerceIn(1f, state.width - 2f)
          v.y = v.targetY.coerceIn(1f, state.height - 2f)
          remaining -= ndist
        } else {
          val nx = (v.x + (ndx / ndist) * remaining).coerceIn(1f, state.width - 2f)
          val ny = (v.y + (ndy / ndist) * remaining).coerceIn(1f, state.height - 2f)
          val nextTile = state.getTerrain(nx.toInt(), ny.toInt())
          if (!nextTile.isWater && nextTile != TerrainType.MOUNTAIN) {
            v.x = nx
            v.y = ny
          } else {
            v.targetX = curTx + 0.5f
            v.targetY = curTy + 0.5f
          }
          remaining = 0f
        }
      }
    }
  }

  private fun assignNextVehicleWaypoint(
    state: WorldState,
    v: Vehicle,
    curTx: Int,
    curTy: Int,
    curTile: TerrainType,
    bulldozerRuins: Building?,
  ) {
    if (bulldozerRuins != null && abs(bulldozerRuins.x - curTx) + abs(bulldozerRuins.y - curTy) <= 2) {
      v.prevTileX = curTx
      v.prevTileY = curTy
      v.targetX = bulldozerRuins.x + 0.5f
      v.targetY = bulldozerRuins.y + 0.5f
      return
    }

    val dirs = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)
    if (curTile == TerrainType.ROAD) {
      val roadNeighbors = ArrayList<Pair<Int, Int>>(4)
      for (d in 0 until 4) {
        val nx = curTx + dirs[d * 2]
        val ny = curTy + dirs[d * 2 + 1]
        if (nx in 1 until state.width - 1 && ny in 1 until state.height - 1) {
          if (state.getTerrain(nx, ny) == TerrainType.ROAD) {
            roadNeighbors.add(nx to ny)
          }
        }
      }
      if (roadNeighbors.isNotEmpty()) {
        val forward = roadNeighbors.filter { it.first != v.prevTileX || it.second != v.prevTileY }
        val pool = if (forward.isNotEmpty()) forward else roadNeighbors
        val chosen =
          if (bulldozerRuins != null && rng.nextFloat() < 0.72f) {
            pool.minByOrNull { abs(it.first - bulldozerRuins.x) + abs(it.second - bulldozerRuins.y) } ?: pool[0]
          } else {
            val prevDx = if (v.prevTileX >= 0) curTx - v.prevTileX else 0
            val prevDy = if (v.prevTileY >= 0) curTy - v.prevTileY else 0
            val straight =
              if (prevDx != 0 || prevDy != 0) {
                pool.firstOrNull { (it.first - curTx) == prevDx && (it.second - curTy) == prevDy }
              } else {
                null
              }
            if (straight != null && (pool.size == 1 || rng.nextFloat() < 0.62f)) {
              straight
            } else {
              pool[rng.nextInt(pool.size)]
            }
          }
        v.prevTileX = curTx
        v.prevTileY = curTy
        v.targetX = chosen.first + 0.5f
        v.targetY = chosen.second + 0.5f
        return
      }
    }

    // Vehicle is off-road (or on a single isolated road tile): steer onto the nearest ROAD tile
    val roadStep = findStepTowardNearestRoad(state, curTx, curTy)
    if (roadStep != null) {
      v.prevTileX = curTx
      v.prevTileY = curTy
      v.targetX = roadStep.first + 0.5f
      v.targetY = roadStep.second + 0.5f
      return
    }

    // Fallback when no road exists nearby: stay within walkable city zone / neighborhood
    val fallbackNeighbors = ArrayList<Pair<Int, Int>>(4)
    for (d in 0 until 4) {
      val nx = curTx + dirs[d * 2]
      val ny = curTy + dirs[d * 2 + 1]
      if (nx in 1 until state.width - 1 && ny in 1 until state.height - 1) {
        val nt = state.getTerrain(nx, ny)
        if (!nt.isWater && nt != TerrainType.MOUNTAIN) {
          fallbackNeighbors.add(nx to ny)
        }
      }
    }
    if (fallbackNeighbors.isNotEmpty()) {
      val cityZonePref = fallbackNeighbors.filter { state.getTerrain(it.first, it.second) == TerrainType.CITY_ZONE }
      val pool = if (cityZonePref.isNotEmpty()) cityZonePref else fallbackNeighbors
      val forward = pool.filter { it.first != v.prevTileX || it.second != v.prevTileY }
      val finalPool = if (forward.isNotEmpty()) forward else pool
      val pick = finalPool[rng.nextInt(finalPool.size)]
      v.prevTileX = curTx
      v.prevTileY = curTy
      v.targetX = pick.first + 0.5f
      v.targetY = pick.second + 0.5f
    } else {
      v.targetX = curTx + 0.5f
      v.targetY = curTy + 0.5f
    }
  }

  private fun findStepTowardNearestRoad(state: WorldState, startX: Int, startY: Int): Pair<Int, Int>? {
    val dirs = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)
    for (d in 0 until 4) {
      val nx = startX + dirs[d * 2]
      val ny = startY + dirs[d * 2 + 1]
      if (nx in 1 until state.width - 1 && ny in 1 until state.height - 1) {
        if (state.getTerrain(nx, ny) == TerrainType.ROAD) return nx to ny
      }
    }

    val radius = 10
    val boxSize = radius * 2 + 1
    val minX = startX - radius
    val minY = startY - radius
    val total = boxSize * boxSize
    val visited = BooleanArray(total)
    val parent = IntArray(total) { -1 }
    val queue = IntArray(total)
    var head = 0
    var tail = 0

    val startLocal = radius * boxSize + radius
    visited[startLocal] = true
    queue[tail++] = startLocal

    while (head < tail) {
      val cur = queue[head++]
      val lx = cur % boxSize
      val ly = cur / boxSize
      val wx = minX + lx
      val wy = minY + ly

      for (d in 0 until 4) {
        val nlx = lx + dirs[d * 2]
        val nly = ly + dirs[d * 2 + 1]
        if (nlx !in 0 until boxSize || nly !in 0 until boxSize) continue
        val nxt = nly * boxSize + nlx
        if (visited[nxt]) continue

        val nwx = minX + nlx
        val nwy = minY + nly
        if (nwx !in 1 until state.width - 1 || nwy !in 1 until state.height - 1) continue
        val t = state.getTerrain(nwx, nwy)
        if (t.isWater || t == TerrainType.MOUNTAIN) continue

        visited[nxt] = true
        parent[nxt] = cur
        if (t == TerrainType.ROAD) {
          var step = nxt
          while (parent[step] != startLocal && parent[step] != -1) {
            step = parent[step]
          }
          val sx = minX + (step % boxSize)
          val sy = minY + (step / boxSize)
          return sx to sy
        }
        queue[tail++] = nxt
      }
    }
    return null
  }

  private fun updateAnimals(state: WorldState, simDt: Float) {
    for (a in state.animals) {
      if (rng.nextFloat() < simDt * 0.8f) {
        a.vx = (rng.nextFloat() - 0.5f) * (if (a.type == AnimalType.BIRD) 2.8f else 1.2f)
        a.vy = (rng.nextFloat() - 0.5f) * (if (a.type == AnimalType.BIRD) 2.8f else 1.2f)
      }
      val nx = (a.x + a.vx * simDt).coerceIn(1f, state.width - 2f)
      val ny = (a.y + a.vy * simDt).coerceIn(1f, state.height - 2f)
      val t = state.getTerrain(nx.toInt(), ny.toInt())
      if (a.type == AnimalType.BIRD || t == TerrainType.GRASS || t == TerrainType.FOREST || t == TerrainType.SAND) {
        a.x = nx
        a.y = ny
      } else {
        a.vx = -a.vx
        a.vy = -a.vy
      }
    }
  }

  private fun updateNationsAndCities(state: WorldState, soundEngine: SirenSoundEngine) {
    if (state.nations.isEmpty()) return

    val nationById = state.nations.associateBy { it.id }

    // 0. City Blackout Detection (missing live POWER_PLANT or nation energy <= 5)
    for (city in state.cities) {
      val hasPowerPlant = state.buildings.any { it.cityId == city.id && it.type == BuildingType.POWER_PLANT && it.hp > 0 }
      val nEnergy = nationById[city.nationId]?.energy ?: 0
      if (!hasPowerPlant || nEnergy <= 5) {
        if (city.blackoutTimer < 4f) {
          city.blackoutTimer = 6f
        }
      }
    }
    val blackoutCityIds = state.cities.filter { it.blackoutTimer > 0f }.map { it.id }.toSet()

    // 1. Economy, Tech Progress, Stability, Rebellions & Autonomous Military Recruitment
    for (nation in state.nations.toList()) {
      val farms = state.buildings.count { it.nationId == nation.id && it.type == BuildingType.FARM }
      val factoryLevelSum =
        state.buildings
          .filter { it.nationId == nation.id && it.type == BuildingType.FACTORY && it.cityId !in blackoutCityIds }
          .sumOf { it.level }
      val plants = state.buildings.count { it.nationId == nation.id && it.type == BuildingType.POWER_PLANT }
      val pop = state.citizens.count { it.nationId == nation.id }
      val warDrain = if (nation.isAtWar()) 4 else 0
      val hasBlackout = state.cities.any { it.nationId == nation.id && it.blackoutTimer > 0f }

      nation.food = (nation.food + farms * 3 + 2 - pop / 6).coerceIn(0, 9999)
      nation.materials = (nation.materials + factoryLevelSum * 3 + 2 - warDrain).coerceIn(0, 9999)
      nation.energy = (nation.energy + plants * 4 + 2).coerceIn(0, 9999)
      nation.treasury = (nation.treasury + pop / 2 + factoryLevelSum * 2 - warDrain * 2).coerceIn(0, 99999)

      // Tech progression 1..4
      val techScore = factoryLevelSum * 2 + plants * 2 + (nation.treasury / 150) + ((state.year - nation.foundedYear) / 3)
      val targetTech =
        when {
          techScore >= 18 -> 4
          techScore >= 12 -> 3
          techScore >= 7 -> 2
          else -> 1
        }.coerceIn(1, 4)
      if (targetTech > nation.techLevel) {
        nation.techLevel = targetTech
        addNews(
          state,
          WorldEventCategory.CITY,
          "Технологический рывок",
          "${nation.name} достигла ${nation.techLevel}-го технологического уровня!",
          state.width / 2,
          state.height / 2,
          nation.colorArgb,
        )
      }

      // Stability update (0.1f..1.0f) & Rebellions
      var stabDelta = 0f
      if (nation.isAtWar()) stabDelta -= 0.018f
      if (nation.food < 10) stabDelta -= 0.03f
      if (hasBlackout) stabDelta -= 0.02f
      if (!nation.isAtWar() && nation.food >= 20 && !hasBlackout) stabDelta += 0.025f
      nation.stability = (nation.stability + stabDelta).coerceIn(0.1f, 1.0f)

      val nationCities = state.cities.filter { it.nationId == nation.id }
      if (nation.stability < 0.25f && nationCities.size >= 2 && rng.nextFloat() < 0.25f) {
        val rebelCity = nationCities.firstOrNull { !it.isCapital && it.id != nation.capitalCityId }
        if (rebelCity != null) {
          val newColor =
            NameGenerator.nationColors.firstOrNull { c -> state.nations.none { it.colorArgb == c } }
              ?: NameGenerator.nationColors[rng.nextInt(NameGenerator.nationColors.size)]
          val rebel = secedeCityAsNewNation(state, nation, rebelCity, newColor, atWarWithSource = true)
          nation.stability = 0.45f
          addNews(
            state,
            WorldEventCategory.WAR,
            "Мятеж в ${rebelCity.name}!",
            "Из-за низкой стабильности город ${rebelCity.name} отделился от ${nation.name} как ${rebel.name}.",
            rebelCity.centerX,
            rebelCity.centerY,
            rebel.colorArgb,
          )
        }
      }

      // Recruit military units based on population, resources & techLevel
      val currentUnits = state.militaryUnits.count { it.nationId == nation.id }
      val maxUnits = (pop / 3 + 2).coerceIn(2, 18)
      if (currentUnits < maxUnits && nation.materials >= 25 && nation.treasury >= 30 && rng.nextFloat() < 0.35f) {
        val base =
          state.buildings.firstOrNull { it.nationId == nation.id && it.type == BuildingType.MILITARY_BASE }
            ?: state.buildings.firstOrNull { it.nationId == nation.id && it.type == BuildingType.CAPITOL }
        val city = state.cities.firstOrNull { it.nationId == nation.id }
        if (city != null) {
          val uType =
            when {
              nation.techLevel == 1 -> UnitType.INFANTRY
              nation.techLevel == 2 ->
                when (rng.nextInt(10)) {
                  0, 1, 2, 3, 4, 5 -> UnitType.INFANTRY
                  6, 7, 8 -> UnitType.ARMOR
                  else -> UnitType.MILITARY_TRUCK
                }
              else ->
                when (rng.nextInt(10)) {
                  0, 1, 2, 3, 4 -> UnitType.INFANTRY
                  5, 6, 7 -> UnitType.ARMOR
                  8 -> UnitType.MILITARY_TRUCK
                  else -> UnitType.AIR_DEFENSE_UNIT
                }
            }
          val sx = base?.x ?: city.centerX
          val sy = base?.y ?: city.centerY
          state.militaryUnits.add(
            MilitaryUnit(
              id = state.allocId(),
              nationId = nation.id,
              cityId = city.id,
              type = uType,
              x = sx + 0.5f,
              y = sy + 0.5f,
              targetX = sx + rng.nextInt(-3, 4) + 0.5f,
              targetY = sy + rng.nextInt(-3, 4) + 0.5f,
              hp = uType.maxHp,
            )
          )
          nation.materials -= 18
          nation.treasury -= 22
        }
      }
    }

    // 2. Border Expansion, City Construction & Building Upgrades
    for (city in state.cities.toList()) {
      val nation = state.nations.find { it.id == city.nationId } ?: continue
      repeat(2) {
        val rx = (city.centerX + rng.nextInt(-10, 11)).coerceIn(1, state.width - 2)
        val ry = (city.centerY + rng.nextInt(-10, 11)).coerceIn(1, state.height - 2)
        val t = state.getTerrain(rx, ry)
        if (!t.isWater && state.getOwner(rx, ry) == 0 && hasOwnedNeighbor(state, rx, ry, nation.id)) {
          state.setOwner(rx, ry, nation.id)
        }
      }

      // Building Upgrade (level 1 -> 2 -> 3)
      if (!city.underAirAlert && city.blackoutTimer <= 0f && nation.materials >= 45 && rng.nextFloat() < 0.25f) {
        val upgradeIdx =
          state.buildings.indices.firstOrNull { idx ->
            val b = state.buildings[idx]
            b.cityId == city.id &&
              b.level < 3 &&
              (b.type == BuildingType.HOUSE || b.type == BuildingType.FACTORY || b.type == BuildingType.RADAR)
          }
        if (upgradeIdx != null) {
          val b = state.buildings[upgradeIdx]
          val nextLevel = b.level + 1
          val newMaxHp = (b.type.maxHp * (1f + (nextLevel - 1) * 0.35f)).toInt()
          val newHp = (b.hp + (newMaxHp - b.maxHp)).coerceAtMost(newMaxHp)
          state.buildings[upgradeIdx] = b.copy(level = nextLevel, maxHp = newMaxHp, hp = newHp)
          nation.materials -= 30
        }
      }

      if (nation.materials >= 35 && rng.nextFloat() < 0.25f) {
        val bx = (city.centerX + rng.nextInt(-4, 5)).coerceIn(1, state.width - 2)
        val by = (city.centerY + rng.nextInt(-4, 5)).coerceIn(1, state.height - 2)
        val t = state.getTerrain(bx, by)
        if (t.isBuildable && state.getOwner(bx, by) == nation.id && state.buildings.none { it.x == bx && it.y == by }) {
          val types =
            mutableListOf(
              BuildingType.HOUSE,
              BuildingType.FARM,
              BuildingType.FACTORY,
              BuildingType.RADAR,
              BuildingType.SHELTER,
              BuildingType.MILITARY_BASE,
              BuildingType.POWER_PLANT,
            ).apply {
              if (nation.techLevel >= 4) add(BuildingType.EW_STATION)
            }
          val bt = types[rng.nextInt(types.size)]
          if (bt != BuildingType.FARM) state.setTerrain(bx, by, TerrainType.CITY_ZONE)
          state.buildings.add(
            Building(
              id = state.allocId(),
              x = bx,
              y = by,
              type = bt,
              cityId = city.id,
              nationId = nation.id,
              hp = bt.maxHp,
              maxHp = bt.maxHp,
            )
          )
          nation.materials -= 20
        }
      }

      // Spawn replacement/extra civilian road traffic for cities that have roads
      val cityVehicleCount = state.vehicles.count { it.cityId == city.id }
      if (cityVehicleCount < 2 && state.vehicles.size < 45 && !city.underAirAlert && nation.materials >= 20 && rng.nextFloat() < 0.2f) {
        var roadSpawn: Pair<Int, Int>? = null
        for (dy in -3..3) {
          for (dx in -3..3) {
            val rx = city.centerX + dx
            val ry = city.centerY + dy
            if (state.inBounds(rx, ry) && state.getTerrain(rx, ry) == TerrainType.ROAD) {
              roadSpawn = rx to ry
              break
            }
          }
          if (roadSpawn != null) break
        }
        if (roadSpawn != null) {
          val vTypes = listOf(VehicleType.CAR, VehicleType.TRUCK, VehicleType.PATROL_JEEP, VehicleType.AMBULANCE)
          val vType = vTypes[rng.nextInt(vTypes.size)]
          state.vehicles.add(
            Vehicle(
              id = state.allocId(),
              x = roadSpawn.first + 0.5f,
              y = roadSpawn.second + 0.5f,
              targetX = roadSpawn.first + 0.5f,
              targetY = roadSpawn.second + 0.5f,
              type = vType,
              nationId = nation.id,
              cityId = city.id,
              prevTileX = roadSpawn.first,
              prevTileY = roadSpawn.second,
            )
          )
          nation.materials -= 8
        }
      }

      val cityPop = state.citizens.count { it.cityId == city.id }
      if (cityPop >= 14 && state.cities.size < 22 && nation.materials >= 60 && rng.nextFloat() < 0.06f) {
        val nx = (city.centerX + rng.nextInt(-18, 19)).coerceIn(6, state.width - 7)
        val ny = (city.centerY + rng.nextInt(-18, 19)).coerceIn(6, state.height - 7)
        val t = state.getTerrain(nx, ny)
        val farEnough = state.cities.none { hypot((it.centerX - nx).toFloat(), (it.centerY - ny).toFloat()) < 11f }
        if (t.isBuildable && t != TerrainType.MOUNTAIN && farEnough) {
          val usedNames = state.cities.map { it.name }.toSet()
          val newCity =
            City(
              id = state.allocId(),
              name = NameGenerator.randomCityName(rng, usedNames),
              nationId = nation.id,
              centerX = nx,
              centerY = ny,
              foundedYear = state.year,
            )
          state.cities.add(newCity)
          WorldGenerator.establishCityInfrastructure(state, newCity, nation, 0.7f, rng, radius = 4)
          WorldGenerator.buildRoadBetween(state, city.centerX, city.centerY, nx, ny)
          nation.materials -= 50
          addNews(
            state,
            WorldEventCategory.CITY,
            "Основан город ${newCity.name}",
            "${nation.name} основала новое поселение ${newCity.name}.",
            nx,
            ny,
            nation.colorArgb,
          )
        }
      }
    }

    // 3. Diplomacy, Trade & War
    if (state.nations.size >= 2) {
      val a = state.nations[rng.nextInt(state.nations.size)]
      val b = state.nations[rng.nextInt(state.nations.size)]
      if (a.id != b.id) {
        val rel = a.relations[b.id] ?: RelationType.NEUTRAL
        if (!state.warsEnabled && rel == RelationType.WAR) {
          endWar(state, a, b)
        } else if (rng.nextFloat() < 0.14f) {
          when (rel) {
            RelationType.NEUTRAL -> {
              val nextRel =
                if (state.warsEnabled && rng.nextFloat() < 0.35f) RelationType.TENSION else RelationType.TRADE
              setRelation(a, b, nextRel)
              if (nextRel == RelationType.TRADE) {
                addNews(
                  state,
                  WorldEventCategory.DIPLOMACY,
                  "Торговый договор",
                  "${a.name} и ${b.name} открыли торговые маршруты.",
                  state.width / 2,
                  state.height / 2,
                  a.colorArgb,
                )
              }
            }
            RelationType.TRADE -> {
              if (rng.nextFloat() < 0.4f) {
                setRelation(a, b, RelationType.ALLIANCE)
                addNews(
                  state,
                  WorldEventCategory.DIPLOMACY,
                  "Заключён союз",
                  "${a.name} и ${b.name} подписали союзный пакт.",
                  state.width / 2,
                  state.height / 2,
                  a.colorArgb,
                )
              }
            }
            RelationType.TENSION -> {
              if (state.warsEnabled && rng.nextFloat() < 0.45f) {
                declareWar(state, a, b)
              } else {
                setRelation(a, b, RelationType.NEUTRAL)
              }
            }
            RelationType.WAR -> {
              if (rng.nextFloat() < 0.12f) {
                endWar(state, a, b)
                addNews(
                  state,
                  WorldEventCategory.DIPLOMACY,
                  "Мирный договор",
                  "${a.name} и ${b.name} заключили перемирие.",
                  state.width / 2,
                  state.height / 2,
                  0xFF22C55E.toInt(),
                )
              }
            }
            RelationType.ALLIANCE -> Unit
          }
        }
      }
    }

    // 4. Autonomous Air Events (War Strikes gated by techLevel + Humanitarian Cargo Flights for Allies/Trade)
    if (state.airObjects.size < 16) {
      var spawnedFriendlyCargo = false
      for (a in state.nations.toList()) {
        if (spawnedFriendlyCargo) break
        for ((bId, rel) in a.relations) {
          if (a.id > bId) continue
          if (rel != RelationType.ALLIANCE && rel != RelationType.TRADE) continue
          val b = state.nations.find { it.id == bId } ?: continue
          if (rng.nextFloat() < 0.18f) {
            val cityA = state.cities.firstOrNull { it.nationId == a.id }
            val cityB = state.cities.firstOrNull { it.nationId == b.id }
            if (cityA != null && cityB != null) {
              val toB = rng.nextBoolean()
              val src = if (toB) cityA else cityB
              val dst = if (toB) cityB else cityA
              state.airObjects.add(
                AirObject(
                  id = state.allocId(),
                  x = src.centerX.toFloat(),
                  y = src.centerY.toFloat(),
                  startX = src.centerX.toFloat(),
                  startY = src.centerY.toFloat(),
                  targetX = dst.centerX.toFloat(),
                  targetY = dst.centerY.toFloat(),
                  speed = 0.25f,
                  type = AirObjectType.CARGO_PLANE,
                  nationId = dst.nationId,
                )
              )
              spawnedFriendlyCargo = true
              break
            }
          }
        }
      }
    }

    if (state.warsEnabled && state.airObjects.size < 14) {
      for (a in state.nations.toList()) {
        for ((bId, rel) in a.relations) {
          if (rel != RelationType.WAR || a.id > bId) continue
          val b = state.nations.find { it.id == bId } ?: continue
          if (rng.nextFloat() < 0.16f) {
            val attacker = if (rng.nextBoolean()) a else b
            val defender = if (attacker.id == a.id) b else a
            if (attacker.techLevel <= 1) continue
            val attackerCity = state.cities.firstOrNull { it.nationId == attacker.id }
            val defenderCity = state.cities.firstOrNull { it.nationId == defender.id }
            if (attackerCity != null && defenderCity != null) {
              val airType =
                when {
                  attacker.techLevel == 2 -> AirObjectType.MILITARY_RECON_UAV
                  attacker.techLevel == 3 ->
                    when (rng.nextInt(3)) {
                      0 -> AirObjectType.STRIKE_DRONE
                      1 -> AirObjectType.MILITARY_RECON_UAV
                      else -> AirObjectType.ROCKET_STREAK
                    }
                  else ->
                    when (rng.nextInt(4)) {
                      0 -> AirObjectType.STRIKE_DRONE
                      1 -> AirObjectType.MILITARY_RECON_UAV
                      2 -> AirObjectType.ROCKET_STREAK
                      else -> AirObjectType.HEAVY_BOMBER
                    }
                }
              state.airObjects.add(
                AirObject(
                  id = state.allocId(),
                  x = attackerCity.centerX.toFloat(),
                  y = attackerCity.centerY.toFloat(),
                  startX = attackerCity.centerX.toFloat(),
                  startY = attackerCity.centerY.toFloat(),
                  targetX = defenderCity.centerX.toFloat(),
                  targetY = defenderCity.centerY.toFloat(),
                  speed = if (airType == AirObjectType.STRIKE_DRONE || airType == AirObjectType.HEAVY_BOMBER) 0.24f else 0.38f,
                  type = airType,
                  nationId = attacker.id,
                )
              )
              state.totalEventsCount++
              state.warConflicts.firstOrNull {
                it.isActive && ((it.nationAId == a.id && it.nationBId == b.id) || (it.nationAId == b.id && it.nationBId == a.id))
              }?.let { it.airEventsCount++ }
              break
            }
          }
        }
      }
    }

    // 5. Collapse / Cleanup empty nations
    val collapsedIds = mutableListOf<Int>()
    val nationIt = state.nations.iterator()
    while (nationIt.hasNext()) {
      val n = nationIt.next()
      val hasCities = state.cities.any { it.nationId == n.id }
      if (!hasCities) {
        for (i in 0 until state.size) {
          if (state.nationOwner[i] == n.id) {
            state.nationOwner[i] = 0
            state.terrainDirty = true
          }
        }
        state.militaryUnits.removeAll { it.nationId == n.id }
        for (bIdx in state.buildings.indices) {
          val b = state.buildings[bIdx]
          if (b.nationId == n.id) state.buildings[bIdx] = b.copy(nationId = 0)
        }
        for (c in state.citizens) {
          if (c.nationId == n.id) c.nationId = 0
        }
        for (vIdx in state.vehicles.indices) {
          val v = state.vehicles[vIdx]
          if (v.nationId == n.id) state.vehicles[vIdx] = v.copy(nationId = 0)
        }
        for (air in state.airObjects) {
          if (air.nationId == n.id) air.nationId = 0
        }
        state.warConflicts.filter { it.isActive && (it.nationAId == n.id || it.nationBId == n.id) }.forEach {
          it.isActive = false
          it.endYear = state.year
        }
        addNews(
          state,
          WorldEventCategory.WAR,
          "Распад государства",
          "${n.name} прекратила существование и распалась.",
          state.width / 2,
          state.height / 2,
          n.colorArgb,
        )
        collapsedIds.add(n.id)
        nationIt.remove()
      }
    }
    if (collapsedIds.isNotEmpty()) {
      for (other in state.nations) {
        for (cid in collapsedIds) {
          other.relations.remove(cid)
        }
      }
    }
  }

  fun applyGodTool(
    state: WorldState,
    tool: GodTool,
    tileX: Int,
    tileY: Int,
    brushRadius: Int,
    selectedColor: Int,
    activeNationId: Int?,
    soundEngine: SirenSoundEngine,
    maxParticles: Int = 180,
  ) {
    if (!state.inBounds(tileX, tileY)) return

    when (tool) {
      GodTool.PAINT_GRASS -> paintTerrainCircle(state, tileX, tileY, brushRadius, TerrainType.GRASS, clearFire = true)
      GodTool.PAINT_WATER -> paintTerrainCircle(state, tileX, tileY, brushRadius, TerrainType.WATER, clearFire = true, clearBuildings = true)
      GodTool.PAINT_FOREST -> paintTerrainCircle(state, tileX, tileY, brushRadius, TerrainType.FOREST, landOnly = true)
      GodTool.PAINT_MOUNTAIN -> paintTerrainCircle(state, tileX, tileY, brushRadius, TerrainType.MOUNTAIN, clearBuildings = true)
      GodTool.PAINT_SAND -> paintTerrainCircle(state, tileX, tileY, brushRadius, TerrainType.SAND)
      GodTool.PAINT_ROAD -> paintTerrainCircle(state, tileX, tileY, brushRadius.coerceAtMost(1), TerrainType.ROAD, landOnly = true, allowRiverBridge = true)
      GodTool.ERASE_TERRAIN -> {
        paintTerrainCircle(state, tileX, tileY, brushRadius, TerrainType.DEEP_WATER, clearFire = true, clearBuildings = true, clearOwner = true)
      }

      GodTool.SPAWN_CITIZENS -> {
        val ownerId = state.getOwner(tileX, tileY)
        val city = findNearestCity(state, tileX, tileY)
        val count = (brushRadius * 2 + 1).coerceIn(2, 7)
        repeat(count) {
          val cx = (tileX + rng.nextInt(-brushRadius, brushRadius + 1)).coerceIn(1, state.width - 2)
          val cy = (tileY + rng.nextInt(-brushRadius, brushRadius + 1)).coerceIn(1, state.height - 2)
          if (state.getTerrain(cx, cy).isWalkable) {
            state.citizens.add(
              Citizen(
                id = state.allocId(),
                name = NameGenerator.randomCitizenName(rng),
                x = cx + 0.5f,
                y = cy + 0.5f,
                targetX = cx + 0.5f,
                targetY = cy + 0.5f,
                age = rng.nextInt(18, 40),
                hp = 100,
                nationId = ownerId,
                cityId = city?.id ?: 0,
                familyId = state.allocId(),
                profession = Profession.entries[rng.nextInt(Profession.entries.size)],
                homeX = city?.centerX ?: cx,
                homeY = city?.centerY ?: cy,
                workX = cx,
                workY = cy,
              )
            )
          }
        }
      }

      GodTool.SPAWN_ANIMALS -> {
        repeat((brushRadius + 2).coerceIn(2, 6)) {
          val ax = (tileX + rng.nextInt(-brushRadius, brushRadius + 1)).coerceIn(1, state.width - 2)
          val ay = (tileY + rng.nextInt(-brushRadius, brushRadius + 1)).coerceIn(1, state.height - 2)
          state.animals.add(
            Animal(
              id = state.allocId(),
              x = ax + 0.5f,
              y = ay + 0.5f,
              vx = (rng.nextFloat() - 0.5f),
              vy = (rng.nextFloat() - 0.5f),
              type = AnimalType.entries[rng.nextInt(AnimalType.entries.size)],
            )
          )
        }
      }

      GodTool.FOUND_CITY, GodTool.FOUND_SETTLEMENT -> {
        if (!state.getTerrain(tileX, tileY).isBuildable) {
          paintTerrainCircle(state, tileX, tileY, 3, TerrainType.GRASS, clearFire = true)
        }
        val existingOwnerId = state.getOwner(tileX, tileY)
        val nation =
          state.nations.find { it.id == existingOwnerId }
            ?: createNewNationInternal(state, selectedColor)
        val city =
          City(
            id = state.allocId(),
            name = NameGenerator.randomCityName(rng, state.cities.map { it.name }.toSet()),
            nationId = nation.id,
            centerX = tileX,
            centerY = tileY,
            isCapital = state.cities.none { it.nationId == nation.id },
            foundedYear = state.year,
          )
        if (city.isCapital) nation.capitalCityId = city.id
        state.cities.add(city)
        val radius = if (tool == GodTool.FOUND_CITY) 5 else 3
        val density = if (tool == GodTool.FOUND_CITY) 1.0f else 0.5f
        WorldGenerator.establishCityInfrastructure(state, city, nation, density, rng, radius = radius)
        val nearestOther =
          state.cities
            .filter { it.id != city.id }
            .minByOrNull { hypot((it.centerX - city.centerX).toFloat(), (it.centerY - city.centerY).toFloat()) }
        if (nearestOther != null &&
          hypot((nearestOther.centerX - city.centerX).toFloat(), (nearestOther.centerY - city.centerY).toFloat()) < state.width * 0.65f
        ) {
          WorldGenerator.buildRoadBetween(state, city.centerX, city.centerY, nearestOther.centerX, nearestOther.centerY)
        }
        addNews(
          state,
          WorldEventCategory.CITY,
          "Создан город ${city.name}",
          "На карте появилось новое поселение ${city.name} (${nation.name}).",
          tileX,
          tileY,
          nation.colorArgb,
        )
      }

      GodTool.SPAWN_INFANTRY, GodTool.SPAWN_ARMOR -> {
        val ownerId = state.getOwner(tileX, tileY)
        val nationId =
          if (ownerId > 0) ownerId
          else activeNationId ?: state.nations.firstOrNull()?.id ?: 0
        val city = findNearestCity(state, tileX, tileY)
        val uType = if (tool == GodTool.SPAWN_ARMOR) UnitType.ARMOR else UnitType.INFANTRY
        if (nationId > 0) {
          state.militaryUnits.add(
            MilitaryUnit(
              id = state.allocId(),
              nationId = nationId,
              cityId = city?.id ?: 0,
              type = uType,
              x = tileX + 0.5f,
              y = tileY + 0.5f,
              targetX = tileX + 0.5f,
              targetY = tileY + 0.5f,
              hp = uType.maxHp,
            )
          )
        }
      }

      GodTool.BUILD_MILITARY_BASE, GodTool.BUILD_PVO_RADAR, GodTool.BUILD_EW_STATION -> {
        if (!state.getTerrain(tileX, tileY).isBuildable) {
          paintTerrainCircle(state, tileX, tileY, 1, TerrainType.CITY_ZONE, clearFire = true)
        }
        val ownerId = state.getOwner(tileX, tileY).takeIf { it > 0 } ?: activeNationId ?: state.nations.firstOrNull()?.id ?: 0
        val city = findNearestCity(state, tileX, tileY)
        val bt =
          when (tool) {
            GodTool.BUILD_PVO_RADAR -> BuildingType.RADAR
            GodTool.BUILD_EW_STATION -> BuildingType.EW_STATION
            else -> BuildingType.MILITARY_BASE
          }
        state.buildings.removeAll { it.x == tileX && it.y == tileY }
        state.buildings.add(
          Building(
            id = state.allocId(),
            x = tileX,
            y = tileY,
            type = bt,
            cityId = city?.id ?: 0,
            nationId = ownerId,
            hp = bt.maxHp,
            maxHp = bt.maxHp,
          )
        )
        if (tool == GodTool.BUILD_PVO_RADAR && ownerId > 0) {
          state.militaryUnits.add(
            MilitaryUnit(
              id = state.allocId(),
              nationId = ownerId,
              cityId = city?.id ?: 0,
              type = UnitType.AIR_DEFENSE_UNIT,
              x = tileX + 0.5f,
              y = tileY + 0.5f,
              targetX = tileX + 0.5f,
              targetY = tileY + 0.5f,
              hp = UnitType.AIR_DEFENSE_UNIT.maxHp,
            )
          )
        }
      }

      GodTool.TOGGLE_GLOBAL_WARS -> {
        state.warsEnabled = !state.warsEnabled
        addNews(
          state,
          WorldEventCategory.DIPLOMACY,
          if (state.warsEnabled) "Войны разрешены" else "Эпоха мира",
          if (state.warsEnabled) "Государства вновь могут начинать военные конфликты." else "Все конфликты остановлены.",
          tileX,
          tileY,
          if (state.warsEnabled) 0xFFEF4444.toInt() else 0xFF22C55E.toInt(),
        )
      }

      GodTool.CREATE_NATION -> {
        if (!state.getTerrain(tileX, tileY).isBuildable) {
          paintTerrainCircle(state, tileX, tileY, 3, TerrainType.GRASS, clearFire = true)
        }
        val nation = createNewNationInternal(state, selectedColor)
        val city =
          City(
            id = state.allocId(),
            name = NameGenerator.randomCityName(rng, state.cities.map { it.name }.toSet()),
            nationId = nation.id,
            centerX = tileX,
            centerY = tileY,
            isCapital = true,
            foundedYear = state.year,
          )
        nation.capitalCityId = city.id
        state.cities.add(city)
        WorldGenerator.establishCityInfrastructure(state, city, nation, 1.0f, rng, radius = 5)
        val nearestOther =
          state.cities
            .filter { it.id != city.id }
            .minByOrNull { hypot((it.centerX - city.centerX).toFloat(), (it.centerY - city.centerY).toFloat()) }
        if (nearestOther != null &&
          hypot((nearestOther.centerX - city.centerX).toFloat(), (nearestOther.centerY - city.centerY).toFloat()) < state.width * 0.65f
        ) {
          WorldGenerator.buildRoadBetween(state, city.centerX, city.centerY, nearestOther.centerX, nearestOther.centerY)
        }
        addNews(
          state,
          WorldEventCategory.DIPLOMACY,
          "Новое государство: ${nation.name}",
          "Образована новая цивилизация со столицей ${city.name}.",
          tileX,
          tileY,
          nation.colorArgb,
        )
      }

      GodTool.PAINT_TERRITORY -> {
        val targetNation =
          state.nations.find { it.id == activeNationId }
            ?: state.nations.find { it.id == state.getOwner(tileX, tileY) }
            ?: state.nations.firstOrNull()
        if (targetNation != null) {
          targetNation.colorArgb = selectedColor
          for (dy in -brushRadius..brushRadius) {
            for (dx in -brushRadius..brushRadius) {
              if (dx * dx + dy * dy <= brushRadius * brushRadius) {
                val tx = tileX + dx
                val ty = tileY + dy
                if (state.inBounds(tx, ty) && !state.getTerrain(tx, ty).isWater) {
                  state.setOwner(tx, ty, targetNation.id)
                }
              }
            }
          }
        }
      }

      GodTool.MERGE_NATIONS -> {
        val ownerId = state.getOwner(tileX, tileY)
        val source = state.nations.find { it.id == ownerId } ?: return
        val target =
          state.nations.filter { it.id != source.id }.minByOrNull { other ->
            val oc = state.cities.firstOrNull { it.nationId == other.id }
            if (oc == null) Float.MAX_VALUE else hypot((oc.centerX - tileX).toFloat(), (oc.centerY - tileY).toFloat())
          } ?: return

        for (i in 0 until state.size) {
          if (state.nationOwner[i] == source.id) state.setOwner(i % state.width, i / state.width, target.id)
        }
        state.cities.filter { it.nationId == source.id }.forEach {
          it.nationId = target.id
          it.isCapital = false
        }
        state.citizens.filter { it.nationId == source.id }.forEach { it.nationId = target.id }
        state.militaryUnits.filter { it.nationId == source.id }.forEach { it.nationId = target.id }
        state.airObjects.filter { it.nationId == source.id }.forEach { it.nationId = target.id }
        state.buildings.indices.forEach { idx ->
          val b = state.buildings[idx]
          if (b.nationId == source.id) state.buildings[idx] = b.copy(nationId = target.id)
        }
        state.vehicles.indices.forEach { idx ->
          val v = state.vehicles[idx]
          if (v.nationId == source.id) state.vehicles[idx] = v.copy(nationId = target.id)
        }
        for (other in state.nations) {
          other.relations.remove(source.id)
        }
        state.warConflicts
          .filter { it.isActive && (it.nationAId == source.id || it.nationBId == source.id) }
          .forEach {
            it.isActive = false
            it.endYear = state.year
          }
        state.nations.removeAll { it.id == source.id }
        addNews(
          state,
          WorldEventCategory.DIPLOMACY,
          "Объединение держав",
          "${source.name} объединилась с ${target.name}.",
          tileX,
          tileY,
          target.colorArgb,
        )
      }

      GodTool.SPLIT_NATION -> {
        val ownerId = state.getOwner(tileX, tileY)
        val source = state.nations.find { it.id == ownerId } ?: return
        val newColor = NameGenerator.nationColors.firstOrNull { c -> state.nations.none { it.colorArgb == c } } ?: selectedColor
        val splitRadius = 9
        var rebelCity =
          state.cities.firstOrNull {
            it.nationId == source.id && hypot((it.centerX - tileX).toFloat(), (it.centerY - tileY).toFloat()) <= splitRadius
          }
        val createdNewCity = rebelCity == null
        if (rebelCity == null) {
          rebelCity =
            City(
              id = state.allocId(),
              name = NameGenerator.randomCityName(rng, state.cities.map { it.name }.toSet()),
              nationId = source.id,
              centerX = tileX,
              centerY = tileY,
              isCapital = false,
              foundedYear = state.year,
            )
          state.cities.add(rebelCity)
        }
        val rebel = secedeCityAsNewNation(state, source, rebelCity, newColor, atWarWithSource = false, claimOriginX = tileX, claimOriginY = tileY)
        if (createdNewCity) {
          WorldGenerator.establishCityInfrastructure(state, rebelCity, rebel, 0.8f, rng, radius = 4)
        }
        addNews(
          state,
          WorldEventCategory.DIPLOMACY,
          "Разделение государства",
          "От ${source.name} отделилось новое государство — ${rebel.name}.",
          tileX,
          tileY,
          rebel.colorArgb,
        )
      }

      GodTool.TOGGLE_NATION_WAR, GodTool.CYCLE_DIPLOMACY -> {
        val ownerId = state.getOwner(tileX, tileY)
        val a = state.nations.find { it.id == ownerId } ?: state.nations.firstOrNull() ?: return
        val b = state.nations.filter { it.id != a.id }.firstOrNull() ?: return
        val current = a.relations[b.id] ?: RelationType.NEUTRAL
        if (tool == GodTool.TOGGLE_NATION_WAR) {
          if (current == RelationType.WAR) {
            endWar(state, a, b)
            addNews(state, WorldEventCategory.DIPLOMACY, "Мир объявлен", "${a.name} и ${b.name} завершили войну.", tileX, tileY, 0xFF22C55E.toInt())
          } else {
            state.warsEnabled = true
            declareWar(state, a, b)
          }
        } else {
          val next = RelationType.entries[(current.ordinal + 1) % RelationType.entries.size]
          if (next == RelationType.WAR) {
            state.warsEnabled = true
            declareWar(state, a, b)
          } else {
            if (current == RelationType.WAR) endWar(state, a, b)
            setRelation(a, b, next)
            addNews(state, WorldEventCategory.DIPLOMACY, "Дипломатия: ${next.titleRu}", "${a.name} и ${b.name}: ${next.titleRu.lowercase()}.", tileX, tileY, a.colorArgb)
          }
        }
      }

      GodTool.TRIGGER_AIR_ALERT -> {
        val city = findNearestCity(state, tileX, tileY)
        if (city != null) {
          triggerCityAirAlert(state, city, soundEngine, triggerRocket = false)
        } else {
          soundEngine.playAirRaidSiren()
          addNews(state, WorldEventCategory.AIR_ALERT, "Учебная тревога", "В секторе ($tileX, $tileY) подан сигнал воздушной тревоги.", tileX, tileY, 0xFFF59E0B.toInt())
        }
      }

      GodTool.TRIGGER_WAR_EVENT -> {
        state.warsEnabled = true
        if (state.nations.size >= 2) {
          val a = state.nations[0]
          val b = state.nations[1]
          declareWar(state, a, b)
          val city = state.cities.firstOrNull { it.nationId == b.id }
          if (city != null) triggerCityAirAlert(state, city, soundEngine, triggerRocket = true)
        }
      }

      GodTool.SPAWN_RECON_DRONE -> spawnAirObject(state, tileX, tileY, AirObjectType.RECON_DRONE, 0.24f, activeNationId ?: 0)
      GodTool.SPAWN_STRIKE_DRONE -> {
        val nearCity = findNearestCity(state, tileX, tileY)
        if (nearCity != null && hypot((nearCity.centerX - tileX).toFloat(), (nearCity.centerY - tileY).toFloat()) < 18f) {
          triggerCityAirAlert(state, nearCity, soundEngine, triggerRocket = false)
        }
        spawnAirObject(state, tileX, tileY, AirObjectType.STRIKE_DRONE, 0.26f, 0)
      }
      GodTool.SPAWN_JET_PATROL -> spawnAirObject(state, tileX, tileY, AirObjectType.JET_PATROL, 0.36f, activeNationId ?: 0)
      GodTool.SPAWN_MILITARY_UAV -> spawnAirObject(state, tileX, tileY, AirObjectType.MILITARY_RECON_UAV, 0.28f, activeNationId ?: 0)
      GodTool.SPAWN_HEAVY_BOMBER -> spawnAirObject(state, tileX, tileY, AirObjectType.HEAVY_BOMBER, 0.22f, 0)

      GodTool.SPAWN_PVO_INTERCEPTOR -> {
        val threat = state.airObjects.firstOrNull { it.type.isHostileThreat }
        if (threat != null) {
          state.airObjects.add(
            AirObject(
              id = state.allocId(),
              x = tileX.toFloat(),
              y = tileY.toFloat(),
              startX = tileX.toFloat(),
              startY = tileY.toFloat(),
              targetX = threat.x,
              targetY = threat.y,
              speed = 0.85f,
              type = AirObjectType.PVO_INTERCEPTOR,
              nationId = activeNationId ?: 0,
              targetAirObjectId = threat.id,
            )
          )
        } else {
          spawnAirObject(state, tileX, tileY, AirObjectType.PVO_INTERCEPTOR, 0.65f, activeNationId ?: 0)
        }
      }

      GodTool.TRIGGER_ROCKET_EVENT -> {
        val nearestCity = findNearestCity(state, tileX, tileY)
        if (nearestCity != null && hypot((nearestCity.centerX - tileX).toFloat(), (nearestCity.centerY - tileY).toFloat()) < 16f) {
          triggerCityAirAlert(state, nearestCity, soundEngine, triggerRocket = false)
        }
        spawnAirObject(state, tileX, tileY, AirObjectType.ROCKET_STREAK, 0.45f, 0)
      }

      GodTool.TRIGGER_EXPLOSION -> {
        spawnExplosion(state, tileX, tileY, radius = brushRadius.coerceIn(2, 5), ignite = true, soundEngine = soundEngine, maxParticles = maxParticles)
      }

      GodTool.CYCLE_WEATHER -> {
        val entries = WeatherType.entries
        state.weather = entries[(state.weather.ordinal + 1) % entries.size]
        state.weatherTimer = 45f
        addNews(state, WorldEventCategory.WEATHER, "Смена погоды", "Погода изменена на: ${state.weather.titleRu}.", tileX, tileY, 0xFF38BDF8.toInt())
      }

      GodTool.RANDOM_EVENT -> triggerRandomEvent(state, tileX, tileY, soundEngine, maxParticles)

      GodTool.EMP_STRIKE -> {
        val empRadius = 12f
        state.airObjects.removeAll { air ->
          val hit =
            (air.type == AirObjectType.STRIKE_DRONE ||
              air.type == AirObjectType.RECON_DRONE ||
              air.type == AirObjectType.MILITARY_RECON_UAV ||
              air.type == AirObjectType.ROCKET_STREAK) &&
              hypot(air.x - tileX, air.y - tileY) <= empRadius
          if (hit && state.particles.size < maxParticles) {
            state.particles.add(
              WorldParticle(
                id = state.allocId(),
                x = air.x,
                y = air.y,
                vx = 0f,
                vy = 0f,
                life = 0.45f,
                maxLife = 0.45f,
                size = 1.6f,
                colorArgb = 0xFF38BDF8.toInt(),
                type = ParticleType.SPARK,
              )
            )
          }
          hit
        }
        state.cities.filter { hypot((it.centerX - tileX).toFloat(), (it.centerY - tileY).toFloat()) <= empRadius }
          .forEach { it.blackoutTimer = maxOf(it.blackoutTimer, 18f) }
        for (idx in state.buildings.indices) {
          val b = state.buildings[idx]
          if (b.type == BuildingType.RADAR && hypot((b.x - tileX).toFloat(), (b.y - tileY).toFloat()) <= empRadius) {
            state.buildings[idx] = b.copy(pvoCooldown = maxOf(b.pvoCooldown, 12f))
          }
        }
        state.militaryUnits.filter { it.type.isArmor && hypot(it.x - tileX, it.y - tileY) <= empRadius }
          .forEach { it.empDisabledTimer = maxOf(it.empDisabledTimer, 10f) }
        if (state.particles.size < maxParticles) {
          state.particles.add(
            WorldParticle(
              id = state.allocId(),
              x = tileX + 0.5f,
              y = tileY + 0.5f,
              vx = 0f,
              vy = 0f,
              life = 0.75f,
              maxLife = 0.75f,
              size = 12f,
              colorArgb = 0xFF22D3EE.toInt(),
              type = ParticleType.EMP_WAVE,
            )
          )
        }
        state.totalEventsCount++
        soundEngine.playExplosion()
        addNews(
          state,
          WorldEventCategory.DISASTER,
          "ЭМИ-импульс",
          "Электромагнитный импульс вывел из строя электронику и дроны в секторе ($tileX, $tileY).",
          tileX,
          tileY,
          0xFF22D3EE.toInt(),
        )
      }

      GodTool.REMOVE_BUILDINGS -> {
        val removed = state.buildings.count { hypot((it.x - tileX).toFloat(), (it.y - tileY).toFloat()) <= brushRadius }
        state.totalDestroyedBuildings += removed
        state.buildings.removeAll { hypot((it.x - tileX).toFloat(), (it.y - tileY).toFloat()) <= brushRadius }
      }

      GodTool.CREATE_RUINS -> {
        for (dy in -brushRadius..brushRadius) {
          for (dx in -brushRadius..brushRadius) {
            if (dx * dx + dy * dy <= brushRadius * brushRadius) {
              val tx = tileX + dx
              val ty = tileY + dy
              if (state.inBounds(tx, ty) && !state.getTerrain(tx, ty).isWater) {
                state.setTerrain(tx, ty, TerrainType.RUINS)
              }
            }
          }
        }
        state.buildings.indices.forEach { idx ->
          val b = state.buildings[idx]
          if (hypot((b.x - tileX).toFloat(), (b.y - cyFloat(tileY))) <= brushRadius && b.type != BuildingType.RUINED_BUILDING) {
            state.buildings[idx] = b.copy(type = BuildingType.RUINED_BUILDING, hp = 25)
            state.totalDestroyedBuildings++
          }
        }
      }

      GodTool.IGNITE_FIRE -> {
        for (dy in -brushRadius..brushRadius) {
          for (dx in -brushRadius..brushRadius) {
            if (dx * dx + dy * dy <= brushRadius * brushRadius) {
              val tx = tileX + dx
              val ty = tileY + dy
              if (state.inBounds(tx, ty) && !state.getTerrain(tx, ty).isWater) {
                state.fireGrid[state.idx(tx, ty)] = 85
              }
            }
          }
        }
      }

      GodTool.DROP_METEOR -> {
        val startX = (tileX - 18).coerceIn(1, state.width - 2).toFloat()
        val startY = 1f
        state.airObjects.add(
          AirObject(
            id = state.allocId(),
            x = startX,
            y = startY,
            startX = startX,
            startY = startY,
            targetX = tileX.toFloat(),
            targetY = tileY.toFloat(),
            speed = 0.55f,
            type = AirObjectType.METEOR,
            nationId = 0,
          )
        )
        state.totalEventsCount++
        addNews(state, WorldEventCategory.DISASTER, "Падение метеорита", "Крупный метеорит вошёл в атмосферу!", tileX, tileY, 0xFFF97316.toInt())
      }

      GodTool.RESTORE_AREA -> {
        for (dy in -brushRadius..brushRadius) {
          for (dx in -brushRadius..brushRadius) {
            if (dx * dx + dy * dy <= brushRadius * brushRadius) {
              val tx = tileX + dx
              val ty = tileY + dy
              if (state.inBounds(tx, ty)) {
                val i = state.idx(tx, ty)
                state.fireGrid[i] = 0
                if (state.radiationGrid[i] > 0) {
                  state.radiationGrid[i] = 0
                  state.terrainDirty = true
                }
                if (state.getTerrain(tx, ty) == TerrainType.RUINS) {
                  state.setTerrain(tx, ty, TerrainType.GRASS)
                }
              }
            }
          }
        }
        state.buildings.indices.forEach { idx ->
          val b = state.buildings[idx]
          if (hypot((b.x - tileX).toFloat(), (b.y - tileY).toFloat()) <= brushRadius) {
            val wasRuined = b.type == BuildingType.RUINED_BUILDING
            val restoredType = if (wasRuined) BuildingType.HOUSE else b.type
            state.buildings[idx] =
              b.copy(type = restoredType, hp = restoredType.maxHp, maxHp = restoredType.maxHp, isBurning = false)
            if (wasRuined) state.totalRebuiltBuildings++
          }
        }
        state.cities.filter { hypot((it.centerX - tileX).toFloat(), (it.centerY - tileY).toFloat()) <= brushRadius + 2 }
          .forEach { it.blackoutTimer = 0f }
        state.citizens.filter { hypot(it.x - tileX, it.y - tileY) <= brushRadius + 2 }.forEach { it.hp = it.maxHp }
        state.militaryUnits.filter { hypot(it.x - tileX, it.y - tileY) <= brushRadius + 2 }.forEach {
          it.hp = it.maxHp
          it.empDisabledTimer = 0f
        }
      }

      GodTool.NUKE_STRIKE -> {
        spawnExplosion(
          state = state,
          cx = tileX,
          cy = tileY,
          radius = 6,
          ignite = true,
          soundEngine = soundEngine,
          maxParticles = maxParticles,
          buildingDamage = 260,
          citizenDamage = 95,
          unitDamage = 180,
        )
        for (dy in -8..8) {
          for (dx in -8..8) {
            val dist = hypot(dx.toFloat(), dy.toFloat())
            if (dist <= 8f) {
              val tx = tileX + dx
              val ty = tileY + dy
              if (state.inBounds(tx, ty)) {
                val radVal = (100 - (dist * 2.5f).toInt()).coerceIn(80, 100)
                val i = state.idx(tx, ty)
                state.radiationGrid[i] = maxOf(state.radiationGrid[i], radVal)
              }
            }
          }
        }
        state.terrainDirty = true
        if (state.particles.size < maxParticles) {
          state.particles.add(
            WorldParticle(
              id = state.allocId(),
              x = tileX + 0.5f,
              y = tileY + 0.5f,
              vx = 0f,
              vy = 0f,
              life = 1.2f,
              maxLife = 1.2f,
              size = 8.5f,
              colorArgb = 0xFF84CC16.toInt(),
              type = ParticleType.RADIATION_GLOW,
            )
          )
        }
        addNews(
          state,
          WorldEventCategory.DISASTER,
          "Ядерный удар!",
          "Мощный ядерный взрыв оставил зону радиационного заражения в секторе ($tileX, $tileY)!",
          tileX,
          tileY,
          0xFF84CC16.toInt(),
        )
      }
    }

    state.worldVersion++
  }

  private fun cyFloat(y: Int): Float = y.toFloat()

  private fun spawnAirObject(
    state: WorldState,
    targetX: Int,
    targetY: Int,
    type: AirObjectType,
    speed: Float,
    nationId: Int,
  ) {
    val startX = if (targetX > state.width / 2) 2f else (state.width - 3).toFloat()
    val startY = rng.nextInt(4, (state.height - 4).coerceAtLeast(5)).toFloat()
    state.airObjects.add(
      AirObject(
        id = state.allocId(),
        x = startX,
        y = startY,
        startX = startX,
        startY = startY,
        targetX = targetX.toFloat(),
        targetY = targetY.toFloat(),
        speed = speed,
        type = type,
        nationId = nationId,
      )
    )
    state.totalEventsCount++
  }

  private fun paintTerrainCircle(
    state: WorldState,
    cx: Int,
    cy: Int,
    radius: Int,
    terrain: TerrainType,
    clearFire: Boolean = false,
    clearBuildings: Boolean = false,
    clearOwner: Boolean = false,
    landOnly: Boolean = false,
    allowRiverBridge: Boolean = false,
  ) {
    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        if (dx * dx + dy * dy <= radius * radius) {
          val tx = cx + dx
          val ty = cy + dy
          if (!state.inBounds(tx, ty)) continue
          val cur = state.getTerrain(tx, ty)
          if (landOnly && cur.isWater && !(allowRiverBridge && cur == TerrainType.RIVER)) continue
          state.setTerrain(tx, ty, terrain)
          if (clearFire) state.fireGrid[state.idx(tx, ty)] = 0
          if (clearOwner) state.setOwner(tx, ty, 0)
        }
      }
    }
    if (clearBuildings) {
      state.buildings.removeAll { hypot((it.x - cx).toFloat(), (it.y - cy).toFloat()) <= radius }
    }
  }

  private fun createNewNationInternal(state: WorldState, color: Int): Nation {
    val used = state.nations.map { it.name }.toSet()
    val nation =
      Nation(
        id = state.allocId(),
        name = NameGenerator.randomNationName(rng, used),
        colorArgb = color,
        flag = NameGenerator.randomFlag(color, rng),
        capitalCityId = 0,
        foundedYear = state.year,
      )
    for (other in state.nations) {
      nation.relations[other.id] = RelationType.NEUTRAL
      other.relations[nation.id] = RelationType.NEUTRAL
    }
    state.nations.add(nation)
    return nation
  }

  private fun secedeCityAsNewNation(
    state: WorldState,
    source: Nation,
    rebelCity: City,
    newColor: Int,
    atWarWithSource: Boolean,
    claimOriginX: Int = rebelCity.centerX,
    claimOriginY: Int = rebelCity.centerY,
  ): Nation {
    val rebel = createNewNationInternal(state, newColor)
    val splitRadius = 9
    for (dy in -splitRadius..splitRadius) {
      for (dx in -splitRadius..splitRadius) {
        if (dx * dx + dy * dy <= splitRadius * splitRadius) {
          val tx = claimOriginX + dx
          val ty = claimOriginY + dy
          if (state.inBounds(tx, ty) && state.getOwner(tx, ty) == source.id) {
            state.setOwner(tx, ty, rebel.id)
          }
        }
      }
    }
    rebelCity.nationId = rebel.id
    rebelCity.isCapital = true
    rebel.capitalCityId = rebelCity.id
    val rebelCityId = rebelCity.id

    state.citizens
      .filter { it.cityId == rebelCityId || state.getOwner(it.x.toInt(), it.y.toInt()) == rebel.id }
      .forEach { it.nationId = rebel.id }
    state.militaryUnits
      .filter { it.cityId == rebelCityId || state.getOwner(it.x.toInt(), it.y.toInt()) == rebel.id }
      .forEach { it.nationId = rebel.id }
    for (air in state.airObjects) {
      if (air.nationId == source.id && state.getOwner(air.x.toInt(), air.y.toInt()) == rebel.id) {
        air.nationId = rebel.id
      }
    }
    for (bIdx in state.buildings.indices) {
      val b = state.buildings[bIdx]
      if (b.cityId == rebelCityId || state.getOwner(b.x, b.y) == rebel.id) {
        state.buildings[bIdx] = b.copy(nationId = rebel.id)
      }
    }
    for (vIdx in state.vehicles.indices) {
      val v = state.vehicles[vIdx]
      if (v.cityId == rebelCityId || state.getOwner(v.x.toInt(), v.y.toInt()) == rebel.id) {
        state.vehicles[vIdx] = v.copy(nationId = rebel.id)
      }
    }
    if (source.capitalCityId == rebelCityId) {
      val nextCapital = state.cities.firstOrNull { it.nationId == source.id && it.id != rebelCityId }
      if (nextCapital != null) {
        nextCapital.isCapital = true
        source.capitalCityId = nextCapital.id
      } else {
        source.capitalCityId = 0
      }
    }
    if (atWarWithSource) {
      if (state.warsEnabled) {
        declareWar(state, rebel, source)
      } else {
        setRelation(rebel, source, RelationType.TENSION)
      }
    }
    state.terrainDirty = true
    return rebel
  }

  fun declareWar(state: WorldState, a: Nation, b: Nation) {
    val alreadyAtWar = a.relations[b.id] == RelationType.WAR
    setRelation(a, b, RelationType.WAR)
    if (!alreadyAtWar) {
      state.totalWarsCount++
      state.warConflicts.add(
        0,
        WarConflict(
          id = state.allocId(),
          nationAId = a.id,
          nationBId = b.id,
          startYear = state.year,
          startMonth = state.month,
          isActive = true,
        ),
      )
      addNews(
        state,
        WorldEventCategory.WAR,
        "Война: ${a.name} — ${b.name}",
        "Между ${a.name} и ${b.name} вспыхнул современный конфликт!",
        state.width / 2,
        state.height / 2,
        0xFFEF4444.toInt(),
      )
      if (state.warsEnabled) {
        val alliesOfB =
          state.nations.filter { other ->
            other.id != a.id &&
              other.id != b.id &&
              b.relations[other.id] == RelationType.ALLIANCE &&
              a.relations[other.id] != RelationType.WAR
          }
        for (ally in alliesOfB) {
          declareWar(state, a, ally)
        }
      }
    }
  }

  fun endWar(state: WorldState, a: Nation, b: Nation) {
    setRelation(a, b, RelationType.NEUTRAL)
    state.warConflicts
      .filter { it.isActive && ((it.nationAId == a.id && it.nationBId == b.id) || (it.nationAId == b.id && it.nationBId == a.id)) }
      .forEach {
        it.isActive = false
        it.endYear = state.year
      }
  }

  private fun setRelation(a: Nation, b: Nation, rel: RelationType) {
    a.relations[b.id] = rel
    b.relations[a.id] = rel
  }

  fun triggerCityAirAlert(
    state: WorldState,
    city: City,
    soundEngine: SirenSoundEngine,
    triggerRocket: Boolean,
  ) {
    city.underAirAlert = true
    city.airAlertTimer = 14f
    city.smokeLevel = (city.smokeLevel + 0.35f).coerceAtMost(1f)
    state.totalEventsCount++
    soundEngine.playAirRaidSiren()

    val nation = state.nations.find { it.id == city.nationId }
    addNews(
      state,
      WorldEventCategory.AIR_ALERT,
      "Воздушная тревога: ${city.name}",
      "В городе ${city.name} звучит сирена! Жители направляются в укрытия.",
      city.centerX,
      city.centerY,
      0xFFEF4444.toInt(),
    )

    if (triggerRocket) {
      val startX = if (city.centerX > state.width / 2) 3f else (state.width - 4).toFloat()
      val startY = 3f
      state.airObjects.add(
        AirObject(
          id = state.allocId(),
          x = startX,
          y = startY,
          startX = startX,
          startY = startY,
          targetX = (city.centerX + rng.nextInt(-2, 3)).toFloat(),
          targetY = (city.centerY + rng.nextInt(-2, 3)).toFloat(),
          speed = 0.38f,
          type = if (rng.nextBoolean()) AirObjectType.STRIKE_DRONE else AirObjectType.ROCKET_STREAK,
          nationId = nation?.id ?: 0,
        )
      )
    }
  }

  private fun spawnExplosion(
    state: WorldState,
    cx: Int,
    cy: Int,
    radius: Int,
    ignite: Boolean,
    soundEngine: SirenSoundEngine,
    playSound: Boolean = true,
    maxParticles: Int = 180,
    buildingDamage: Int = 75,
    citizenDamage: Int = 45,
    unitDamage: Int? = null,
  ) {
    if (playSound) soundEngine.playExplosion()
    state.totalEventsCount++

    if (state.particles.size < maxParticles) {
      state.particles.add(
        WorldParticle(
          id = state.allocId(),
          x = cx + 0.5f,
          y = cy + 0.5f,
          vx = 0f,
          vy = 0f,
          life = 0.55f,
          maxLife = 0.55f,
          size = radius.toFloat() * 1.8f,
          colorArgb = 0xFFFB923C.toInt(),
          type = ParticleType.SHOCKWAVE,
        )
      )
    }

    repeat(14) {
      if (state.particles.size >= maxParticles) return@repeat
      val angle = rng.nextFloat() * 6.283f
      val speed = rng.nextFloat() * 6f + 2f
      state.particles.add(
        WorldParticle(
          id = state.allocId(),
          x = cx + 0.5f,
          y = cy + 0.5f,
          vx = cos(angle) * speed,
          vy = sin(angle) * speed,
          life = 0.65f,
          maxLife = 0.65f,
          size = 1.5f,
          colorArgb = if (it % 2 == 0) 0xFFFACC15.toInt() else 0xFFEF4444.toInt(),
          type = ParticleType.EXPLOSION,
        )
      )
    }

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        if (dx * dx + dy * dy <= radius * radius) {
          val tx = cx + dx
          val ty = cy + dy
          if (!state.inBounds(tx, ty)) continue
          val t = state.getTerrain(tx, ty)
          if (!t.isWater) {
            state.setTerrain(tx, ty, TerrainType.RUINS)
            if (ignite && rng.nextFloat() < 0.65f) {
              state.fireGrid[state.idx(tx, ty)] = 75
            }
          }
        }
      }
    }

    state.buildings.indices.forEach { idx ->
      val b = state.buildings[idx]
      if (hypot((b.x - cx).toFloat(), (b.y - cy).toFloat()) <= radius + 0.5f) {
        val newHp = b.hp - buildingDamage
        if (newHp <= 0 && b.type != BuildingType.RUINED_BUILDING) {
          state.buildings[idx] = b.copy(type = BuildingType.RUINED_BUILDING, hp = 25, isBurning = ignite)
          state.totalDestroyedBuildings++
        } else if (newHp > 0) {
          state.buildings[idx] = b.copy(hp = newHp, isBurning = ignite)
        }
      }
    }

    for (c in state.citizens) {
      val d = hypot(c.x - cx, c.y - cy)
      if (d <= radius + 1.5f) {
        val reduction = if (c.state == CitizenState.IN_SHELTER) (citizenDamage / 3).coerceAtLeast(10) else citizenDamage
        c.hp -= reduction
        if (c.hp <= 0) {
          state.nations.find { it.id == c.nationId }?.let { it.totalCasualties++ }
        }
      }
    }

    val unitIt = state.militaryUnits.iterator()
    while (unitIt.hasNext()) {
      val unit = unitIt.next()
      val d = hypot(unit.x - cx, unit.y - cy)
      if (d <= radius + 1.5f) {
        val dmg = unitDamage ?: if (unit.type.isArmor) 40 else 65
        unit.hp -= dmg
        if (unit.hp <= 0) {
          state.nations.find { it.id == unit.nationId }?.let {
            it.totalCasualties++
            it.losses++
          }
          unitIt.remove()
        }
      }
    }

    val nearCity = findNearestCity(state, cx, cy)
    if (nearCity != null && hypot((nearCity.centerX - cx).toFloat(), (nearCity.centerY - cy).toFloat()) < 10f) {
      nearCity.underAirAlert = true
      nearCity.airAlertTimer = 10f
      nearCity.smokeLevel = (nearCity.smokeLevel + 0.4f).coerceAtMost(1f)
    }
  }

  private fun triggerRandomEvent(
    state: WorldState,
    tx: Int,
    ty: Int,
    soundEngine: SirenSoundEngine,
    maxParticles: Int,
  ) {
    when (rng.nextInt(4)) {
      0 -> {
        val city = state.cities.randomOrNull(rng)
        if (city != null) {
          triggerCityAirAlert(state, city, soundEngine, triggerRocket = true)
        }
      }
      1 -> {
        state.weather = WeatherType.STORM
        state.weatherTimer = 30f
        addNews(state, WorldEventCategory.WEATHER, "Внезапная буря", "Над миром разразилась гроза.", tx, ty, 0xFF38BDF8.toInt())
      }
      2 -> {
        for (n in state.nations) {
          n.food += 50
          n.materials += 40
        }
        addNews(state, WorldEventCategory.DIPLOMACY, "Эпоха процветания", "Все государства получили прирост ресурсов!", tx, ty, 0xFF22C55E.toInt())
      }
      else -> {
        applyGodTool(state, GodTool.DROP_METEOR, tx, ty, 3, 0xFFF59E0B.toInt(), null, soundEngine, maxParticles)
      }
    }
  }

  private fun stepAroundObstacle(
    state: WorldState,
    x: Float,
    y: Float,
    dx: Float,
    dy: Float,
    dist: Float,
    stepDist: Float,
    allowWater: Boolean,
    minBound: Float,
    maxOffset: Float,
  ): Pair<Float, Float>? {
    val ux = dx / dist
    val uy = dy / dist
    val maxX = state.width - maxOffset
    val maxY = state.height - maxOffset
    val angles = floatArrayOf(0f, 0.7853982f, -0.7853982f, 1.5707964f, -1.5707964f)
    for (ang in angles) {
      val c = cos(ang)
      val s = sin(ang)
      val vx = ux * c - uy * s
      val vy = ux * s + uy * c
      val nx = (x + vx * stepDist).coerceIn(minBound, maxX)
      val ny = (y + vy * stepDist).coerceIn(minBound, maxY)
      if (isTilePassable(state.getTerrain(nx.toInt(), ny.toInt()), allowWater)) {
        return nx to ny
      }
    }
    return null
  }

  private fun isTilePassable(terrain: TerrainType, allowWater: Boolean): Boolean {
    if (terrain == TerrainType.MOUNTAIN) return false
    if (terrain.isWater && terrain != TerrainType.RIVER) return allowWater
    return terrain.isWalkable
  }

  private fun findNearestCity(state: WorldState, x: Int, y: Int): City? {
    return state.cities.minByOrNull { hypot((it.centerX - x).toFloat(), (it.centerY - y).toFloat()) }
  }

  private fun hasOwnedNeighbor(state: WorldState, x: Int, y: Int, nationId: Int): Boolean {
    return state.getOwner(x + 1, y) == nationId ||
      state.getOwner(x - 1, y) == nationId ||
      state.getOwner(x, y + 1) == nationId ||
      state.getOwner(x, y - 1) == nationId
  }

  private fun isFireNear(state: WorldState, x: Int, y: Int, radius: Int): Boolean {
    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val tx = x + dx
        val ty = y + dy
        if (state.inBounds(tx, ty) && state.fireGrid[state.idx(tx, ty)] > 0) return true
      }
    }
    return false
  }

  private fun addNews(
    state: WorldState,
    category: WorldEventCategory,
    title: String,
    desc: String,
    x: Int,
    y: Int,
    color: Int,
  ) {
    state.newsLog.add(
      0,
      WorldNewsItem(
        id = state.allocId(),
        year = state.year,
        month = state.month,
        category = category,
        title = title,
        description = desc,
        x = x,
        y = y,
        nationColorArgb = color,
      ),
    )
    if (state.newsLog.size > 60) {
      state.newsLog.removeAt(state.newsLog.lastIndex)
    }
  }
}
