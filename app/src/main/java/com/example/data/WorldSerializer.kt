package com.example.data

import com.example.engine.WorldState
import com.example.model.AirObject
import com.example.model.AirObjectType
import com.example.model.Animal
import com.example.model.AnimalType
import com.example.model.Building
import com.example.model.BuildingType
import com.example.model.Citizen
import com.example.model.CitizenState
import com.example.model.City
import com.example.model.DensityPreset
import com.example.model.FlagPattern
import com.example.model.MapSizePreset
import com.example.model.MilitaryUnit
import com.example.model.Nation
import com.example.model.NationFlag
import com.example.model.Profession
import com.example.model.RelationType
import com.example.model.UnitOrder
import com.example.model.UnitType
import com.example.model.Vehicle
import com.example.model.VehicleType
import com.example.model.WarConflict
import com.example.model.WeatherType
import com.example.model.WorldEventCategory
import com.example.model.WorldGenConfig
import com.example.model.WorldNewsItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

object WorldSerializer {
  private const val CURRENT_SAVE_VERSION = 2

  fun compressState(state: WorldState): ByteArray {
    val root = JSONObject()
    root.put("saveVersion", CURRENT_SAVE_VERSION)
    root.put("w", state.width)
    root.put("h", state.height)
    root.put("year", state.year)
    root.put("month", state.month)
    root.put("tick", state.tickCount)
    root.put("tod", state.timeOfDay.toDouble())
    root.put("weather", state.weather.ordinal)
    root.put("weatherTimer", state.weatherTimer.toDouble())
    root.put("wars", state.warsEnabled)
    root.put("warsCount", state.totalWarsCount)
    root.put("eventsCount", state.totalEventsCount)
    root.put("destBld", state.totalDestroyedBuildings)
    root.put("rebBld", state.totalRebuiltBuildings)
    root.put("pvoInt", state.totalPvoIntercepts)
    root.put("nextId", state.nextId)

    val cfg = JSONObject()
    cfg.put("sizePreset", state.config.sizePreset.ordinal)
    cfg.put("water", state.config.waterPercent)
    cfg.put("forest", state.config.forestPercent)
    cfg.put("cities", state.config.cityCount)
    cfg.put("pop", state.config.popDensity.ordinal)
    cfg.put("seed", state.config.seed)
    root.put("cfg", cfg)

    root.put("terrain", rleEncode(state.terrain))
    root.put("owners", rleEncode(state.nationOwner))
    root.put("fires", rleEncode(state.fireGrid))
    root.put("rad", rleEncode(state.radiationGrid))

    val nationsArr = JSONArray()
    for (n in state.nations) {
      val jo = JSONObject()
      jo.put("id", n.id)
      jo.put("name", n.name)
      jo.put("color", n.colorArgb)
      jo.put("cap", n.capitalCityId)
      jo.put("food", n.food)
      jo.put("mat", n.materials)
      jo.put("eng", n.energy)
      jo.put("trs", n.treasury)
      jo.put("stab", n.stability.toDouble())
      jo.put("tech", n.techLevel)
      jo.put("cas", n.totalCasualties)
      jo.put("wins", n.wins)
      jo.put("losses", n.losses)
      jo.put("tcap", n.territoriesCaptured)
      jo.put("pvo", n.airDefenseIntercepts)
      jo.put("fy", n.foundedYear)
      val fObj = JSONObject()
      fObj.put("pat", n.flag.pattern.ordinal)
      fObj.put("c1", n.flag.primaryColor)
      fObj.put("c2", n.flag.secondaryColor)
      fObj.put("c3", n.flag.accentColor)
      jo.put("flag", fObj)
      val relObj = JSONObject()
      for ((k, v) in n.relations) relObj.put(k.toString(), v.ordinal)
      jo.put("rel", relObj)
      nationsArr.put(jo)
    }
    root.put("nations", nationsArr)

    val citiesArr = JSONArray()
    for (c in state.cities) {
      val jo = JSONObject()
      jo.put("id", c.id)
      jo.put("name", c.name)
      jo.put("nid", c.nationId)
      jo.put("x", c.centerX)
      jo.put("y", c.centerY)
      jo.put("cap", c.isCapital)
      jo.put("alert", c.underAirAlert)
      jo.put("atimer", c.airAlertTimer.toDouble())
      jo.put("smoke", c.smokeLevel.toDouble())
      jo.put("fy", c.foundedYear)
      jo.put("blk", c.blackoutTimer.toDouble())
      citiesArr.put(jo)
    }
    root.put("cities", citiesArr)

    val bArr = JSONArray()
    for (b in state.buildings) {
      val jo = JSONObject()
      jo.put("id", b.id)
      jo.put("x", b.x)
      jo.put("y", b.y)
      jo.put("t", b.type.ordinal)
      jo.put("cid", b.cityId)
      jo.put("nid", b.nationId)
      jo.put("hp", b.hp)
      jo.put("mhp", b.maxHp)
      jo.put("lvl", b.level)
      jo.put("burn", b.isBurning)
      jo.put("cd", b.pvoCooldown.toDouble())
      bArr.put(jo)
    }
    root.put("buildings", bArr)

    val cArr = JSONArray()
    for (c in state.citizens) {
      val jo = JSONObject()
      jo.put("id", c.id)
      jo.put("name", c.name)
      jo.put("x", c.x.toDouble())
      jo.put("y", c.y.toDouble())
      jo.put("tx", c.targetX.toDouble())
      jo.put("ty", c.targetY.toDouble())
      jo.put("age", c.age)
      jo.put("hp", c.hp)
      jo.put("mhp", c.maxHp)
      jo.put("hng", c.hunger.toDouble())
      jo.put("nid", c.nationId)
      jo.put("cid", c.cityId)
      jo.put("fid", c.familyId)
      jo.put("p", c.profession.ordinal)
      jo.put("st", c.state.ordinal)
      jo.put("at", c.alertTimer.toDouble())
      jo.put("hx", c.homeX)
      jo.put("hy", c.homeY)
      jo.put("wx", c.workX)
      jo.put("wy", c.workY)
      cArr.put(jo)
    }
    root.put("citizens", cArr)

    val vArr = JSONArray()
    for (v in state.vehicles) {
      val jo = JSONObject()
      jo.put("id", v.id)
      jo.put("x", v.x.toDouble())
      jo.put("y", v.y.toDouble())
      jo.put("tx", v.targetX.toDouble())
      jo.put("ty", v.targetY.toDouble())
      jo.put("t", v.type.ordinal)
      jo.put("nid", v.nationId)
      jo.put("cid", v.cityId)
      jo.put("hp", v.hp)
      vArr.put(jo)
    }
    root.put("vehicles", vArr)

    val uArr = JSONArray()
    for (u in state.militaryUnits) {
      val jo = JSONObject()
      jo.put("id", u.id)
      jo.put("nid", u.nationId)
      jo.put("cid", u.cityId)
      jo.put("t", u.type.ordinal)
      jo.put("x", u.x.toDouble())
      jo.put("y", u.y.toDouble())
      jo.put("tx", u.targetX.toDouble())
      jo.put("ty", u.targetY.toDouble())
      jo.put("hp", u.hp)
      jo.put("mhp", u.maxHp)
      jo.put("str", u.strength)
      jo.put("ord", u.order.ordinal)
      jo.put("mor", u.morale.toDouble())
      jo.put("water", u.isOnWater)
      jo.put("emp", u.empDisabledTimer.toDouble())
      uArr.put(jo)
    }
    root.put("units", uArr)

    val aArr = JSONArray()
    for (a in state.animals) {
      val jo = JSONObject()
      jo.put("id", a.id)
      jo.put("x", a.x.toDouble())
      jo.put("y", a.y.toDouble())
      jo.put("vx", a.vx.toDouble())
      jo.put("vy", a.vy.toDouble())
      jo.put("t", a.type.ordinal)
      jo.put("hp", a.hp)
      aArr.put(jo)
    }
    root.put("animals", aArr)

    val airArr = JSONArray()
    for (air in state.airObjects) {
      val jo = JSONObject()
      jo.put("id", air.id)
      jo.put("x", air.x.toDouble())
      jo.put("y", air.y.toDouble())
      jo.put("sx", air.startX.toDouble())
      jo.put("sy", air.startY.toDouble())
      jo.put("tx", air.targetX.toDouble())
      jo.put("ty", air.targetY.toDouble())
      jo.put("pr", air.progress.toDouble())
      jo.put("sp", air.speed.toDouble())
      jo.put("t", air.type.ordinal)
      jo.put("nid", air.nationId)
      jo.put("pay", air.payloadTriggered)
      jo.put("tid", air.targetAirObjectId)
      jo.put("det", air.detectedByPvo)
      airArr.put(jo)
    }
    root.put("airObjects", airArr)

    val warArr = JSONArray()
    for (wc in state.warConflicts) {
      val jo = JSONObject()
      jo.put("id", wc.id)
      jo.put("a", wc.nationAId)
      jo.put("b", wc.nationBId)
      jo.put("sy", wc.startYear)
      jo.put("sm", wc.startMonth)
      jo.put("ca", wc.casualtiesA)
      jo.put("cb", wc.casualtiesB)
      jo.put("ta", wc.tilesCapturedA)
      jo.put("tb", wc.tilesCapturedB)
      jo.put("air", wc.airEventsCount)
      jo.put("pvo", wc.pvoInterceptsCount)
      jo.put("act", wc.isActive)
      jo.put("ey", wc.endYear)
      warArr.put(jo)
    }
    root.put("conflicts", warArr)

    val newsArr = JSONArray()
    for (n in state.newsLog.take(60)) {
      val jo = JSONObject()
      jo.put("id", n.id)
      jo.put("y", n.year)
      jo.put("m", n.month)
      jo.put("cat", n.category.ordinal)
      jo.put("ti", n.title)
      jo.put("de", n.description)
      jo.put("px", n.x)
      jo.put("py", n.y)
      jo.put("col", n.nationColorArgb)
      newsArr.put(jo)
    }
    root.put("news", newsArr)

    val baos = ByteArrayOutputStream()
    GZIPOutputStream(baos).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
    return baos.toByteArray()
  }

