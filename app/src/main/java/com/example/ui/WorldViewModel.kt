package com.example.ui

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SirenSoundEngine
import com.example.data.AppDatabase
import com.example.data.WorldSaveRepository
import com.example.data.WorldSaveSummary
import com.example.data.WorldSerializer
import com.example.engine.ChronicleGenerator
import com.example.engine.ChronicleSnapshot
import com.example.engine.NameGenerator
import com.example.engine.PixelMapRenderer
import com.example.engine.WorldGenerator
import com.example.engine.WorldSimulationEngine
import com.example.engine.WorldState
import com.example.model.AirObject
import com.example.model.Animal
import com.example.model.Building
import com.example.model.Citizen
import com.example.model.City
import com.example.model.GameSettings
import com.example.model.GodTool
import com.example.model.GraphicsQuality
import com.example.model.MilitaryUnit
import com.example.model.Nation
import com.example.model.RelationType
import com.example.model.SelectionTarget
import com.example.model.ToolCategory
import com.example.model.Vehicle
import com.example.model.WarConflict
import com.example.model.WeatherType
import com.example.model.WorldGenConfig
import com.example.model.WorldNewsItem
import com.example.model.WorldParticle
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ActiveDialog {
  NONE,
  NEW_WORLD,
  STATS,
  NEWS,
  SAVES,
  CONFLICTS,
  SETTINGS,
}

data class RenderFrame(
  val mapWidth: Int = 104,
  val mapHeight: Int = 104,
  val terrainBitmap: ImageBitmap? = null,
  val minimapBitmap: ImageBitmap? = null,
  val nations: List<Nation> = emptyList(),
  val cities: List<City> = emptyList(),
  val buildings: List<Building> = emptyList(),
  val citizens: List<Citizen> = emptyList(),
  val vehicles: List<Vehicle> = emptyList(),
  val militaryUnits: List<MilitaryUnit> = emptyList(),
  val animals: List<Animal> = emptyList(),
  val airObjects: List<AirObject> = emptyList(),
  val particles: List<WorldParticle> = emptyList(),
  val warConflicts: List<WarConflict> = emptyList(),
  val timeOfDay: Float = 0.35f,
  val nightIntensity: Float = 0f,
  val dawnWarmth: Float = 0f,
  val weather: WeatherType = WeatherType.CLEAR,
  val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
  val historyFrames: List<Pair<Int, IntArray>> = emptyList(),
  val frameVersion: Long = 0L,
)

