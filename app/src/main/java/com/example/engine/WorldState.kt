package com.example.engine

import com.example.model.AirObject
import com.example.model.Animal
import com.example.model.Building
import com.example.model.BuildingType
import com.example.model.Citizen
import com.example.model.City
import com.example.model.MilitaryUnit
import com.example.model.Nation
import com.example.model.TerrainType
import com.example.model.Vehicle
import com.example.model.WarConflict
import com.example.model.WeatherType
import com.example.model.WorldGenConfig
import com.example.model.WorldNewsItem
import com.example.model.WorldParticle

class WorldState(
  val width: Int,
  val height: Int,
  val config: WorldGenConfig,
) {
  val size: Int = width * height
  val terrain: IntArray = IntArray(size) { TerrainType.GRASS.ordinal }
  val nationOwner: IntArray = IntArray(size) { 0 }
  val fireGrid: IntArray = IntArray(size) { 0 }
  val radiationGrid: IntArray = IntArray(size) { 0 }
  val variationGrid: ByteArray = ByteArray(size) { 0 }
  val historyFrames: MutableList<Pair<Int, IntArray>> = mutableListOf()

  val nations: MutableList<Nation> = mutableListOf()
  val cities: MutableList<City> = mutableListOf()
  val buildings: MutableList<Building> = mutableListOf()
  val citizens: MutableList<Citizen> = mutableListOf()
  val vehicles: MutableList<Vehicle> = mutableListOf()
  val militaryUnits: MutableList<MilitaryUnit> = mutableListOf()
  val animals: MutableList<Animal> = mutableListOf()
  val airObjects: MutableList<AirObject> = mutableListOf()
  val particles: MutableList<WorldParticle> = mutableListOf()
  val warConflicts: MutableList<WarConflict> = mutableListOf()
  val newsLog: MutableList<WorldNewsItem> = mutableListOf()

  var year: Int = 2026
  var month: Int = 1
  var tickCount: Long = 0L
  var timeOfDay: Float = 0.35f // 0.0=midnight, 0.25=dawn, 0.5=noon, 0.75=dusk
  var weather: WeatherType = WeatherType.CLEAR
  var weatherTimer: Float = 35f
  var warsEnabled: Boolean = true
  var totalWarsCount: Int = 0
  var totalEventsCount: Int = 0
  var totalDestroyedBuildings: Int = 0
  var totalRebuiltBuildings: Int = 0
  var totalPvoIntercepts: Int = 0
  var nextId: Int = 1
  var worldVersion: Long = 1L
  var terrainDirty: Boolean = true

  fun allocId(): Int = nextId++

  inline fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

  inline fun idx(x: Int, y: Int): Int = y * width + x

  fun getTerrain(x: Int, y: Int): TerrainType {
    if (!inBounds(x, y)) return TerrainType.DEEP_WATER
    return TerrainType.entries[terrain[idx(x, y)].coerceIn(0, TerrainType.entries.lastIndex)]
  }

  fun setTerrain(x: Int, y: Int, type: TerrainType) {
    if (!inBounds(x, y)) return
    val i = idx(x, y)
    if (terrain[i] != type.ordinal) {
      terrain[i] = type.ordinal
      terrainDirty = true
    }
  }

  fun getOwner(x: Int, y: Int): Int {
    if (!inBounds(x, y)) return 0
    return nationOwner[idx(x, y)]
  }

  fun setOwner(x: Int, y: Int, nationId: Int) {
    if (!inBounds(x, y)) return
    val i = idx(x, y)
    if (nationOwner[i] != nationId) {
      nationOwner[i] = nationId
      terrainDirty = true
    }
  }

  fun isNight(): Boolean = timeOfDay < 0.21f || timeOfDay > 0.79f

  fun nightIntensity(): Float {
    return when {
      timeOfDay < 0.18f -> 1f
      timeOfDay < 0.28f -> 1f - (timeOfDay - 0.18f) / 0.10f
      timeOfDay > 0.82f -> 1f
      timeOfDay > 0.72f -> (timeOfDay - 0.72f) / 0.10f
      else -> 0f
    }.coerceIn(0f, 1f)
  }

  fun dawnDuskWarmth(): Float {
    return when {
      timeOfDay in 0.20f..0.32f -> 1f - kotlin.math.abs(timeOfDay - 0.26f) / 0.06f
      timeOfDay in 0.68f..0.80f -> 1f - kotlin.math.abs(timeOfDay - 0.74f) / 0.06f
      else -> 0f
    }.coerceIn(0f, 1f)
  }

  fun recordHistorySnapshot() {
    val grid = IntArray(32 * 32)
    val nationColors = HashMap<Int, Int>(nations.size)
    for (n in nations) {
      nationColors[n.id] = n.colorArgb
    }
    for (sy in 0 until 32) {
      val ty = (sy * height) / 32
      for (sx in 0 until 32) {
        val tx = (sx * width) / 32
        val i = idx(tx, ty)
        val owner = nationOwner[i]
        grid[sy * 32 + sx] =
          if (owner > 0) {
            nationColors[owner] ?: 0xFF64748B.toInt()
          } else {
            val t = terrain[i]
            if (t == TerrainType.DEEP_WATER.ordinal || t == TerrainType.WATER.ordinal) {
              0xFF164E63.toInt()
            } else {
              0xFF2B3B2B.toInt()
            }
          }
      }
    }
    if (historyFrames.size >= 48) {
      historyFrames.removeAt(0)
    }
    historyFrames.add(year to grid)
  }

  fun cityDefenseScore(cityId: Int): Int {
    val radars = buildings.count { it.cityId == cityId && it.type == BuildingType.RADAR }
    val ewStations = buildings.count { it.cityId == cityId && it.type == BuildingType.EW_STATION }
    val shelters = buildings.count { it.cityId == cityId && it.type == BuildingType.SHELTER }
    val bases = buildings.count { it.cityId == cityId && it.type == BuildingType.MILITARY_BASE }
    val garrison = militaryUnits.count { it.cityId == cityId }
    return (radars * 25 + ewStations * 20 + shelters * 15 + bases * 30 + garrison * 10).coerceIn(0, 100)
  }
}