  fun decompressState(bytes: ByteArray): WorldState {
    val jsonStr = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    val root = JSONObject(jsonStr)
    val saveVersion = root.optInt("saveVersion", 1)

    val cfgObj = root.getJSONObject("cfg")
    val cfg =
      WorldGenConfig(
        sizePreset = MapSizePreset.entries[cfgObj.optInt("sizePreset", 1).coerceIn(0, MapSizePreset.entries.lastIndex)],
        waterPercent = cfgObj.optInt("water", 34),
        forestPercent = cfgObj.optInt("forest", 38),
        cityCount = cfgObj.optInt("cities", 5),
        popDensity = DensityPreset.entries[cfgObj.optInt("pop", 1).coerceIn(0, DensityPreset.entries.lastIndex)],
        seed = cfgObj.optLong("seed", 12345L),
      )

    val w = root.getInt("w")
    val h = root.getInt("h")
    val state = WorldState(w, h, cfg)
    state.year = root.optInt("year", 2026)
    state.month = root.optInt("month", 1)
    state.tickCount = root.optLong("tick", 0L)
    state.timeOfDay = root.optDouble("tod", 0.35).toFloat()
    state.weather = WeatherType.entries[root.optInt("weather", 0).coerceIn(0, WeatherType.entries.lastIndex)]
    state.weatherTimer = root.optDouble("weatherTimer", 35.0).toFloat()
    state.warsEnabled = root.optBoolean("wars", true)
    state.totalWarsCount = root.optInt("warsCount", 0)
    state.totalEventsCount = root.optInt("eventsCount", 0)
    state.totalDestroyedBuildings = root.optInt("destBld", 0)
    state.totalRebuiltBuildings = root.optInt("rebBld", 0)
    state.totalPvoIntercepts = root.optInt("pvoInt", 0)
    var maxId = root.optInt("nextId", 1)

    rleDecodeInto(root.optString("terrain", ""), state.terrain)
    rleDecodeInto(root.optString("owners", ""), state.nationOwner)
    rleDecodeInto(root.optString("fires", ""), state.fireGrid)
    rleDecodeInto(root.optString("rad", ""), state.radiationGrid)

    // Restore deterministic tile variation so graphics look identical
    for (i in 0 until state.size) {
      state.variationGrid[i] = ((i * 7 + w * 3) and 0x0F).toByte()
    }

    val nationsArr = root.optJSONArray("nations") ?: JSONArray()
    for (i in 0 until nationsArr.length()) {
      val jo = nationsArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      val fObj = jo.getJSONObject("flag")
      val flag =
        NationFlag(
          pattern = FlagPattern.entries[fObj.optInt("pat", 0).coerceIn(0, FlagPattern.entries.lastIndex)],
          primaryColor = fObj.getInt("c1"),
          secondaryColor = fObj.getInt("c2"),
          accentColor = fObj.getInt("c3"),
        )
      val relMap = mutableMapOf<Int, RelationType>()
      val relObj = jo.optJSONObject("rel")
      if (relObj != null) {
        val keys = relObj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          val kInt = k.toIntOrNull() ?: continue
          relMap[kInt] = RelationType.entries[relObj.getInt(k).coerceIn(0, RelationType.entries.lastIndex)]
        }
      }
      state.nations.add(
        Nation(
          id = id,
          name = jo.getString("name"),
          colorArgb = jo.getInt("color"),
          flag = flag,
          capitalCityId = jo.optInt("cap", 0),
          food = jo.optInt("food", 100),
          materials = jo.optInt("mat", 100),
          energy = jo.optInt("eng", 80),
          treasury = jo.optInt("trs", 100),
          stability = jo.optDouble("stab", 0.85).toFloat(),
          techLevel = jo.optInt("tech", 1),
          totalCasualties = jo.optInt("cas", 0),
          wins = jo.optInt("wins", 0),
          losses = jo.optInt("losses", 0),
          territoriesCaptured = jo.optInt("tcap", 0),
          airDefenseIntercepts = jo.optInt("pvo", 0),
          foundedYear = jo.optInt("fy", 2026),
          relations = relMap,
        )
      )
    }