data class WorldUiState(
  val year: Int = 2026,
  val month: Int = 1,
  val population: Int = 0,
  val nationCount: Int = 0,
  val cityCount: Int = 0,
  val buildingCount: Int = 0,
  val animalCount: Int = 0,
  val militaryUnitsCount: Int = 0,
  val activeWarsCount: Int = 0,
  val totalWarsCount: Int = 0,
  val totalEventsCount: Int = 0,
  val totalCasualties: Int = 0,
  val totalDestroyedBuildings: Int = 0,
  val totalRebuiltBuildings: Int = 0,
  val totalPvoIntercepts: Int = 0,
  val activeAirAlerts: List<String> = emptyList(),
  val weather: WeatherType = WeatherType.CLEAR,
  val isNight: Boolean = false,
  val speedMultiplier: Int = 1,
  val warsEnabled: Boolean = true,
  val soundEnabled: Boolean = true,
  val showTerritoryBorders: Boolean = true,
  val showMinimap: Boolean = true,
  val isPanInspectMode: Boolean = false,
  val selectedCategory: ToolCategory = ToolCategory.WORLD,
  val selectedTool: GodTool = GodTool.PAINT_GRASS,
  val brushRadius: Int = 2,
  val selectedNationColor: Int = NameGenerator.nationColors.first(),
  val activeNationId: Int? = null,
  val activeDialog: ActiveDialog = ActiveDialog.NONE,
  val selection: SelectionTarget = SelectionTarget.None,
  val latestNews: WorldNewsItem? = null,
  val newsLog: List<WorldNewsItem> = emptyList(),
  val genConfig: WorldGenConfig = WorldGenConfig(),
  val settings: GameSettings = GameSettings(),
  val statusBannerMessage: String? = null,
  val chronicleText: String? = null,
  val isGeneratingChronicle: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorldViewModel(application: Application) : AndroidViewModel(application) {
  private val simDispatcher = Dispatchers.Default.limitedParallelism(1)
  private val saveMutex = Mutex()
  private val simEngine = WorldSimulationEngine()
  private val mapRenderer = PixelMapRenderer()
  private val soundEngine = SirenSoundEngine()
  private val saveRepo = WorldSaveRepository(AppDatabase.getInstance(application).worldSaveDao())

  private var worldState: WorldState = WorldGenerator.generate(WorldGenConfig())
  private var waterAnimTick: Int = 0
  private var minimapCountdown: Int = 0

  private val _uiState = MutableStateFlow(WorldUiState())
  val uiState: StateFlow<WorldUiState> = _uiState.asStateFlow()

  private val _renderFrame = MutableStateFlow(RenderFrame())
  val renderFrame: StateFlow<RenderFrame> = _renderFrame.asStateFlow()

  private val _cameraFocusRequest = MutableStateFlow<Pair<Float, Float>?>(null)
  val cameraFocusRequest: StateFlow<Pair<Float, Float>?> = _cameraFocusRequest.asStateFlow()

  val saveSummaries: StateFlow<List<WorldSaveSummary>> =
    saveRepo.saveSummaries.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  init {
    viewModelScope.launch(simDispatcher) {
      publishSnapshot(forceMinimap = true, forceTerrain = true)
    }

    // Main Simulation Loop
    viewModelScope.launch(simDispatcher) {
      var lastTime = System.nanoTime()
      while (isActive) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.01f, 0.1f)
        lastTime = now

        val curUi = _uiState.value
        val speed = curUi.speedMultiplier
        if (speed > 0) {
          simEngine.step(
            state = worldState,
            dt = dt,
            speedMultiplier = speed,
            soundEngine = soundEngine,
            maxParticles = curUi.settings.particleQuality.maxParticles,
          )
          val animChanged = (worldState.tickCount % 6L == 0L)
          if (animChanged) waterAnimTick++
          val shouldRenderTerrain = worldState.terrainDirty || animChanged
          publishSnapshot(
            forceMinimap = (minimapCountdown-- <= 0),
            forceTerrain = shouldRenderTerrain,
          )
          if (minimapCountdown < 0) minimapCountdown = 10
          delay(curUi.settings.simTickRate.delayMs)
        } else {
          delay(120L)
        }
      }
    }

    // Periodic Autosave every 45 seconds
    viewModelScope.launch(simDispatcher) {
      while (isActive) {
        delay(45_000L)
        if (_uiState.value.settings.autosaveEnabled && saveMutex.tryLock()) {
          try {
            val bytes = WorldSerializer.compressState(worldState)
            val year = worldState.year
            val population = worldState.citizens.size
            val nationCount = worldState.nations.size
            val cityCount = worldState.cities.size
            val mapSize = worldState.width
            saveRepo.savePreparedSnapshot(
              slotId = "autosave",
              slotTitle = "Автосохранение",
              year = year,
              population = population,
              nationCount = nationCount,
              cityCount = cityCount,
              mapSize = mapSize,
              compressedSnapshot = bytes,
            )
          } catch (_: Throwable) {
          } finally {
            saveMutex.unlock()
          }
        }
      }
    }
  }

  private fun publishSnapshot(forceMinimap: Boolean = false, forceTerrain: Boolean = false) {
    val curUi = _uiState.value
    val showBorders = curUi.showTerritoryBorders
    val terrainImg =
      if (forceTerrain || _renderFrame.value.terrainBitmap == null) {
        worldState.terrainDirty = false
        mapRenderer
          .renderTerrainBitmap(worldState, showBorders, waterAnimTick, curUi.settings.graphicsQuality)
          .asImageBitmap()
      } else {
        _renderFrame.value.terrainBitmap
      }

    val minimapImg =
      if (forceMinimap || _renderFrame.value.minimapBitmap == null) {
        mapRenderer.renderMinimapBitmap(worldState).asImageBitmap()
      } else {
        _renderFrame.value.minimapBitmap
      }

    val nationsSnap = worldState.nations.map { it.copy(relations = it.relations.toMutableMap()) }
    val citiesSnap = worldState.cities.map { it.copy() }
    val buildingsSnap = worldState.buildings.toList()
    val citizensSnap = worldState.citizens.map { it.copy() }
    val vehiclesSnap = worldState.vehicles.map { it.copy() }
    val unitsSnap = worldState.militaryUnits.map { it.copy() }
    val animalsSnap = worldState.animals.map { it.copy() }
    val airSnap = worldState.airObjects.map { it.copy() }
    val particlesSnap = worldState.particles.map { it.copy() }
    val conflictsSnap = worldState.warConflicts.map { it.copy() }

    val activeAlerts = citiesSnap.filter { it.underAirAlert }.map { it.name }
    val activeWars = conflictsSnap.count { it.isActive }
    val totalCasualties = nationsSnap.sumOf { it.totalCasualties }

    _renderFrame.value =
      RenderFrame(
        mapWidth = worldState.width,
        mapHeight = worldState.height,
        terrainBitmap = terrainImg,
        minimapBitmap = minimapImg,
        nations = nationsSnap,
        cities = citiesSnap,
        buildings = buildingsSnap,
        citizens = citizensSnap,
        vehicles = vehiclesSnap,
        militaryUnits = unitsSnap,
        animals = animalsSnap,
        airObjects = airSnap,
        particles = particlesSnap,
        warConflicts = conflictsSnap,
        timeOfDay = worldState.timeOfDay,
        nightIntensity = worldState.nightIntensity(),
        dawnWarmth = worldState.dawnDuskWarmth(),
        weather = worldState.weather,
        graphicsQuality = curUi.settings.graphicsQuality,
        historyFrames = worldState.historyFrames.toList(),
        frameVersion = worldState.worldVersion,
      )

    val newsChanged =
      worldState.newsLog.size != curUi.newsLog.size ||
        worldState.newsLog.firstOrNull()?.id != curUi.newsLog.firstOrNull()?.id
    val nextNewsLog = if (newsChanged) worldState.newsLog.toList() else curUi.newsLog

    _uiState.update { cur ->
      cur.copy(
        year = worldState.year,
        month = worldState.month,
        population = citizensSnap.size,
        nationCount = nationsSnap.size,
        cityCount = citiesSnap.size,
        buildingCount = buildingsSnap.size,
        animalCount = animalsSnap.size,
        militaryUnitsCount = unitsSnap.size,
        activeWarsCount = activeWars,
        totalWarsCount = worldState.totalWarsCount,
        totalEventsCount = worldState.totalEventsCount,
        totalCasualties = totalCasualties,
        totalDestroyedBuildings = worldState.totalDestroyedBuildings,
        totalRebuiltBuildings = worldState.totalRebuiltBuildings,
        totalPvoIntercepts = worldState.totalPvoIntercepts,
        activeAirAlerts = activeAlerts,
        weather = worldState.weather,
        isNight = worldState.isNight(),
        warsEnabled = worldState.warsEnabled,
        latestNews = nextNewsLog.firstOrNull(),
        newsLog = nextNewsLog,
      )
    }
  }

  fun onMapTapOrPaint(tileX: Int, tileY: Int, isDrag: Boolean) {
    val ui = _uiState.value
    if (ui.isPanInspectMode) {
      if (!isDrag) {
        inspectAt(tileX, tileY)
      }
      return
    }
    if (isDrag && !ui.selectedTool.usesBrush) return

    viewModelScope.launch(simDispatcher) {
      simEngine.applyGodTool(
        state = worldState,
        tool = ui.selectedTool,
        tileX = tileX,
        tileY = tileY,
        brushRadius = ui.brushRadius,
        selectedColor = ui.selectedNationColor,
        activeNationId = ui.activeNationId,
        soundEngine = soundEngine,
        maxParticles = ui.settings.particleQuality.maxParticles,
      )
      publishSnapshot(forceMinimap = true, forceTerrain = true)
    }
  }

  fun inspectAt(tileX: Int, tileY: Int) {
    viewModelScope.launch(simDispatcher) {
      if (!worldState.inBounds(tileX, tileY)) return@launch

      val nearUnit =
        worldState.militaryUnits.minByOrNull { hypot(it.x - (tileX + 0.5f), it.y - (tileY + 0.5f)) }
      if (nearUnit != null && hypot(nearUnit.x - (tileX + 0.5f), nearUnit.y - (tileY + 0.5f)) < 1.8f) {
        _uiState.update { it.copy(selection = SelectionTarget.MilitaryUnitInfo(nearUnit.id)) }
        return@launch
      }

      val nearCitizen =
        worldState.citizens.minByOrNull { hypot(it.x - (tileX + 0.5f), it.y - (tileY + 0.5f)) }
      if (nearCitizen != null && hypot(nearCitizen.x - (tileX + 0.5f), nearCitizen.y - (tileY + 0.5f)) < 1.8f) {
        _uiState.update { it.copy(selection = SelectionTarget.CitizenInfo(nearCitizen.id)) }
        return@launch
      }

      val nearCity =
        worldState.cities.minByOrNull { hypot((it.centerX - tileX).toFloat(), (it.centerY - tileY).toFloat()) }
      if (nearCity != null && hypot((nearCity.centerX - tileX).toFloat(), (nearCity.centerY - tileY).toFloat()) <= 3.5f) {
        _uiState.update { it.copy(selection = SelectionTarget.CityInfo(nearCity.id)) }
        return@launch
      }

      val ownerId = worldState.getOwner(tileX, tileY)
      if (ownerId > 0) {
        _uiState.update {
          it.copy(
            selection = SelectionTarget.NationInfo(ownerId),
            activeNationId = ownerId,
          )
        }
        return@launch
      }

      val building = worldState.buildings.firstOrNull { it.x == tileX && it.y == tileY }
      _uiState.update {
        it.copy(
          selection =
            SelectionTarget.TileInfo(
              x = tileX,
              y = tileY,
              terrain = worldState.getTerrain(tileX, tileY),
              nationId = ownerId,
              fireLevel = worldState.fireGrid[worldState.idx(tileX, tileY)],
              building = building,
            )
        )
      }
    }
  }

  fun selectNation(nationId: Int) {
    _uiState.update {
      it.copy(
        selection = SelectionTarget.NationInfo(nationId),
        activeNationId = nationId,
        activeDialog = ActiveDialog.NONE,
      )
    }
    val city = _renderFrame.value.cities.firstOrNull { it.nationId == nationId }
    if (city != null) {
      focusCameraOn(city.centerX.toFloat(), city.centerY.toFloat())
    }
  }

  fun focusCameraOn(tileX: Float, tileY: Float) {
    _cameraFocusRequest.value = tileX to tileY
  }

  fun centerCamera() {
    focusCameraOn(worldState.width / 2f, worldState.height / 2f)
  }

  fun clearCameraFocusRequest() {
    _cameraFocusRequest.value = null
  }

  fun setSpeed(speed: Int) {
    _uiState.update { it.copy(speedMultiplier = speed) }
  }

  fun toggleWarsEnabled() {
    viewModelScope.launch(simDispatcher) {
      worldState.warsEnabled = !worldState.warsEnabled
      publishSnapshot()
    }
  }

  fun toggleSound() {
    val next = !_uiState.value.soundEnabled
    soundEngine.enabled = next
    _uiState.update { it.copy(soundEnabled = next) }
  }

  fun toggleTerritoryBorders() {
    viewModelScope.launch(simDispatcher) {
      val next = !_uiState.value.showTerritoryBorders
      _uiState.update {
        it.copy(
          showTerritoryBorders = next,
          settings = it.settings.copy(showTerritoryBorders = next),
        )
      }
      publishSnapshot(forceMinimap = true, forceTerrain = true)
    }
  }

  fun toggleMinimap() {
    _uiState.update {
      val next = !it.showMinimap
      it.copy(showMinimap = next, settings = it.settings.copy(showMinimap = next))
    }
  }

  fun togglePanInspectMode() {
    _uiState.update { it.copy(isPanInspectMode = !it.isPanInspectMode) }
  }

  fun updateSettings(newSettings: GameSettings) {
    soundEngine.updateVolumes(newSettings.soundVolume, newSettings.sirenVolume, newSettings.musicEnabled)
    viewModelScope.launch(simDispatcher) {
      val terrainNeedsRedraw =
        newSettings.showTerritoryBorders != _uiState.value.showTerritoryBorders ||
          newSettings.graphicsQuality != _uiState.value.settings.graphicsQuality
      _uiState.update {
        it.copy(
          settings = newSettings,
          showTerritoryBorders = newSettings.showTerritoryBorders,
          showMinimap = newSettings.showMinimap,
        )
      }
      if (terrainNeedsRedraw) {
        publishSnapshot(forceMinimap = true, forceTerrain = true)
      }
    }
  }

  fun selectCategory(category: ToolCategory) {
    val firstTool = GodTool.entries.first { it.category == category }
    _uiState.update {
      it.copy(
        selectedCategory = category,
        selectedTool = firstTool,
        isPanInspectMode = false,
      )
    }
  }

  fun selectTool(tool: GodTool) {
    _uiState.update {
      it.copy(
        selectedCategory = tool.category,
        selectedTool = tool,
        isPanInspectMode = false,
      )
    }
  }

  fun setBrushRadius(radius: Int) {
    _uiState.update { it.copy(brushRadius = radius) }
  }

  fun setNationColor(color: Int) {
    _uiState.update { it.copy(selectedNationColor = color) }
  }

  fun openDialog(dialog: ActiveDialog) {
    _uiState.update { it.copy(activeDialog = dialog) }
  }

  fun clearSelection() {
    _uiState.update { it.copy(selection = SelectionTarget.None) }
  }

  fun clearStatusBanner() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }

  fun generateNewWorld(config: WorldGenConfig) {
    viewModelScope.launch(simDispatcher) {
      worldState = WorldGenerator.generate(config)
      _uiState.update {
        it.copy(
          genConfig = config,
          activeDialog = ActiveDialog.NONE,
          selection = SelectionTarget.None,
          activeNationId = null,
          chronicleText = null,
          isGeneratingChronicle = false,
          latestNews = null,
          newsLog = emptyList(),
          statusBannerMessage = "Создан новый мир (${config.sizePreset.size}×${config.sizePreset.size})",
        )
      }
      publishSnapshot(forceMinimap = true, forceTerrain = true)
      focusCameraOn(worldState.width / 2f, worldState.height / 2f)
    }
  }

  fun triggerCityAirAlertById(cityId: Int) {
    viewModelScope.launch(simDispatcher) {
      val city = worldState.cities.find { it.id == cityId } ?: return@launch
      simEngine.triggerCityAirAlert(worldState, city, soundEngine, triggerRocket = true)
      publishSnapshot(forceMinimap = true)
    }
  }

  fun toggleDiplomaticWar(nationAId: Int, nationBId: Int) {
    viewModelScope.launch(simDispatcher) {
      val a = worldState.nations.find { it.id == nationAId } ?: return@launch
      val b = worldState.nations.find { it.id == nationBId } ?: return@launch
      val cur = a.relations[b.id] ?: RelationType.NEUTRAL
      if (cur == RelationType.WAR) {
        simEngine.endWar(worldState, a, b)
      } else {
        worldState.warsEnabled = true
        simEngine.declareWar(worldState, a, b)
      }
      publishSnapshot()
    }
  }

  fun generateChronicle() {
    if (_uiState.value.isGeneratingChronicle) return
    _uiState.update { it.copy(isGeneratingChronicle = true) }
    viewModelScope.launch(simDispatcher) {
      val snapshot =
        ChronicleSnapshot(
          year = worldState.year,
          month = worldState.month,
          weather = worldState.weather,
          population = worldState.citizens.size,
          nations = worldState.nations.map { it.copy(relations = it.relations.toMutableMap()) },
          cities = worldState.cities.map { it.copy() },
          warConflicts = worldState.warConflicts.map { it.copy() },
          totalPvoIntercepts = worldState.totalPvoIntercepts,
        )
      val text =
        try {
          ChronicleGenerator.generateChronicle(snapshot)
        } catch (_: Throwable) {
          ChronicleGenerator.generateOfflineChronicle(snapshot)
        }
      _uiState.update { it.copy(chronicleText = text, isGeneratingChronicle = false) }
    }
  }

  fun saveToSlot(slotId: String, slotTitle: String) {
    viewModelScope.launch(simDispatcher) {
      try {
        saveMutex.withLock {
          val bytes = WorldSerializer.compressState(worldState)
          val year = worldState.year
          val population = worldState.citizens.size
          val nationCount = worldState.nations.size
          val cityCount = worldState.cities.size
          val mapSize = worldState.width
          saveRepo.savePreparedSnapshot(
            slotId = slotId,
            slotTitle = slotTitle,
            year = year,
            population = population,
            nationCount = nationCount,
            cityCount = cityCount,
            mapSize = mapSize,
            compressedSnapshot = bytes,
          )
        }
        _uiState.update { it.copy(statusBannerMessage = "Сохранено в «$slotTitle»") }
      } catch (_: Throwable) {
        _uiState.update { it.copy(statusBannerMessage = "Ошибка сохранения") }
      }
    }
  }

  fun loadFromSlot(slotId: String, slotTitle: String) {
    viewModelScope.launch(simDispatcher) {
      try {
        val loaded = saveRepo.loadWorld(slotId)
        if (loaded != null) {
          worldState = loaded
          _uiState.update {
            it.copy(
              activeDialog = ActiveDialog.NONE,
              selection = SelectionTarget.None,
              activeNationId = null,
              chronicleText = null,
              isGeneratingChronicle = false,
              latestNews = null,
              newsLog = emptyList(),
              genConfig = loaded.config,
              statusBannerMessage = "Загружен мир из «$slotTitle»",
            )
          }
          publishSnapshot(forceMinimap = true, forceTerrain = true)
          focusCameraOn(worldState.width / 2f, worldState.height / 2f)
        }
      } catch (_: Throwable) {
        _uiState.update { it.copy(statusBannerMessage = "Ошибка загрузки сохранения") }
      }
    }
  }

  fun deleteSaveSlot(slotId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      saveRepo.deleteSlot(slotId)
    }
  }

  override fun onCleared() {
    super.onCleared()
    soundEngine.release()
  }
}
