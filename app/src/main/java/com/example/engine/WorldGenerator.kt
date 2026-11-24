package com.example.engine

import com.example.model.Animal
import com.example.model.AnimalType
import com.example.model.Building
import com.example.model.BuildingType
import com.example.model.Citizen
import com.example.model.CitizenState
import com.example.model.City
import com.example.model.Nation
import com.example.model.Profession
import com.example.model.RelationType
import com.example.model.TerrainType
import com.example.model.Vehicle
import com.example.model.VehicleType
import com.example.model.WorldEventCategory
import com.example.model.WorldGenConfig
import com.example.model.WorldNewsItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object WorldGenerator {
  fun generate(config: WorldGenConfig): WorldState {
    val size = config.sizePreset.size
    val state = WorldState(size, size, config)
    val rng = Random(config.seed)

    val elev = FloatArray(state.size)
    val moist = FloatArray(state.size)

    val waterThreshold = (config.waterPercent.coerceIn(5, 80) / 100f) * 0.78f + 0.10f
    val forestThreshold = 1f - (config.forestPercent.coerceIn(0, 90) / 100f) * 0.68f

    // 1. Fractal Value Noise for continents + moisture
    val seed1 = rng.nextInt(10000)
    val seed2 = rng.nextInt(10000)

    for (y in 0 until size) {
      for (x in 0 until size) {
        val nx = x.toFloat() / size
        val ny = y.toFloat() / size
        val distCenter = hypot(nx - 0.5f, ny - 0.5f) * 1.45f

        val n1 = fbm(nx * 4.5f, ny * 4.5f, seed1)
        val n2 = fbm(nx * 9.0f, ny * 9.0f, seed1 + 71) * 0.35f
        val islandMask = (1f - distCenter * distCenter).coerceIn(0f, 1f)
        val e = (n1 * 0.75f + n2 * 0.25f) * (0.45f + 0.65f * islandMask)

        val m = fbm(nx * 5.5f, ny * 5.5f, seed2)
        val i = state.idx(x, y)
        elev[i] = e
        moist[i] = m
        state.variationGrid[i] = rng.nextInt(0, 16).toByte()

        val terrainType =
          when {
            e < waterThreshold - 0.12f -> TerrainType.DEEP_WATER
            e < waterThreshold -> TerrainType.WATER
            e < waterThreshold + 0.045f -> TerrainType.SAND
            e > 0.74f -> TerrainType.MOUNTAIN
            m > forestThreshold && e > waterThreshold + 0.06f -> TerrainType.FOREST
            else -> TerrainType.GRASS
          }
        state.terrain[i] = terrainType.ordinal
      }
    }

    // 2. Carve meandering rivers from mountain/highland peaks toward sea
    val riverSources = mutableListOf<Pair<Int, Int>>()
    for (attempt in 0 until 220) {
      if (riverSources.size >= 3) break
      val rx = rng.nextInt(8, size - 8)
      val ry = rng.nextInt(8, size - 8)
      val e = elev[state.idx(rx, ry)]
      if (e > 0.62f && state.getTerrain(rx, ry) != TerrainType.WATER) {
        riverSources.add(rx to ry)
      }
    }
    for ((sx, sy) in riverSources) {
      carveRiver(state, elev, sx, sy, rng)
    }

    // 3. Place initial Civilizations & Cities
    val usedNationNames = mutableSetOf<String>()
    val usedCityNames = mutableSetOf<String>()
    val placedCenters = mutableListOf<Pair<Int, Int>>()
    val minCityDist = (size / (config.cityCount.coerceAtLeast(1) + 1.5f)).coerceIn(10f, 26f)

    for (cIdx in 0 until config.cityCount) {
      var bestX = -1
      var bestY = -1
      var bestScore = -1f
      for (tryIdx in 0 until 260) {
        val cx = rng.nextInt(8, size - 8)
        val cy = rng.nextInt(8, size - 8)
        val t = state.getTerrain(cx, cy)
        if (!t.isBuildable || t == TerrainType.MOUNTAIN || t == TerrainType.SAND) continue
        val tooClose = placedCenters.any { hypot((it.first - cx).toFloat(), (it.second - cy).toFloat()) < minCityDist }
        if (tooClose) continue

        var landNeighbors = 0
        var waterBonus = 0
        for (dy in -3..3) {
          for (dx in -3..3) {
            val nt = state.getTerrain(cx + dx, cy + dy)
            if (nt.isBuildable) landNeighbors++
            if (nt == TerrainType.RIVER || nt == TerrainType.WATER) waterBonus++
          }
        }
        val score = landNeighbors + waterBonus * 0.5f
        if (score > bestScore) {
          bestScore = score
          bestX = cx
          bestY = cy
        }
      }

      if (bestX >= 0) {
        placedCenters.add(bestX to bestY)
        val color = NameGenerator.nationColors[cIdx % NameGenerator.nationColors.size]
        val nationId = state.allocId()
        val nationName = NameGenerator.randomNationName(rng, usedNationNames)
        usedNationNames.add(nationName)
        val cityId = state.allocId()
        val cityName = NameGenerator.randomCityName(rng, usedCityNames)
        usedCityNames.add(cityName)

        val nation =
          Nation(
            id = nationId,
            name = nationName,
            colorArgb = color,
            flag = NameGenerator.randomFlag(color, rng),
            capitalCityId = cityId,
            foundedYear = state.year,
          )
        state.nations.add(nation)

        val city =
          City(
            id = cityId,
            name = cityName,
            nationId = nationId,
            centerX = bestX,
            centerY = bestY,
            isCapital = true,
            foundedYear = state.year,
          )
        state.cities.add(city)

        establishCityInfrastructure(state, city, nation, config.popDensity.multiplier, rng)
      }
    }

    // 4. Initialize Diplomacy between nations
    for (i in state.nations.indices) {
      for (j in i + 1 until state.nations.size) {
        val a = state.nations[i]
        val b = state.nations[j]
        val rel =
          when (rng.nextInt(10)) {
            0, 1 -> RelationType.TRADE
            2 -> RelationType.ALLIANCE
            3 -> RelationType.TENSION
            else -> RelationType.NEUTRAL
          }
        a.relations[b.id] = rel
        b.relations[a.id] = rel
      }
    }

    // 5. Connect cities into a coherent road network (with nearest-neighbor fallback)
    val connectedPairs = HashSet<Long>()
    for (i in state.cities.indices) {
      val c1 = state.cities[i]
      val sortedNeighbors =
        state.cities.indices
          .filter { it != i }
          .sortedBy { j ->
            val c2 = state.cities[j]
            hypot((c1.centerX - c2.centerX).toFloat(), (c1.centerY - c2.centerY).toFloat())
          }
      var links = 0
      for (j in sortedNeighbors) {
        val c2 = state.cities[j]
        val dist = hypot((c1.centerX - c2.centerX).toFloat(), (c1.centerY - c2.centerY).toFloat())
        if (links >= 2 && dist >= size * 0.45f) break
        if (links >= 1 && dist >= size * 0.65f) break
        val key = if (i < j) (i.toLong() shl 16) or j.toLong() else (j.toLong() shl 16) or i.toLong()
        if (connectedPairs.add(key)) {
          buildRoadBetween(state, c1.centerX, c1.centerY, c2.centerX, c2.centerY)
        }
        links++
      }
    }

    // 6. Populate wildlife across forests & plains
    val animalCount = (size * 0.45f).toInt().coerceIn(14, 90)
    repeat(animalCount) {
      for (attempt in 0 until 12) {
        val ax = rng.nextInt(2, size - 2)
        val ay = rng.nextInt(2, size - 2)
        val t = state.getTerrain(ax, ay)
        if (t == TerrainType.FOREST || t == TerrainType.GRASS) {
          val type =
            when {
              t == TerrainType.FOREST && rng.nextFloat() < 0.45f -> AnimalType.DEER
              rng.nextFloat() < 0.25f -> AnimalType.WOLF
              else -> AnimalType.BIRD
            }
          state.animals.add(
            Animal(
              id = state.allocId(),
              x = ax + 0.5f,
              y = ay + 0.5f,
              vx = (rng.nextFloat() - 0.5f) * 0.4f,
              vy = (rng.nextFloat() - 0.5f) * 0.4f,
              type = type,
            )
          )
          break
        }
      }
    }

    state.newsLog.add(
      WorldNewsItem(
        id = state.allocId(),
        year = state.year,
        month = state.month,
        category = WorldEventCategory.CITY,
        title = "Сотворение мира",
        description =
          "Сформирован новый мир (${size}×${size}), основано городов: ${state.cities.size}, государств: ${state.nations.size}.",
        x = size / 2,
        y = size / 2,
        nationColorArgb = 0xFF38BDF8.toInt(),
      )
    )

    state.recordHistorySnapshot()
    return state
  }

  fun establishCityInfrastructure(
    state: WorldState,
    city: City,
    nation: Nation?,
    densityMultiplier: Float,
    rng: Random,
    radius: Int = 5,
  ) {
    val cx = city.centerX
    val cy = city.centerY

    // Claim initial circular territory
    val claimR = radius + 2
    for (dy in -claimR..claimR) {
      for (dx in -claimR..claimR) {
        if (dx * dx + dy * dy <= claimR * claimR) {
          val tx = cx + dx
          val ty = cy + dy
          if (state.inBounds(tx, ty)) {
            val t = state.getTerrain(tx, ty)
            if (!t.isWater && state.getOwner(tx, ty) == 0 && nation != null) {
              state.setOwner(tx, ty, nation.id)
            }
            if (dx * dx + dy * dy <= 4 && (t == TerrainType.GRASS || t == TerrainType.FOREST || t == TerrainType.SAND)) {
              state.setTerrain(tx, ty, TerrainType.CITY_ZONE)
            }
          }
        }
      }
    }

    // Place Capitol at center
    if (state.buildings.none { it.x == cx && it.y == cy }) {
      state.buildings.add(
        Building(
          id = state.allocId(),
          x = cx,
          y = cy,
          type = BuildingType.CAPITOL,
          cityId = city.id,
          nationId = nation?.id ?: 0,
          hp = BuildingType.CAPITOL.maxHp,
          maxHp = BuildingType.CAPITOL.maxHp,
        )
      )
    }

    // Place surrounding houses, farm, factory, radar, shelter, military base
    val buildingPlan =
      listOf(
        Pair(-2, 0) to BuildingType.HOUSE,
        Pair(2, 0) to BuildingType.HOUSE,
        Pair(0, -2) to BuildingType.HOUSE,
        Pair(0, 2) to BuildingType.SHELTER,
        Pair(-2, -2) to BuildingType.FARM,
        Pair(2, -2) to BuildingType.FACTORY,
        Pair(2, 2) to BuildingType.RADAR,
        Pair(-2, 2) to BuildingType.POWER_PLANT,
        Pair(3, 0) to BuildingType.MILITARY_BASE,
      )
    val workSpots = mutableListOf<Pair<Int, Int>>()
    val homeSpots = mutableListOf<Pair<Int, Int>>()

    for ((offset, bType) in buildingPlan) {
      val bx = cx + offset.first
      val by = cy + offset.second
      if (!state.inBounds(bx, by)) continue
      val t = state.getTerrain(bx, by)
      if (t.isWater || t == TerrainType.MOUNTAIN) continue
      if (state.buildings.any { it.x == bx && it.y == by }) continue

      if (bType != BuildingType.FARM) {
        state.setTerrain(bx, by, TerrainType.CITY_ZONE)
      } else {
        state.setTerrain(bx, by, TerrainType.GRASS)
      }
      state.buildings.add(
        Building(
          id = state.allocId(),
          x = bx,
          y = by,
          type = bType,
          cityId = city.id,
          nationId = nation?.id ?: 0,
          hp = bType.maxHp,
          maxHp = bType.maxHp,
        )
      )
      if (bType == BuildingType.HOUSE || bType == BuildingType.SHELTER) {
        homeSpots.add(bx to by)
      } else {
        workSpots.add(bx to by)
      }
    }

    if (homeSpots.isEmpty()) homeSpots.add(cx to cy)
    if (workSpots.isEmpty()) workSpots.add(cx to cy)

    // Spawn initial citizens
    val citizenCount = (10 * densityMultiplier).toInt().coerceIn(5, 24)
    val professions = Profession.entries
    repeat(citizenCount) { idx ->
      val home = homeSpots[idx % homeSpots.size]
      val work = workSpots[idx % workSpots.size]
      val prof = professions[idx % professions.size]
      val sx = (cx + rng.nextInt(-2, 3)).coerceIn(1, state.width - 2)
      val sy = (cy + rng.nextInt(-2, 3)).coerceIn(1, state.height - 2)
      state.citizens.add(
        Citizen(
          id = state.allocId(),
          name = NameGenerator.randomCitizenName(rng),
          x = sx + 0.5f,
          y = sy + 0.5f,
          targetX = work.first + 0.5f,
          targetY = work.second + 0.5f,
          age = rng.nextInt(18, 48),
          hp = 100,
          nationId = nation?.id ?: 0,
          cityId = city.id,
          familyId = city.id * 100 + (idx / 2),
          profession = prof,
          state = CitizenState.WORKING,
          homeX = home.first,
          homeY = home.second,
          workX = work.first,
          workY = work.second,
          animPhase = rng.nextFloat() * 6.28f,
        )
      )
    }

    // Spawn initial garrison military units for the city
    if (nation != null) {
      val unitTypes = listOf(com.example.model.UnitType.INFANTRY, com.example.model.UnitType.ARMOR)
      unitTypes.forEachIndexed { uIdx, uType ->
        val ux = (cx + rng.nextInt(-3, 4)).coerceIn(1, state.width - 2)
        val uy = (cy + rng.nextInt(-3, 4)).coerceIn(1, state.height - 2)
        state.militaryUnits.add(
          com.example.model.MilitaryUnit(
            id = state.allocId(),
            nationId = nation.id,
            cityId = city.id,
            type = uType,
            x = ux + 0.5f,
            y = uy + 0.5f,
            targetX = (cx + (uIdx * 2 - 1) * 3).coerceIn(1, state.width - 2) + 0.5f,
            targetY = cy + 0.5f,
            hp = uType.maxHp,
          )
        )
      }
    }

    // Connect internal city street loop and radial avenues out to inter-city road junctions
    val streetOffsets =
      listOf(
        // Inner 3x3 ring road around the Capitol
        -1 to 0, 1 to 0, 0 to -1, 0 to 1,
        -1 to -1, 1 to -1, -1 to 1, 1 to 1,
        // Continuous radial avenues to city outskirts (avoiding building tiles)
        -1 to -2, -1 to -3, 0 to -3,
        1 to 2, 1 to 3, 0 to 3,
        -2 to 1, -3 to 1, -3 to 0,
        2 to -1, 3 to -1, 4 to -1, 4 to 0,
      )
    val roadSpawnTiles = mutableListOf<Pair<Int, Int>>()
    for ((dx, dy) in streetOffsets) {
      val sx = cx + dx
      val sy = cy + dy
      if (!state.inBounds(sx, sy)) continue
      if (state.buildings.any { it.x == sx && it.y == sy }) continue
      val t = state.getTerrain(sx, sy)
      if (t == TerrainType.GRASS || t == TerrainType.FOREST || t == TerrainType.SAND || t == TerrainType.CITY_ZONE || t == TerrainType.ROAD) {
        state.setTerrain(sx, sy, TerrainType.ROAD)
        roadSpawnTiles.add(sx to sy)
      }
    }

    // Spawn 2-3 vehicles directly onto city road tiles so traffic immediately rides on roads
    val vehicleTypes = listOf(VehicleType.CAR, VehicleType.TRUCK, VehicleType.PATROL_JEEP, VehicleType.AMBULANCE)
    val vehicleCount = if (densityMultiplier >= 0.9f) 3 else 2
    repeat(vehicleCount) { vIdx ->
      val spawnTile =
        if (roadSpawnTiles.isNotEmpty()) roadSpawnTiles[(vIdx * 3) % roadSpawnTiles.size] else (cx to cy)
      val adjRoad =
        listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
          .map { (dx, dy) -> (spawnTile.first + dx) to (spawnTile.second + dy) }
          .firstOrNull { (nx, ny) -> state.inBounds(nx, ny) && state.getTerrain(nx, ny) == TerrainType.ROAD }
          ?: spawnTile
      state.vehicles.add(
        Vehicle(
          id = state.allocId(),
          x = spawnTile.first + 0.5f,
          y = spawnTile.second + 0.5f,
          targetX = adjRoad.first + 0.5f,
          targetY = adjRoad.second + 0.5f,
          type = vehicleTypes[(vIdx + city.id) % vehicleTypes.size],
          nationId = nation?.id ?: 0,
          cityId = city.id,
          prevTileX = spawnTile.first,
          prevTileY = spawnTile.second,
        )
      )
    }
  }

  fun buildRoadBetween(state: WorldState, x0: Int, y0: Int, x1: Int, y1: Int) {
    if (!state.inBounds(x0, y0) || !state.inBounds(x1, y1)) return

    // Bounded A* road pathfinder that avoids mountains/deep water, bridges rivers, and merges into existing roads
    val margin = 16
    val minX = (minOf(x0, x1) - margin).coerceIn(1, state.width - 2)
    val maxX = (maxOf(x0, x1) + margin).coerceIn(1, state.width - 2)
    val minY = (minOf(y0, y1) - margin).coerceIn(1, state.height - 2)
    val maxY = (maxOf(y0, y1) + margin).coerceIn(1, state.height - 2)
    val boxW = maxX - minX + 1
    val boxH = maxY - minY + 1

    if (boxW * boxH <= 16_384) {
      val boxSize = boxW * boxH
      val gScore = IntArray(boxSize) { Int.MAX_VALUE }
      val parent = IntArray(boxSize) { -1 }
      val closed = BooleanArray(boxSize)
      val open = java.util.PriorityQueue<Long>(compareBy { it ushr 32 })

      val startLocal = (y0 - minY) * boxW + (x0 - minX)
      val goalLocal = (y1 - minY) * boxW + (x1 - minX)
      gScore[startLocal] = 0
      open.add(((abs(x1 - x0) + abs(y1 - y0)).toLong() * 2L shl 32) or startLocal.toLong())

      val dirs = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)
      var found = false
      var iter = 0
      while (open.isNotEmpty() && iter < boxSize * 3) {
        iter++
        val entry = open.poll() ?: break
        val cur = (entry and 0xFFFFFFFFL).toInt()
        if (cur == goalLocal) {
          found = true
          break
        }
        if (closed[cur]) continue
        closed[cur] = true

        val lx = cur % boxW
        val ly = cur / boxW
        val wx = minX + lx
        val wy = minY + ly

        for (d in 0 until 4) {
          val nlx = lx + dirs[d * 2]
          val nly = ly + dirs[d * 2 + 1]
          if (nlx !in 0 until boxW || nly !in 0 until boxH) continue
          val nxt = nly * boxW + nlx
          if (closed[nxt]) continue

          val nwx = minX + nlx
          val nwy = minY + nly
          val t = state.getTerrain(nwx, nwy)
          val stepCost =
            when (t) {
              TerrainType.ROAD, TerrainType.CITY_ZONE -> 1
              TerrainType.GRASS, TerrainType.SAND, TerrainType.RUINS -> 3
              TerrainType.FOREST -> 4
              TerrainType.RIVER -> 7
              TerrainType.WATER -> 45
              TerrainType.DEEP_WATER, TerrainType.MOUNTAIN -> 180
            }
          val tentativeG = gScore[cur] + stepCost
          if (tentativeG < gScore[nxt]) {
            gScore[nxt] = tentativeG
            parent[nxt] = cur
            val h = (abs(x1 - nwx) + abs(y1 - nwy)) * 2
            open.add(((tentativeG + h).toLong() shl 32) or nxt.toLong())
          }
        }
      }

      if (found) {
        var cur = goalLocal
        while (cur != -1) {
          val wx = minX + (cur % boxW)
          val wy = minY + (cur / boxW)
          val t = state.getTerrain(wx, wy)
          if (t == TerrainType.GRASS || t == TerrainType.FOREST || t == TerrainType.SAND || t == TerrainType.RIVER || t == TerrainType.RUINS) {
            if (state.buildings.none { it.x == wx && it.y == wy }) {
              state.setTerrain(wx, wy, TerrainType.ROAD)
            }
          }
          if (cur == startLocal) break
          cur = parent[cur]
        }
        return
      }
    }

    // Fallback stepping for very distant points
    var cx = x0
    var cy = y0
    var steps = 0
    val maxSteps = (abs(x1 - x0) + abs(y1 - y0)) * 3 + 20
    while ((cx != x1 || cy != y1) && steps < maxSteps) {
      steps++
      val t = state.getTerrain(cx, cy)
      if (t == TerrainType.GRASS || t == TerrainType.FOREST || t == TerrainType.SAND || t == TerrainType.RIVER) {
        if (state.buildings.none { it.x == cx && it.y == cy }) {
          state.setTerrain(cx, cy, TerrainType.ROAD)
        }
      }
      val dx = x1 - cx
      val dy = y1 - cy
      if (abs(dx) >= abs(dy)) {
        cx += if (dx > 0) 1 else -1
      } else {
        cy += if (dy > 0) 1 else -1
      }
    }
  }

  private fun carveRiver(state: WorldState, elev: FloatArray, startX: Int, startY: Int, rng: Random) {
    var x = startX
    var y = startY
    val visited = HashSet<Int>()
    for (step in 0 until state.width * 2) {
      if (!state.inBounds(x, y)) break
      val t = state.getTerrain(x, y)
      if (t == TerrainType.WATER || t == TerrainType.DEEP_WATER) break
      state.setTerrain(x, y, TerrainType.RIVER)
      visited.add(state.idx(x, y))

      var bestNx = x
      var bestNy = y
      var lowestElev = Float.MAX_VALUE
      val dirs = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
      for ((dx, dy) in dirs) {
        val nx = x + dx
        val ny = y + dy
        if (!state.inBounds(nx, ny)) continue
        val nIdx = state.idx(nx, ny)
        if (nIdx in visited) continue
        val e = elev[nIdx] + rng.nextFloat() * 0.03f
        if (e < lowestElev) {
          lowestElev = e
          bestNx = nx
          bestNy = ny
        }
      }
      if (bestNx == x && bestNy == y) break
      x = bestNx
      y = bestNy
    }
  }

  private fun fbm(x: Float, y: Float, seed: Int): Float {
    var sum = 0f
    var amp = 0.5f
    var freq = 1f
    var norm = 0f
    repeat(4) {
      sum += valueNoise(x * freq, y * freq, seed + it * 31) * amp
      norm += amp
      amp *= 0.5f
      freq *= 2.05f
    }
    return sum / norm
  }

  private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3f - 2f * xf)
    val v = yf * yf * (3f - 2f * yf)

    val n00 = hash2d(xi, yi, seed)
    val n10 = hash2d(xi + 1, yi, seed)
    val n01 = hash2d(xi, yi + 1, seed)
    val n11 = hash2d(xi + 1, yi + 1, seed)

    val nx0 = n00 + u * (n10 - n00)
    val nx1 = n01 + u * (n11 - n01)
    return nx0 + v * (nx1 - nx0)
  }

  private fun hash2d(x: Int, y: Int, seed: Int): Float {
    var n = x * 374761393 + y * 668265263 + seed * 1442695041
    n = (n xor (n shr 13)) * 1274126177
    return ((n xor (n shr 16)) and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
  }
}