    val citiesArr = root.optJSONArray("cities") ?: JSONArray()
    for (i in 0 until citiesArr.length()) {
      val jo = citiesArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      val underAlert = jo.optBoolean("alert", false)
      state.cities.add(
        City(
          id = id,
          name = jo.getString("name"),
          nationId = jo.getInt("nid"),
          centerX = jo.getInt("x").coerceIn(0, w - 1),
          centerY = jo.getInt("y").coerceIn(0, h - 1),
          isCapital = jo.optBoolean("cap", false),
          underAirAlert = underAlert,
          airAlertTimer = jo.optDouble("atimer", if (underAlert) 10.0 else 0.0).toFloat(),
          smokeLevel = jo.optDouble("smoke", 0.0).toFloat(),
          foundedYear = jo.optInt("fy", 2026),
          blackoutTimer = jo.optDouble("blk", 0.0).toFloat(),
        )
      )
    }

    val bArr = root.optJSONArray("buildings") ?: JSONArray()
    for (i in 0 until bArr.length()) {
      val jo = bArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      val bType = BuildingType.entries[jo.optInt("t", 0).coerceIn(0, BuildingType.entries.lastIndex)]
      state.buildings.add(
        Building(
          id = id,
          x = jo.getInt("x").coerceIn(0, w - 1),
          y = jo.getInt("y").coerceIn(0, h - 1),
          type = bType,
          cityId = jo.optInt("cid", 0),
          nationId = jo.optInt("nid", 0),
          hp = jo.optInt("hp", bType.maxHp),
          maxHp = jo.optInt("mhp", bType.maxHp),
          level = jo.optInt("lvl", 1),
          isBurning = jo.optBoolean("burn", false),
          pvoCooldown = jo.optDouble("cd", 0.0).toFloat(),
        )
      )
    }

