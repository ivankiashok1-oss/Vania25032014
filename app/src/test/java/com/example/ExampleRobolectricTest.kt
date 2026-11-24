package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SirenSoundEngine
import com.example.data.WorldSerializer
import com.example.engine.ChronicleGenerator
import com.example.engine.ChronicleSnapshot
import com.example.engine.PixelMapRenderer
import com.example.engine.WorldGenerator
import com.example.engine.WorldSimulationEngine
import com.example.model.AirObject
import com.example.model.AirObjectType
import com.example.model.Building
import com.example.model.BuildingType
import com.example.model.Citizen
import com.example.model.CitizenState
import com.example.model.GodTool
import com.example.model.GraphicsQuality
import com.example.model.MapSizePreset
import com.example.model.MilitaryUnit
import com.example.model.Profession
import com.example.model.RelationType
import com.example.model.TerrainType
import com.example.model.UnitOrder
import com.example.model.UnitType
import com.example.model.Vehicle
import com.example.model.VehicleType
import com.example.model.WarConflict
import com.example.model.WeatherType
import com.example.model.WorldGenConfig
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Pixel World", appName)
  }

  @Test
  fun `full update 2_0 end to end simulation war air event pvo and save restore`() {
    // 1. Create world with 0 initial cities, then create 2 nations manually
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 2026L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    // 2. Create two nations
    engine.applyGodTool(world, GodTool.CREATE_NATION, 18, 18, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 52, 52, 2, 0xFFEF4444.toInt(), null, sound)
    assertEquals(2, world.nations.size)
    assertTrue(world.militaryUnits.isNotEmpty())

    // 3. Start a war & spawn additional military units
    engine.applyGodTool(world, GodTool.TRIGGER_WAR_EVENT, 52, 52, 2, 0xFFEF4444.toInt(), null, sound)
    assertTrue(world.warConflicts.any { it.isActive })
    assertEquals(RelationType.WAR, world.nations[0].relations[world.nations[1].id])

    // 4. Create air event (Shahed-like strike drone) + place PVO radar & run ticks for interception
    engine.applyGodTool(world, GodTool.BUILD_PVO_RADAR, 50, 50, 1, 0xFFEF4444.toInt(), world.nations[1].id, sound)
    engine.applyGodTool(world, GodTool.SPAWN_STRIKE_DRONE, 52, 52, 1, 0xFF38BDF8.toInt(), null, sound)
    assertTrue(world.airObjects.any { it.type == AirObjectType.STRIKE_DRONE })

    world.weather = WeatherType.RAIN
    world.timeOfDay = 0.85f
    repeat(15) {
      engine.step(world, 0.08f, 2, sound)
    }

    // 5. Save world to v2 compressed snapshot
    val savedBytes = WorldSerializer.compressState(world)

    // 6-7. Restore world and verify EVERY important field survived intact
    val restored = WorldSerializer.decompressState(savedBytes)
    assertEquals(world.width, restored.width)
    assertEquals(world.year, restored.year)
    assertEquals(world.month, restored.month)
    assertEquals(world.weather, restored.weather)
    assertEquals(world.timeOfDay, restored.timeOfDay, 0.001f)
    assertEquals(world.nations.size, restored.nations.size)
    assertEquals(world.cities.size, restored.cities.size)
    assertEquals(world.buildings.size, restored.buildings.size)
    assertEquals(world.citizens.size, restored.citizens.size)
    assertEquals(world.militaryUnits.size, restored.militaryUnits.size)
    assertEquals(world.warConflicts.size, restored.warConflicts.size)
    assertEquals(world.airObjects.size, restored.airObjects.size)
    assertEquals(world.newsLog.size, restored.newsLog.size)
    assertTrue(restored.warConflicts.any { it.isActive })
    assertEquals(RelationType.WAR, restored.nations[0].relations[restored.nations[1].id])

    // Verify no duplicate IDs after continuing simulation on restored world
    val idSet = restored.citizens.map { it.id } + restored.militaryUnits.map { it.id } + restored.buildings.map { it.id }
    assertEquals(idSet.size, idSet.toSet().size)
    assertTrue(restored.nextId > (idSet.maxOrNull() ?: 0))
  }

  @Test
  fun `v1 legacy save migrates cleanly without crashing`() {
    val root = JSONObject().apply {
      put("w", 72)
      put("h", 72)
      put("year", 2030)
      put("month", 5)
      put("tick", 100L)
      put("tod", 0.5)
      put("weather", 0)
      put("wars", true)
      put("warsCount", 1)
      put("eventsCount", 2)
      put("nextId", 50)
      put("cfg", JSONObject().apply {
        put("sizePreset", 0)
        put("water", 30)
        put("forest", 30)
        put("cities", 2)
        put("pop", 1)
        put("seed", 99L)
      })
      put("terrain", "${TerrainType.GRASS.ordinal}:${72 * 72}")
      put("owners", "0:${72 * 72}")
      put("fires", "0:${72 * 72}")
      put("nations", JSONArray())
      put("cities", JSONArray())
      put("buildings", JSONArray())
      put("citizens", JSONArray())
      put("vehicles", JSONArray())
      put("animals", JSONArray())
      put("news", JSONArray())
    }
    val baos = ByteArrayOutputStream()
    GZIPOutputStream(baos).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }

    val migrated = WorldSerializer.decompressState(baos.toByteArray())
    assertEquals(72, migrated.width)
    assertEquals(2030, migrated.year)
    assertFalse(migrated.cities.any { it.underAirAlert })
  }

  @Test
  fun `giant 256x256 map generates and renders cleanly`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.GIANT, cityCount = 6, seed = 777L)
    val giant = WorldGenerator.generate(cfg)
    assertEquals(256, giant.width)
    assertEquals(256, giant.height)
    assertTrue(giant.cities.isNotEmpty())

    val renderer = PixelMapRenderer()
    val bmp = renderer.renderTerrainBitmap(giant, showTerritoryBorders = true, waterAnimPhase = 1, quality = GraphicsQuality.HIGH)
    assertEquals(512, bmp.width)
    assertEquals(512, bmp.height)
  }

  @Test
  fun `city capture transfers buildings radar citizens vehicles and reassigns defender capital`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 4242L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    // Create attacker (nationA) and defender (nationB)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 16, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 52, 52, 2, 0xFFEF4444.toInt(), null, sound)
    val nationA = world.nations[0]
    val nationB = world.nations[1]
    val capitalB = world.cities.first { it.nationId == nationB.id }
    assertTrue(capitalB.isCapital)
    assertEquals(capitalB.id, nationB.capitalCityId)

    // Give nationB a second city so capital reassignment can be verified
    world.setOwner(24, 54, nationB.id)
    engine.applyGodTool(world, GodTool.FOUND_CITY, 24, 54, 2, nationB.colorArgb, nationB.id, sound)
    val secondCityB = world.cities.first { it.nationId == nationB.id && it.id != capitalB.id }
    assertFalse(secondCityB.isCapital)

    // Ensure capitalB has a RADAR building, citizens, and a vehicle belonging to nationB
    world.buildings.add(
      Building(
        id = world.allocId(),
        x = capitalB.centerX + 1,
        y = capitalB.centerY,
        type = BuildingType.RADAR,
        cityId = capitalB.id,
        nationId = nationB.id,
        hp = BuildingType.RADAR.maxHp,
        maxHp = BuildingType.RADAR.maxHp,
      )
    )
    world.vehicles.add(
      Vehicle(
        id = world.allocId(),
        x = capitalB.centerX + 0.5f,
        y = capitalB.centerY + 0.5f,
        targetX = capitalB.centerX + 0.5f,
        targetY = capitalB.centerY + 0.5f,
        type = VehicleType.PATROL_JEEP,
        nationId = nationB.id,
        cityId = capitalB.id,
      )
    )
    assertTrue(world.citizens.any { it.cityId == capitalB.id && it.nationId == nationB.id })

    // Remove nationB defenders and place nationA unit right on undefended capitalB
    world.militaryUnits.removeAll { it.nationId == nationB.id }
    world.militaryUnits.add(
      MilitaryUnit(
        id = world.allocId(),
        nationId = nationA.id,
        cityId = world.cities.first { it.nationId == nationA.id }.id,
        type = UnitType.ARMOR,
        x = capitalB.centerX + 0.2f,
        y = capitalB.centerY + 0.2f,
        targetX = capitalB.centerX + 0.5f,
        targetY = capitalB.centerY + 0.5f,
        hp = UnitType.ARMOR.maxHp,
      )
    )
    world.warsEnabled = true
    engine.declareWar(world, nationA, nationB)

    var captured = false
    repeat(60) {
      if (!captured) {
        engine.step(world, 0.08f, 2, sound)
        if (capitalB.nationId == nationA.id) {
          captured = true
        }
      }
    }

    assertTrue("Expected capitalB to be captured by nationA", captured)
    assertFalse(capitalB.isCapital)
    assertTrue(world.buildings.filter { it.cityId == capitalB.id }.all { it.nationId == nationA.id })
    assertTrue(world.buildings.any { it.cityId == capitalB.id && it.type == BuildingType.RADAR && it.nationId == nationA.id })
    assertTrue(world.citizens.filter { it.cityId == capitalB.id }.all { it.nationId == nationA.id })
    assertTrue(world.vehicles.filter { it.cityId == capitalB.id }.all { it.nationId == nationA.id })
    assertEquals(secondCityB.id, nationB.capitalCityId)
    assertTrue(secondCityB.isCapital)

    // Verify save/restore preserves captured city state and reassigned capital
    val restored = WorldSerializer.decompressState(WorldSerializer.compressState(world))
    val restoredCapB = restored.cities.first { it.id == capitalB.id }
    val restoredSecondB = restored.cities.first { it.id == secondCityB.id }
    val restoredNationB = restored.nations.first { it.id == nationB.id }
    assertEquals(nationA.id, restoredCapB.nationId)
    assertFalse(restoredCapB.isCapital)
    assertEquals(restoredSecondB.id, restoredNationB.capitalCityId)
    assertTrue(restoredSecondB.isCapital)
    assertTrue(restored.buildings.filter { it.cityId == capitalB.id }.all { it.nationId == nationA.id })
    assertTrue(restored.vehicles.filter { it.cityId == capitalB.id }.all { it.nationId == nationA.id })
    assertTrue(restored.citizens.filter { it.cityId == capitalB.id }.all { it.nationId == nationA.id })
  }

  @Test
  fun `merge nations cleans relations vehicles active conflicts and persists cleanly`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 5151L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 16, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 36, 16, 2, 0xFFEF4444.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 54, 54, 2, 0xFF22C55E.toInt(), null, sound)
    assertEquals(3, world.nations.size)

    val source = world.nations[0]
    val nearestTarget = world.nations[1]
    val thirdNation = world.nations[2]

    world.warsEnabled = true
    engine.declareWar(world, source, nearestTarget)
    source.relations[thirdNation.id] = RelationType.TRADE
    thirdNation.relations[source.id] = RelationType.TRADE

    val sourceCity = world.cities.first { it.nationId == source.id }
    world.vehicles.add(
      Vehicle(
        id = world.allocId(),
        x = sourceCity.centerX + 0.5f,
        y = sourceCity.centerY + 0.5f,
        targetX = sourceCity.centerX + 0.5f,
        targetY = sourceCity.centerY + 0.5f,
        type = VehicleType.TRUCK,
        nationId = source.id,
        cityId = sourceCity.id,
      )
    )

    // Merge source (at 16,16) into nearestTarget
    engine.applyGodTool(world, GodTool.MERGE_NATIONS, 16, 16, 1, source.colorArgb, null, sound)

    assertEquals(2, world.nations.size)
    assertFalse(world.nations.any { it.id == source.id })
    assertTrue(world.nations.none { it.relations.containsKey(source.id) })
    assertTrue(world.vehicles.none { it.nationId == source.id })
    assertTrue(world.vehicles.any { it.cityId == sourceCity.id && it.nationId == nearestTarget.id })
    assertTrue(world.buildings.none { it.nationId == source.id })
    assertTrue(world.citizens.none { it.nationId == source.id })
    assertTrue(world.cities.none { it.nationId == source.id })
    assertTrue(world.warConflicts.filter { it.nationAId == source.id || it.nationBId == source.id }.all { !it.isActive && it.endYear == world.year })

    // Verify save/restore after merge keeps world consistent
    val restored = WorldSerializer.decompressState(WorldSerializer.compressState(world))
    assertEquals(2, restored.nations.size)
    assertTrue(restored.nations.none { it.relations.containsKey(source.id) })
    assertTrue(restored.vehicles.none { it.nationId == source.id })
    assertTrue(restored.warConflicts.filter { it.nationAId == source.id || it.nationBId == source.id }.all { !it.isActive })
    val allIds = restored.citizens.map { it.id } + restored.buildings.map { it.id } + restored.vehicles.map { it.id }
    assertEquals(allIds.size, allIds.toSet().size)
  }

  @Test
  fun `retreating unit falls back to friendly city when its homeCity was captured`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 6060L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 15, 15, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 52, 52, 2, 0xFFEF4444.toInt(), null, sound)
    val nationA = world.nations[0]
    val nationB = world.nations[1]
    val cityB1 = world.cities.first { it.nationId == nationB.id }

    world.setOwner(22, 54, nationB.id)
    engine.applyGodTool(world, GodTool.FOUND_CITY, 22, 54, 2, nationB.colorArgb, nationB.id, sound)
    val cityB2 = world.cities.first { it.nationId == nationB.id && it.id != cityB1.id }

    // Simulate cityB1 already captured by nationA while unit still references cityB1.id
    cityB1.nationId = nationA.id
    val damagedUnit =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nationB.id,
        cityId = cityB1.id,
        type = UnitType.INFANTRY,
        x = 40.5f,
        y = 40.5f,
        targetX = 40.5f,
        targetY = 40.5f,
        hp = (UnitType.INFANTRY.maxHp * 0.15f).toInt(),
      )
    world.militaryUnits.clear()
    world.militaryUnits.add(damagedUnit)

    engine.step(world, 0.05f, 1, sound)

    assertEquals(UnitOrder.RETREAT, damagedUnit.order)
    assertEquals(cityB2.centerX + 0.5f, damagedUnit.targetX, 0.01f)
    assertEquals(cityB2.centerY + 0.5f, damagedUnit.targetY, 0.01f)
  }

  @Test
  fun `split nation transfers buildings and vehicles and reassigns source capital`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 7070L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 20, 20, 2, 0xFF38BDF8.toInt(), null, sound)
    val source = world.nations[0]
    val sourceCapital = world.cities.first { it.nationId == source.id }
    assertTrue(sourceCapital.isCapital)
    assertEquals(sourceCapital.id, source.capitalCityId)

    world.setOwner(52, 52, source.id)
    engine.applyGodTool(world, GodTool.FOUND_CITY, 52, 52, 2, source.colorArgb, source.id, sound)
    val secondCity = world.cities.first { it.nationId == source.id && it.id != sourceCapital.id }
    assertFalse(secondCity.isCapital)

    world.vehicles.add(
      Vehicle(
        id = world.allocId(),
        x = sourceCapital.centerX + 0.5f,
        y = sourceCapital.centerY + 0.5f,
        targetX = sourceCapital.centerX + 0.5f,
        targetY = sourceCapital.centerY + 0.5f,
        type = VehicleType.CAR,
        nationId = source.id,
        cityId = sourceCapital.id,
      )
    )

    // Split right at sourceCapital so the capital secedes
    engine.applyGodTool(world, GodTool.SPLIT_NATION, 20, 20, 2, 0xFF22C55E.toInt(), null, sound)

    assertEquals(2, world.nations.size)
    val rebel = world.nations.first { it.id != source.id }
    assertEquals(rebel.id, sourceCapital.nationId)
    assertEquals(sourceCapital.id, rebel.capitalCityId)
    assertTrue(world.buildings.filter { it.cityId == sourceCapital.id }.all { it.nationId == rebel.id })
    assertTrue(world.vehicles.filter { it.cityId == sourceCapital.id }.all { it.nationId == rebel.id })
    assertEquals(secondCity.id, source.capitalCityId)
    assertTrue(secondCity.isCapital)
  }

  @Test
  fun `military unit sets isOnWater during naval crossing and persists via serializer`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 8080L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 25, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 56, 25, 2, 0xFFEF4444.toInt(), null, sound)
    val nationA = world.nations[0]
    val nationB = world.nations[1]

    // Carve water channel between x=28..42, y=18..32 and land at x=45..60
    for (y in 18..32) {
      for (x in 28..42) {
        world.setTerrain(x, y, TerrainType.WATER)
      }
      for (x in 43..60) {
        if (world.getTerrain(x, y).isWater) world.setTerrain(x, y, TerrainType.GRASS)
      }
    }

    world.warsEnabled = true
    engine.declareWar(world, nationA, nationB)

    val navalUnit =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nationA.id,
        cityId = world.cities.first { it.nationId == nationA.id }.id,
        type = UnitType.INFANTRY,
        x = 34.5f,
        y = 25.5f,
        targetX = 56.5f,
        targetY = 25.5f,
        hp = UnitType.INFANTRY.maxHp,
      )
    world.militaryUnits.clear()
    world.militaryUnits.add(navalUnit)

    engine.step(world, 0.05f, 1, sound)
    assertTrue("Unit on water should set isOnWater=true", navalUnit.isOnWater)

    val restored = WorldSerializer.decompressState(WorldSerializer.compressState(world))
    val restoredUnit = restored.militaryUnits.first { it.id == navalUnit.id }
    assertTrue("Restored unit should preserve isOnWater=true", restoredUnit.isOnWater)

    // Move unit onto land and step again -> isOnWater should become false
    navalUnit.x = 46.5f
    navalUnit.y = 25.5f
    engine.step(world, 0.05f, 1, sound)
    assertFalse("Unit on land should clear isOnWater=false", navalUnit.isOnWater)
  }

  @Test
  fun `chronicle generator returns meaningful offline summary without api key`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 9090L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 16, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 52, 52, 2, 0xFFEF4444.toInt(), null, sound)
    val a = world.nations[0]
    val b = world.nations[1]
    a.territoriesCaptured = 42
    world.warsEnabled = true
    engine.declareWar(world, a, b)
    world.warConflicts.first().casualtiesA = 15
    world.totalPvoIntercepts = 7

    val snapshot =
      ChronicleSnapshot(
        year = 2034,
        month = 8,
        weather = WeatherType.STORM,
        population = world.citizens.size,
        nations = world.nations,
        cities = world.cities,
        warConflicts = world.warConflicts,
        totalPvoIntercepts = world.totalPvoIntercepts,
      )

    val summary = runBlocking { ChronicleGenerator.generateChronicle(snapshot, apiKeyOverride = "") }
    assertTrue(summary.isNotBlank())
    assertTrue(summary.contains("2034.08"))
    assertTrue(summary.contains(a.name))
    assertTrue(summary.contains("7"))
    assertTrue(summary.contains("15"))
  }

  @Test
  fun `unit retreating from water swims back to shore instead of getting stuck`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 4001L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    // Ensure flat land at (18, 25) and water at x=25..34, y=20..30
    for (y in 20..30) {
      for (x in 14..24) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
      for (x in 25..34) {
        world.setTerrain(x, y, TerrainType.WATER)
      }
    }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 18, 25, 2, 0xFF38BDF8.toInt(), null, sound)
    val nation = world.nations[0]
    val homeCity = world.cities.first { it.nationId == nation.id }

    val damagedInWater =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nation.id,
        cityId = homeCity.id,
        type = UnitType.INFANTRY,
        x = 28.5f,
        y = 25.5f,
        targetX = 28.5f,
        targetY = 25.5f,
        hp = (UnitType.INFANTRY.maxHp * 0.2f).toInt(),
      )
    world.militaryUnits.clear()
    world.militaryUnits.add(damagedInWater)

    val startX = damagedInWater.x
    repeat(35) {
      engine.step(world, 0.08f, 1, sound)
    }

    assertEquals(UnitOrder.RETREAT, damagedInWater.order)
    assertTrue("Retreating unit in water should move toward shore/homeCity", damagedInWater.x < startX - 2.0f)
    assertFalse("Unit should have reached land and cleared isOnWater", damagedInWater.isOnWater)
  }

  @Test
  fun `explosion damages and destroys military units within blast radius`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 4002L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    for (y in 20..30) {
      for (x in 20..30) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    engine.applyGodTool(world, GodTool.CREATE_NATION, 25, 25, 2, 0xFF38BDF8.toInt(), null, sound)
    val nation = world.nations[0]
    val city = world.cities.first { it.nationId == nation.id }

    world.militaryUnits.clear()
    val infantryNear =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nation.id,
        cityId = city.id,
        type = UnitType.INFANTRY,
        x = 26.0f,
        y = 25.0f,
        targetX = 26.0f,
        targetY = 25.0f,
        hp = 60,
      )
    val armorNear =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nation.id,
        cityId = city.id,
        type = UnitType.ARMOR,
        x = 25.5f,
        y = 26.0f,
        targetX = 25.5f,
        targetY = 26.0f,
        hp = UnitType.ARMOR.maxHp,
      )
    world.militaryUnits.addAll(listOf(infantryNear, armorNear))

    engine.applyGodTool(world, GodTool.TRIGGER_EXPLOSION, 25, 25, 2, 0xFF38BDF8.toInt(), null, sound)

    assertFalse("Infantry with 60 HP should be destroyed by 65 blast damage", world.militaryUnits.any { it.id == infantryNear.id })
    assertTrue("Armor should survive single blast with reduced HP", world.militaryUnits.any { it.id == armorNear.id })
    assertEquals(UnitType.ARMOR.maxHp - 40, armorNear.hp)
    assertTrue(nation.losses >= 1)
    assertTrue(nation.totalCasualties >= 1)
  }

  @Test
  fun `nation collapse clears relations and resets nationId on orphaned entities`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 4003L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 16, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 52, 52, 2, 0xFFEF4444.toInt(), null, sound)
    val doomed = world.nations[0]
    val survivor = world.nations[1]

    world.warsEnabled = true
    engine.declareWar(world, doomed, survivor)

    val orphanAir =
      AirObject(
        id = world.allocId(),
        x = 20f,
        y = 20f,
        startX = 20f,
        startY = 20f,
        targetX = 52f,
        targetY = 52f,
        speed = 0.05f,
        type = AirObjectType.RECON_DRONE,
        nationId = doomed.id,
      )
    world.airObjects.add(orphanAir)

    // Remove all cities of doomed nation so collapse triggers on next nation update tick
    val doomedCityIds = world.cities.filter { it.nationId == doomed.id }.map { it.id }.toSet()
    world.cities.removeAll { it.nationId == doomed.id }
    assertTrue(world.buildings.any { it.nationId == doomed.id })
    assertTrue(world.citizens.any { it.nationId == doomed.id })
    assertTrue(world.vehicles.any { it.nationId == doomed.id })

    // Advance enough ticks to trigger updateNationsAndCities (tickCount % 18 == 0)
    world.tickCount = 17L
    engine.step(world, 0.05f, 1, sound)

    assertFalse("Doomed nation should be removed", world.nations.any { it.id == doomed.id })
    assertFalse("Survivor relations must no longer contain collapsed nation", survivor.relations.containsKey(doomed.id))
    assertTrue("No buildings should reference collapsed nationId", world.buildings.none { it.nationId == doomed.id })
    assertTrue("No citizens should reference collapsed nationId", world.citizens.none { it.nationId == doomed.id })
    assertTrue("No vehicles should reference collapsed nationId", world.vehicles.none { it.nationId == doomed.id })
    assertEquals("Orphaned air object should reset nationId to 0", 0, orphanAir.nationId)
    assertTrue(doomedCityIds.isNotEmpty())
  }

  @Test
  fun `ground citizen and unit skirt around single mountain tile instead of stepping onto it or getting stuck`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 4004L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    for (y in 20..30) {
      for (x in 15..35) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    // Single mountain tile directly on straight path between x=20 and x=26 at y=25
    world.setTerrain(23, 25, TerrainType.MOUNTAIN)

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 25, 2, 0xFF38BDF8.toInt(), null, sound)
    val nation = world.nations[0]
    val city = world.cities.first { it.nationId == nation.id }

    world.citizens.clear()
    val citizen =
      Citizen(
        id = world.allocId(),
        name = "Тест",
        x = 21.5f,
        y = 25.5f,
        targetX = 32.5f,
        targetY = 25.5f,
        age = 25,
        hp = 100,
        nationId = nation.id,
        cityId = city.id,
        familyId = 1,
        profession = Profession.TRADER,
        state = CitizenState.TRAVELING,
        homeX = 21,
        homeY = 25,
        workX = 32,
        workY = 25,
      )
    world.citizens.add(citizen)

    world.militaryUnits.clear()
    val unit =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nation.id,
        cityId = city.id,
        type = UnitType.INFANTRY,
        x = 21.5f,
        y = 25.5f,
        targetX = 32.5f,
        targetY = 25.5f,
        hp = UnitType.INFANTRY.maxHp,
      )
    world.militaryUnits.add(unit)

    // Keep unit in ADVANCE/custom target by giving enemy city at (32, 25)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 32, 25, 2, 0xFFEF4444.toInt(), null, sound)
    val enemyNation = world.nations[1]
    world.militaryUnits.removeAll { it.nationId == enemyNation.id }
    world.warsEnabled = true
    engine.declareWar(world, nation, enemyNation)
    // Restore mountain in case city infra cleared nearby tiles
    world.setTerrain(23, 25, TerrainType.MOUNTAIN)

    repeat(40) {
      engine.step(world, 0.08f, 1, sound)
      assertFalse("Citizen must not step onto mountain", world.getTerrain(citizen.x.toInt(), citizen.y.toInt()) == TerrainType.MOUNTAIN)
      assertFalse("Military unit must not step onto mountain", world.getTerrain(unit.x.toInt(), unit.y.toInt()) == TerrainType.MOUNTAIN)
    }

    assertTrue("Citizen should detour past x=24 around mountain", citizen.x > 24.0f)
    assertTrue("Unit should detour past x=24 around mountain", unit.x > 24.0f)
  }

  @Test
  fun `nuke strike creates radiation cleared by restore and persisted in saves`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 5001L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    for (y in 20..38) {
      for (x in 20..38) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }

    engine.applyGodTool(world, GodTool.NUKE_STRIKE, 30, 30, 2, 0xFF38BDF8.toInt(), null, sound)
    val centerRad = world.radiationGrid[world.idx(30, 30)]
    assertTrue("Nuke strike should set high radiation at impact center", centerRad >= 80)

    val restored = WorldSerializer.decompressState(WorldSerializer.compressState(world))
    assertEquals(centerRad, restored.radiationGrid[restored.idx(30, 30)])

    engine.applyGodTool(world, GodTool.RESTORE_AREA, 30, 30, 9, 0xFF38BDF8.toInt(), null, sound)
    assertEquals("RESTORE_AREA should clear radiation at center", 0, world.radiationGrid[world.idx(30, 30)])
  }

  @Test
  fun `emp strike downs drones disables armor movement and triggers city blackout`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 5002L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    for (y in 20..35) {
      for (x in 20..35) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    engine.applyGodTool(world, GodTool.CREATE_NATION, 25, 25, 2, 0xFF38BDF8.toInt(), null, sound)
    val nation = world.nations[0]
    val city = world.cities.first { it.nationId == nation.id }
    val buildingCountBefore = world.buildings.size

    val armor =
      MilitaryUnit(
        id = world.allocId(),
        nationId = nation.id,
        cityId = city.id,
        type = UnitType.ARMOR,
        x = 27.5f,
        y = 25.5f,
        targetX = 33.5f,
        targetY = 25.5f,
        hp = UnitType.ARMOR.maxHp,
      )
    world.militaryUnits.clear()
    world.militaryUnits.add(armor)

    val drone =
      AirObject(
        id = world.allocId(),
        x = 28f,
        y = 26f,
        startX = 10f,
        startY = 26f,
        targetX = 40f,
        targetY = 26f,
        speed = 0.3f,
        type = AirObjectType.STRIKE_DRONE,
        nationId = 999,
      )
    world.airObjects.add(drone)

    engine.applyGodTool(world, GodTool.EMP_STRIKE, 26, 25, 2, nation.colorArgb, null, sound)

    assertFalse("EMP strike should destroy drones within radius", world.airObjects.any { it.id == drone.id })
    assertTrue("EMP strike should disable armor", armor.empDisabledTimer > 0f)
    assertTrue("EMP strike should cause city blackout", city.blackoutTimer > 0f)
    assertEquals("EMP strike should not destroy buildings", buildingCountBefore, world.buildings.size)

    val startX = armor.x
    engine.step(world, 0.08f, 1, sound)
    assertEquals("EMP-disabled armor must not move", startX, armor.x, 0.001f)
  }

  @Test
  fun `ew station jams nearby hostile drone when city is not in blackout`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 5003L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 25, 25, 2, 0xFF38BDF8.toInt(), null, sound)
    val nation = world.nations[0]
    val city = world.cities.first { it.nationId == nation.id }
    city.blackoutTimer = 0f

    engine.applyGodTool(world, GodTool.BUILD_EW_STATION, 26, 25, 1, nation.colorArgb, nation.id, sound)
    assertTrue(world.buildings.any { it.type == BuildingType.EW_STATION && it.nationId == nation.id })

    val hostileDrone =
      AirObject(
        id = world.allocId(),
        x = 30f,
        y = 25f,
        startX = 50f,
        startY = 25f,
        targetX = 25f,
        targetY = 25f,
        speed = 0.25f,
        type = AirObjectType.STRIKE_DRONE,
        nationId = nation.id + 100,
      )
    world.airObjects.add(hostileDrone)

    engine.step(world, 0.05f, 1, sound)

    assertFalse("EW station should suppress drone within 15 tiles", world.airObjects.any { it.id == hostileDrone.id })
    assertTrue(world.totalPvoIntercepts >= 1)
  }

  @Test
  fun `alliance triggers collective defense war when ally is attacked`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 5004L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 16, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 36, 16, 2, 0xFFEF4444.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.CREATE_NATION, 52, 52, 2, 0xFF22C55E.toInt(), null, sound)
    val aggressor = world.nations[0]
    val victim = world.nations[1]
    val ally = world.nations[2]

    victim.relations[ally.id] = RelationType.ALLIANCE
    ally.relations[victim.id] = RelationType.ALLIANCE
    world.warsEnabled = true

    engine.declareWar(world, aggressor, victim)

    assertEquals(RelationType.WAR, aggressor.relations[victim.id])
    assertEquals("Ally should automatically enter war against aggressor", RelationType.WAR, ally.relations[aggressor.id])
    assertEquals(RelationType.WAR, aggressor.relations[ally.id])
    assertEquals(2, world.warConflicts.count { it.isActive })
  }

  @Test
  fun `tech level advances stability rebellion secedes city and historyFrames record snapshots`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 5005L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    assertTrue("WorldGenerator should capture initial timelapse frame", world.historyFrames.isNotEmpty())

    engine.applyGodTool(world, GodTool.CREATE_NATION, 16, 16, 2, 0xFF38BDF8.toInt(), null, sound)
    val nation = world.nations[0]
    nation.treasury = 900
    nation.foundedYear.let { world.year = it + 9 }
    repeat(5) { idx ->
      world.buildings.add(
        Building(
          id = world.allocId(),
          x = 14 + idx,
          y = 18,
          type = BuildingType.FACTORY,
          cityId = world.cities.first().id,
          nationId = nation.id,
          hp = BuildingType.FACTORY.maxHp,
          maxHp = BuildingType.FACTORY.maxHp,
        )
      )
    }

    // Add a second city and force very low stability until rebellion occurs
    world.setOwner(52, 52, nation.id)
    engine.applyGodTool(world, GodTool.FOUND_CITY, 52, 52, 2, nation.colorArgb, nation.id, sound)
    assertEquals(2, world.cities.count { it.nationId == nation.id })

    nation.stability = 0.12f
    world.tickCount = 17L
    var rebelled = false
    repeat(20) {
      if (!rebelled) {
        nation.stability = 0.12f
        world.tickCount = 17L
        engine.step(world, 0.05f, 1, sound)
        if (world.nations.size >= 2) rebelled = true
      }
    }

    assertTrue("Tech level should advance beyond 1 with factories/treasury", nation.techLevel >= 2)
    assertTrue("Low stability with 2+ cities should trigger rebellion secession", rebelled)

    val beforeSnapshots = world.historyFrames.size
    world.year += 2
    world.recordHistorySnapshot()
    assertEquals(beforeSnapshots + 1, world.historyFrames.size)
  }

  @Test
  fun `road pathfinder avoids mountains connects founded cities and paint road bridges rivers`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 6001L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    for (y in 15..38) {
      for (x in 15..42) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    // Mountain wall blocking straight line from (20, 26) to (34, 26)
    for (y in 23..28) {
      world.setTerrain(27, y, TerrainType.MOUNTAIN)
    }

    WorldGenerator.buildRoadBetween(world, 20, 26, 34, 26)

    // Road must not overwrite mountain barrier, yet must place road tiles connecting around it
    for (y in 23..28) {
      assertEquals(TerrainType.MOUNTAIN, world.getTerrain(27, y))
    }
    val hasDetourRoad =
      (16..22).any { y -> world.getTerrain(27, y) == TerrainType.ROAD } ||
        (29..36).any { y -> world.getTerrain(27, y) == TerrainType.ROAD }
    assertTrue("Road pathfinder should detour around mountain range", hasDetourRoad)

    // Founding two cities within range should auto-connect them with a road
    engine.applyGodTool(world, GodTool.CREATE_NATION, 18, 18, 2, 0xFF38BDF8.toInt(), null, sound)
    engine.applyGodTool(world, GodTool.FOUND_CITY, 38, 18, 2, 0xFF38BDF8.toInt(), world.nations[0].id, sound)
    val roadTilesBetween = (22..34).count { x -> (15..22).any { y -> world.getTerrain(x, y) == TerrainType.ROAD } }
    assertTrue("Founding a second city should build a connecting highway", roadTilesBetween >= 8)

    // Painting road over a river tile creates a bridge road
    world.setTerrain(10, 10, TerrainType.RIVER)
    engine.applyGodTool(world, GodTool.PAINT_ROAD, 10, 10, 0, 0xFF38BDF8.toInt(), null, sound)
    assertEquals(TerrainType.ROAD, world.getTerrain(10, 10))
  }

  @Test
  fun `road autotiling connects horizontal vertical junctions and bridges in high and low quality`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 6002L)
    val world = WorldGenerator.generate(cfg)
    for (y in 10..20) {
      for (x in 10..20) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    // Crossroad at (15, 15) + horizontal bridge over river at (17, 15)
    for (x in 13..18) world.setTerrain(x, 15, TerrainType.ROAD)
    for (y in 13..17) world.setTerrain(15, y, TerrainType.ROAD)
    world.setTerrain(17, 14, TerrainType.RIVER)
    world.setTerrain(17, 16, TerrainType.RIVER)

    val renderer = PixelMapRenderer()
    val highBmp = renderer.renderTerrainBitmap(world, showTerritoryBorders = false, waterAnimPhase = 0, quality = GraphicsQuality.HIGH)
    // Tile (15,15) is a 4-way junction -> top middle subpixel (sx=1, sy=0) and right middle subpixel (sx=3, sy=1) must be road/marking, not grass
    val crossTopPixel = highBmp.getPixel(15 * 4 + 1, 15 * 4 + 0)
    val crossRightPixel = highBmp.getPixel(15 * 4 + 3, 15 * 4 + 1)
    assertFalse("North arm of 4-way road junction must not be grass green", crossTopPixel == 0xFF3F6A3F.toInt())
    assertFalse("East arm of 4-way road junction must not be grass green", crossRightPixel == 0xFF3F6A3F.toInt())

    // Low quality (2x2 subpixel) should also render cleanly without error
    val lowBmp = renderer.renderTerrainBitmap(world, showTerritoryBorders = true, waterAnimPhase = 1, quality = GraphicsQuality.LOW)
    assertEquals(world.width * 2, lowBmp.width)
  }

  @Test
  fun `vehicles follow connected roads around corners without cutting across grass even at 5x speed`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 7001L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    // Clear a grass plaza with an L-shaped + loop road network
    for (y in 10..28) {
      for (x in 10..28) {
        world.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    // Road rectangle loop: (14..22, 14) top, (22, 14..22) right, (14..22, 22) bottom, (14, 14..22) left
    for (x in 14..22) {
      world.setTerrain(x, 14, TerrainType.ROAD)
      world.setTerrain(x, 22, TerrainType.ROAD)
    }
    for (y in 14..22) {
      world.setTerrain(14, y, TerrainType.ROAD)
      world.setTerrain(22, y, TerrainType.ROAD)
    }

    val car =
      Vehicle(
        id = world.allocId(),
        x = 14.5f,
        y = 14.5f,
        targetX = 15.5f,
        targetY = 14.5f,
        type = VehicleType.CAR,
        nationId = 0,
        cityId = 0,
        prevTileX = 14,
        prevTileY = 14,
      )
    world.vehicles.add(car)

    val visitedTiles = mutableSetOf<Pair<Int, Int>>()
    repeat(60) {
      engine.step(world, 0.08f, 5, sound)
      val tx = car.x.toInt()
      val ty = car.y.toInt()
      visitedTiles.add(tx to ty)
      assertEquals(
        "Vehicle at ($tx, $ty) left the road at 5x speed!",
        TerrainType.ROAD,
        world.getTerrain(tx, ty),
      )
    }
    assertTrue("Vehicle should traverse many distinct road tiles around the loop", visitedTiles.size >= 14)
  }

  @Test
  fun `off road vehicle steers onto nearest road and city vehicles spawn on roads`() {
    val cfg = WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 2, seed = 7002L)
    val world = WorldGenerator.generate(cfg)
    val engine = WorldSimulationEngine()
    val sound = SirenSoundEngine().apply { enabled = false }

    assertTrue("Generated world should spawn city vehicles", world.vehicles.isNotEmpty())
    assertTrue(
      "Generated vehicles should spawn directly on ROAD tiles",
      world.vehicles.all { v -> world.getTerrain(v.x.toInt(), v.y.toInt()) == TerrainType.ROAD },
    )

    // Test off-road vehicle finding a nearby road
    val isolated = WorldGenerator.generate(WorldGenConfig(sizePreset = MapSizePreset.SMALL, cityCount = 0, seed = 7003L))
    for (y in 12..25) {
      for (x in 12..25) {
        isolated.setTerrain(x, y, TerrainType.GRASS)
      }
    }
    for (x in 15..22) isolated.setTerrain(x, 18, TerrainType.ROAD)

    val offRoadTruck =
      Vehicle(
        id = isolated.allocId(),
        x = 18.5f,
        y = 14.5f,
        targetX = 18.5f,
        targetY = 14.5f,
        type = VehicleType.TRUCK,
        nationId = 0,
        cityId = 0,
      )
    isolated.vehicles.add(offRoadTruck)

    repeat(30) {
      engine.step(isolated, 0.08f, 2, sound)
    }
    assertEquals(
      "Off-road vehicle should drive onto the nearby road and stay on it",
      TerrainType.ROAD,
      isolated.getTerrain(offRoadTruck.x.toInt(), offRoadTruck.y.toInt()),
    )
  }
}