    val cArr = root.optJSONArray("citizens") ?: JSONArray()
    for (i in 0 until cArr.length()) {
      val jo = cArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      val x = jo.getDouble("x").toFloat().coerceIn(0.5f, w - 1.5f)
      val y = jo.getDouble("y").toFloat().coerceIn(0.5f, h - 1.5f)
      state.citizens.add(
        Citizen(
          id = id,
          name = jo.getString("name"),
          x = x,
          y = y,
          targetX = jo.optDouble("tx", x.toDouble()).toFloat().coerceIn(0.5f, w - 1.5f),
          targetY = jo.optDouble("ty", y.toDouble()).toFloat().coerceIn(0.5f, h - 1.5f),
          age = jo.optInt("age", 25),
          hp = jo.optInt("hp", 100),
          maxHp = jo.optInt("mhp", 100),
          hunger = jo.optDouble("hng", 0.1).toFloat(),
          nationId = jo.optInt("nid", 0),
          cityId = jo.optInt("cid", 0),
          familyId = jo.optInt("fid", id),
          profession = Profession.entries[jo.optInt("p", 0).coerceIn(0, Profession.entries.lastIndex)],
          state = CitizenState.entries[jo.optInt("st", 0).coerceIn(0, CitizenState.entries.lastIndex)],
          alertTimer = jo.optDouble("at", 0.0).toFloat(),
          homeX = jo.optInt("hx", x.toInt()),
          homeY = jo.optInt("hy", y.toInt()),
          workX = jo.optInt("wx", x.toInt()),
          workY = jo.optInt("wy", y.toInt()),
        )
      )
    }

    val vArr = root.optJSONArray("vehicles") ?: JSONArray()
    for (i in 0 until vArr.length()) {
      val jo = vArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      val x = jo.getDouble("x").toFloat().coerceIn(0.5f, w - 1.5f)
      val y = jo.getDouble("y").toFloat().coerceIn(0.5f, h - 1.5f)
      state.vehicles.add(
        Vehicle(
          id = id,
          x = x,
          y = y,
          targetX = jo.optDouble("tx", x.toDouble()).toFloat().coerceIn(0.5f, w - 1.5f),
          targetY = jo.optDouble("ty", y.toDouble()).toFloat().coerceIn(0.5f, h - 1.5f),
          type = VehicleType.entries[jo.optInt("t", 0).coerceIn(0, VehicleType.entries.lastIndex)],
          nationId = jo.optInt("nid", 0),
          cityId = jo.optInt("cid", 0),
          hp = jo.optInt("hp", 100),
        )
      )
    }

    if (saveVersion >= 2) {
      val uArr = root.optJSONArray("units") ?: JSONArray()
      for (i in 0 until uArr.length()) {
        val jo = uArr.getJSONObject(i)
        val id = jo.getInt("id")
        if (id > maxId) maxId = id
        val uType = UnitType.entries[jo.optInt("t", 0).coerceIn(0, UnitType.entries.lastIndex)]
        val x = jo.getDouble("x").toFloat().coerceIn(0.5f, w - 1.5f)
        val y = jo.getDouble("y").toFloat().coerceIn(0.5f, h - 1.5f)
        state.militaryUnits.add(
          MilitaryUnit(
            id = id,
            nationId = jo.getInt("nid"),
            cityId = jo.optInt("cid", 0),
            type = uType,
            x = x,
            y = y,
            targetX = jo.optDouble("tx", x.toDouble()).toFloat(),
            targetY = jo.optDouble("ty", y.toDouble()).toFloat(),
            hp = jo.optInt("hp", uType.maxHp),
            maxHp = jo.optInt("mhp", uType.maxHp),
            strength = jo.optInt("str", uType.baseStrength),
            order = UnitOrder.entries[jo.optInt("ord", 0).coerceIn(0, UnitOrder.entries.lastIndex)],
            morale = jo.optDouble("mor", 1.0).toFloat(),
            isOnWater = jo.optBoolean("water", false),
            empDisabledTimer = jo.optDouble("emp", 0.0).toFloat(),
          )
        )
      }

      val airArr = root.optJSONArray("airObjects") ?: JSONArray()
      for (i in 0 until airArr.length()) {
        val jo = airArr.getJSONObject(i)
        val id = jo.getInt("id")
        if (id > maxId) maxId = id
        state.airObjects.add(
          AirObject(
            id = id,
            x = jo.getDouble("x").toFloat(),
            y = jo.getDouble("y").toFloat(),
            startX = jo.getDouble("sx").toFloat(),
            startY = jo.getDouble("sy").toFloat(),
            targetX = jo.getDouble("tx").toFloat(),
            targetY = jo.getDouble("ty").toFloat(),
            progress = jo.optDouble("pr", 0.0).toFloat(),
            speed = jo.optDouble("sp", 0.3).toFloat(),
            type = AirObjectType.entries[jo.optInt("t", 0).coerceIn(0, AirObjectType.entries.lastIndex)],
            nationId = jo.optInt("nid", 0),
            payloadTriggered = jo.optBoolean("pay", false),
            targetAirObjectId = jo.optInt("tid", 0),
            detectedByPvo = jo.optBoolean("det", false),
          )
        )
      }

      val warArr = root.optJSONArray("conflicts") ?: JSONArray()
      for (i in 0 until warArr.length()) {
        val jo = warArr.getJSONObject(i)
        val id = jo.getInt("id")
        if (id > maxId) maxId = id
        state.warConflicts.add(
          WarConflict(
            id = id,
            nationAId = jo.getInt("a"),
            nationBId = jo.getInt("b"),
            startYear = jo.getInt("sy"),
            startMonth = jo.getInt("sm"),
            casualtiesA = jo.optInt("ca", 0),
            casualtiesB = jo.optInt("cb", 0),
            tilesCapturedA = jo.optInt("ta", 0),
            tilesCapturedB = jo.optInt("tb", 0),
            airEventsCount = jo.optInt("air", 0),
            pvoInterceptsCount = jo.optInt("pvo", 0),
            isActive = jo.optBoolean("act", true),
            endYear = jo.optInt("ey", 0),
          )
        )
      }
    } else {
      // Migration from v1: synthesize WarConflicts for existing WAR relations
      for (a in state.nations) {
        for ((bId, rel) in a.relations) {
          if (rel == RelationType.WAR && a.id < bId) {
            state.warConflicts.add(
              WarConflict(
                id = ++maxId,
                nationAId = a.id,
                nationBId = bId,
                startYear = state.year,
                startMonth = state.month,
                isActive = true,
              )
            )
          }
        }
      }
    }

    val aArr = root.optJSONArray("animals") ?: JSONArray()
    for (i in 0 until aArr.length()) {
      val jo = aArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      state.animals.add(
        Animal(
          id = id,
          x = jo.getDouble("x").toFloat().coerceIn(0.5f, w - 1.5f),
          y = jo.getDouble("y").toFloat().coerceIn(0.5f, h - 1.5f),
          vx = jo.optDouble("vx", 0.2).toFloat(),
          vy = jo.optDouble("vy", -0.2).toFloat(),
          type = AnimalType.entries[jo.optInt("t", 0).coerceIn(0, AnimalType.entries.lastIndex)],
          hp = jo.optInt("hp", 40),
        )
      )
    }

    val newsArr = root.optJSONArray("news") ?: JSONArray()
    for (i in 0 until newsArr.length()) {
      val jo = newsArr.getJSONObject(i)
      val id = jo.getInt("id")
      if (id > maxId) maxId = id
      state.newsLog.add(
        WorldNewsItem(
          id = id,
          year = jo.getInt("y"),
          month = jo.getInt("m"),
          category = WorldEventCategory.entries[jo.optInt("cat", 0).coerceIn(0, WorldEventCategory.entries.lastIndex)],
          title = jo.getString("ti"),
          description = jo.getString("de"),
          x = jo.optInt("px", w / 2),
          y = jo.optInt("py", h / 2),
          nationColorArgb = jo.optInt("col", 0xFF38BDF8.toInt()),
        )
      )
    }

    state.nextId = maxId + 1
    state.terrainDirty = true
    return state
  }

  private fun rleEncode(arr: IntArray): String {
    if (arr.isEmpty()) return ""
    val sb = StringBuilder(arr.size / 2)
    var cur = arr[0]
    var count = 1
    for (i in 1 until arr.size) {
      if (arr[i] == cur) {
        count++
      } else {
        sb.append(cur).append(':').append(count).append(',')
        cur = arr[i]
        count = 1
      }
    }
    sb.append(cur).append(':').append(count)
    return sb.toString()
  }

  private fun rleDecodeInto(encoded: String, dest: IntArray) {
    if (encoded.isEmpty()) return
    var idx = 0
    val tokens = encoded.split(',')
    for (tok in tokens) {
      val sep = tok.indexOf(':')
      if (sep <= 0) continue
      val valInt = tok.substring(0, sep).toIntOrNull() ?: 0
      val count = tok.substring(sep + 1).toIntOrNull() ?: 0
      val end = (idx + count).coerceAtMost(dest.size)
      while (idx < end) {
        dest[idx++] = valInt
      }
    }
  }
}
