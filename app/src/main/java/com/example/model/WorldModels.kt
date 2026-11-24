package com.example.model

enum class TerrainType(
  val titleRu: String,
  val isWater: Boolean,
  val isWalkable: Boolean,
  val isBuildable: Boolean,
  val isFlammable: Boolean,
) {
  DEEP_WATER("Глубокая вода", true, false, false, false),
  WATER("Вода", true, false, false, false),
  RIVER("Река", true, true, false, false),
  SAND("Песок", false, true, true, false),
  GRASS("Трава", false, true, true, true),
  FOREST("Лес", false, true, false, true),
  MOUNTAIN("Горы", false, false, false, false),
  ROAD("Дорога", false, true, false, false),
  CITY_ZONE("Городская зона", false, true, true, true),
  RUINS("Разрушения", false, true, false, false),
}

enum class BuildingType(val titleRu: String, val maxHp: Int) {
  CAPITOL("Ратуша", 240),
  HOUSE("Жилой дом", 100),
  FACTORY("Завод", 140),
  FARM("Ферма", 90),
  RADAR("Радар ПВО", 160),
  SHELTER("Убежище", 220),
  POWER_PLANT("Электростанция", 150),
  RUINED_BUILDING("Руины здания", 40),
  MILITARY_BASE("Военная база", 260),
  EW_STATION("Станция РЭБ", 170),
}

data class Building(
  val id: Int,
  val x: Int,
  val y: Int,
  val type: BuildingType,
  val cityId: Int,
  val nationId: Int,
  val hp: Int,
  val maxHp: Int,
  val level: Int = 1,
  val isBurning: Boolean = false,
  val pvoCooldown: Float = 0f,
)

enum class Profession(val titleRu: String) {
  CIVILIAN("Житель"),
  BUILDER("Строитель"),
  FARMER("Фермер"),
  WORKER("Рабочий"),
  TRADER("Торговец"),
  MEDIC("Медик"),
  SOLDIER("Защитник"),
}

enum class CitizenState(val titleRu: String) {
  WORKING("Работает"),
  GOING_HOME("Идёт домой"),
  TRAVELING("В пути между городами"),
  EVACUATING("Эвакуация"),
  IN_SHELTER("В укрытии"),
  REBUILDING("Восстанавливает город"),
  PANIC("Паника"),
  PATROL("Патрулирует границы"),
}

data class Citizen(
  val id: Int,
  val name: String,
  var x: Float,
  var y: Float,
  var targetX: Float,
  var targetY: Float,
  var age: Int,
  var hp: Int,
  val maxHp: Int = 100,
  var hunger: Float = 0.1f,
  var nationId: Int,
  var cityId: Int,
  val familyId: Int,
  var profession: Profession,
  var state: CitizenState = CitizenState.WORKING,
  var alertTimer: Float = 0f,
  var homeX: Int,
  var homeY: Int,
  var workX: Int,
  var workY: Int,
  var animPhase: Float = 0f,
)

enum class VehicleType(val titleRu: String) {
  CAR("Автомобиль"),
  TRUCK("Грузовик"),
  BULLDOZER("Бульдозер"),
  PATROL_JEEP("Патрульная машина"),
  AMBULANCE("Скорая помощь"),
}

data class Vehicle(
  val id: Int,
  var x: Float,
  var y: Float,
  var targetX: Float,
  var targetY: Float,
  val type: VehicleType,
  val nationId: Int,
  val cityId: Int,
  var hp: Int = 100,
  var prevTileX: Int = -1,
  var prevTileY: Int = -1,
)

enum class UnitType(val titleRu: String, val maxHp: Int, val baseStrength: Int, val speed: Float, val isArmor: Boolean) {
  INFANTRY("Пехотное подразделение", 110, 18, 2.3f, false),
  ARMOR("Бронетехника", 220, 34, 2.8f, true),
  MILITARY_TRUCK("Военный транспорт", 140, 12, 3.6f, true),
  AIR_DEFENSE_UNIT("Мобильное ПВО", 150, 14, 2.4f, true),
}

enum class UnitOrder(val titleRu: String) {
  PATROL("Патрулирование"),
  DEFEND_CITY("Защита города"),
  ADVANCE("Наступление"),
  RETREAT("Отступление"),
}

data class MilitaryUnit(
  val id: Int,
  var nationId: Int,
  var cityId: Int,
  val type: UnitType,
  var x: Float,
  var y: Float,
  var targetX: Float,
  var targetY: Float,
  var hp: Int,
  val maxHp: Int = type.maxHp,
  var strength: Int = type.baseStrength,
  var order: UnitOrder = UnitOrder.PATROL,
  var morale: Float = 1.0f,
  var animPhase: Float = 0f,
  var isOnWater: Boolean = false,
  var empDisabledTimer: Float = 0f,
)

enum class AnimalType(val titleRu: String) {
  DEER("Олень"),
  WOLF("Волк"),
  BIRD("Птица"),
}

data class Animal(
  val id: Int,
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  val type: AnimalType,
  var hp: Int = 40,
)

enum class FlagPattern {
  TRICOLOR_H,
  TRICOLOR_V,
  CROSS,
  BICOLOR_DIAG,
  CANTON,
  STRIPE_STAR,
}

data class NationFlag(
  val pattern: FlagPattern,
  val primaryColor: Int,
  val secondaryColor: Int,
  val accentColor: Int,
)

enum class RelationType(val titleRu: String) {
  ALLIANCE("Союз"),
  TRADE("Торговля"),
  NEUTRAL("Нейтралитет"),
  TENSION("Напряжённость"),
  WAR("Война"),
}

data class Nation(
  val id: Int,
  var name: String,
  var colorArgb: Int,
  val flag: NationFlag,
  var capitalCityId: Int,
  var food: Int = 120,
  var materials: Int = 100,
  var energy: Int = 80,
  var treasury: Int = 150,
  var stability: Float = 0.85f,
  var techLevel: Int = 1,
  var totalCasualties: Int = 0,
  var wins: Int = 0,
  var losses: Int = 0,
  var territoriesCaptured: Int = 0,
  var airDefenseIntercepts: Int = 0,
  val foundedYear: Int = 1,
  val relations: MutableMap<Int, RelationType> = mutableMapOf(),
) {
  fun isAtWar(): Boolean = relations.values.any { it == RelationType.WAR }
}

data class WarConflict(
  val id: Int,
  val nationAId: Int,
  val nationBId: Int,
  val startYear: Int,
  val startMonth: Int,
  var casualtiesA: Int = 0,
  var casualtiesB: Int = 0,
  var tilesCapturedA: Int = 0,
  var tilesCapturedB: Int = 0,
  var airEventsCount: Int = 0,
  var pvoInterceptsCount: Int = 0,
  var isActive: Boolean = true,
  var endYear: Int = 0,
)

data class City(
  val id: Int,
  var name: String,
  var nationId: Int,
  val centerX: Int,
  val centerY: Int,
  var isCapital: Boolean = false,
  var underAirAlert: Boolean = false,
  var airAlertTimer: Float = 0f,
  var smokeLevel: Float = 0f,
  val foundedYear: Int = 1,
  var blackoutTimer: Float = 0f,
)

enum class WeatherType(val titleRu: String) {
  CLEAR("Ясно"),
  RAIN("Дождь"),
  FOG("Туман"),
  STORM("Гроза"),
}

enum class AirObjectType(val titleRu: String, val isHostileThreat: Boolean) {
  RECON_DRONE("Развед-дрон", false),
  JET_PATROL("Воздушный патруль", true),
  CARGO_PLANE("Транспортный борт", false),
  ROCKET_STREAK("Ракетное событие", true),
  METEOR("Метеорит", false),
  STRIKE_DRONE("Ударный дрон (Шахед)", true),
  PVO_INTERCEPTOR("Перехватчик ПВО", false),
  HEAVY_BOMBER("Тяжёлый борт", true),
  MILITARY_RECON_UAV("Военный БПЛА", true),
}

data class AirObject(
  val id: Int,
  var x: Float,
  var y: Float,
  val startX: Float,
  val startY: Float,
  val targetX: Float,
  val targetY: Float,
  var progress: Float = 0f,
  val speed: Float,
  val type: AirObjectType,
  var nationId: Int,
  var payloadTriggered: Boolean = false,
  var targetAirObjectId: Int = 0,
  var detectedByPvo: Boolean = false,
)

enum class ParticleType {
  EXPLOSION,
  SMOKE,
  FIRE,
  SPARK,
  SHOCKWAVE,
  LIGHTNING,
  INTERCEPT_FLASH,
  EMP_WAVE,
  RADIATION_GLOW,
}

data class WorldParticle(
  val id: Int,
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  var life: Float,
  val maxLife: Float,
  val size: Float,
  val colorArgb: Int,
  val type: ParticleType,
)

enum class WorldEventCategory(val titleRu: String) {
  WAR("Война"),
  DIPLOMACY("Дипломатия"),
  CITY("Города"),
  DISASTER("Бедствия"),
  AIR_ALERT("Тревога"),
  WEATHER("Погода"),
  PVO("ПВО"),
}

data class WorldNewsItem(
  val id: Int,
  val year: Int,
  val month: Int,
  val category: WorldEventCategory,
  val title: String,
  val description: String,
  val x: Int,
  val y: Int,
  val nationColorArgb: Int,
)

enum class MapSizePreset(val titleRu: String, val size: Int) {
  SMALL("Малый (72×72)", 72),
  MEDIUM("Средний (104×104)", 104),
  LARGE("Большой (136×136)", 136),
  HUGE("Огромный (192×192)", 192),
  GIANT("Гигантский (256×256)", 256),
}

enum class DensityPreset(val titleRu: String, val multiplier: Float) {
  LOW("Низкая", 0.6f),
  NORMAL("Обычная", 1.0f),
  HIGH("Высокая", 1.6f),
}

data class WorldGenConfig(
  val sizePreset: MapSizePreset = MapSizePreset.MEDIUM,
  val waterPercent: Int = 34,
  val forestPercent: Int = 38,
  val cityCount: Int = 5,
  val popDensity: DensityPreset = DensityPreset.NORMAL,
  val seed: Long = System.currentTimeMillis() and 0xFFFFFL,
)

enum class GraphicsQuality(val titleRu: String) {
  LOW("Низкое"),
  MEDIUM("Среднее"),
  HIGH("Высокое"),
}

enum class ParticleQuality(val titleRu: String, val maxParticles: Int) {
  LOW("Минимум", 70),
  NORMAL("Стандарт", 180),
  HIGH("Максимум", 320),
}

enum class SimTickRate(val titleRu: String, val delayMs: Long) {
  ECO("Эконом (12 Гц)", 82L),
  BALANCED("Баланс (20 Гц)", 50L),
  SMOOTH("Плавная (30 Гц)", 33L),
}

data class GameSettings(
  val soundVolume: Float = 0.8f,
  val sirenVolume: Float = 0.85f,
  val musicEnabled: Boolean = false,
  val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
  val particleQuality: ParticleQuality = ParticleQuality.NORMAL,
  val simTickRate: SimTickRate = SimTickRate.BALANCED,
  val showTerritoryBorders: Boolean = true,
  val showMinimap: Boolean = true,
  val autosaveEnabled: Boolean = true,
)

enum class ToolCategory(val titleRu: String) {
  WORLD("Мир"),
  LIFE("Жизнь"),
  MILITARY("Армия"),
  AIR_EVENTS("Воздух"),
  NATIONS("Государства"),
  EVENTS("События"),
  DESTRUCTION("Разрушения"),
}

enum class GodTool(
  val category: ToolCategory,
  val titleRu: String,
  val shortLabel: String,
  val usesBrush: Boolean = true,
) {
  // World
  PAINT_GRASS(ToolCategory.WORLD, "Создать землю", "Земля", true),
  PAINT_WATER(ToolCategory.WORLD, "Создать воду", "Вода", true),
  PAINT_FOREST(ToolCategory.WORLD, "Создать лес", "Лес", true),
  PAINT_MOUNTAIN(ToolCategory.WORLD, "Создать горы", "Горы", true),
  PAINT_SAND(ToolCategory.WORLD, "Создать песок", "Песок", true),
  PAINT_ROAD(ToolCategory.WORLD, "Проложить дорогу", "Дорога", true),
  ERASE_TERRAIN(ToolCategory.WORLD, "Удалить территорию", "Удалить", true),

  // Life
  SPAWN_CITIZENS(ToolCategory.LIFE, "Создать жителей", "Жители", true),
  SPAWN_ANIMALS(ToolCategory.LIFE, "Создать животных", "Животные", true),
  FOUND_CITY(ToolCategory.LIFE, "Создать город", "Город", false),
  FOUND_SETTLEMENT(ToolCategory.LIFE, "Создать поселение", "Поселение", false),

  // Military
  SPAWN_INFANTRY(ToolCategory.MILITARY, "Создать пехоту", "Пехота", false),
  SPAWN_ARMOR(ToolCategory.MILITARY, "Создать бронетехнику", "Техника", false),
  BUILD_MILITARY_BASE(ToolCategory.MILITARY, "Военная база", "База", false),
  BUILD_PVO_RADAR(ToolCategory.MILITARY, "Разместить ПВО", "ПВО", false),
  TOGGLE_GLOBAL_WARS(ToolCategory.MILITARY, "Вкл/Выкл войны", "Реж. войн", false),
  TRIGGER_WAR_EVENT(ToolCategory.MILITARY, "Начать конфликт", "Конфликт", false),
  BUILD_EW_STATION(ToolCategory.MILITARY, "Построить РЭБ", "РЭБ", false),

  // Air Events
  TRIGGER_AIR_ALERT(ToolCategory.AIR_EVENTS, "Воздушная тревога", "Тревога", false),
  SPAWN_RECON_DRONE(ToolCategory.AIR_EVENTS, "Развед-дрон", "Разведка", false),
  SPAWN_STRIKE_DRONE(ToolCategory.AIR_EVENTS, "Ударный дрон (Шахед)", "Шахед", false),
  TRIGGER_ROCKET_EVENT(ToolCategory.AIR_EVENTS, "Ракетное событие", "Ракета", false),
  SPAWN_JET_PATROL(ToolCategory.AIR_EVENTS, "Воздушный патруль", "Патруль", false),
  SPAWN_PVO_INTERCEPTOR(ToolCategory.AIR_EVENTS, "Перехват ПВО", "Перехват", false),
  SPAWN_MILITARY_UAV(ToolCategory.AIR_EVENTS, "Военный БПЛА", "БПЛА", false),
  SPAWN_HEAVY_BOMBER(ToolCategory.AIR_EVENTS, "Тяжёлый борт", "Борт", false),

  // Nations
  CREATE_NATION(ToolCategory.NATIONS, "Создать государство", "Новое гос.", false),
  PAINT_TERRITORY(ToolCategory.NATIONS, "Управление территориями", "Границы", true),
  MERGE_NATIONS(ToolCategory.NATIONS, "Объединить государства", "Объединить", false),
  SPLIT_NATION(ToolCategory.NATIONS, "Разделить государство", "Разделить", false),
  TOGGLE_NATION_WAR(ToolCategory.NATIONS, "Война / Мир государств", "Война/Мир", false),
  CYCLE_DIPLOMACY(ToolCategory.NATIONS, "Изменить отношения", "Отношения", false),

  // Events
  TRIGGER_EXPLOSION(ToolCategory.EVENTS, "Взрыв", "Взрыв", false),
  CYCLE_WEATHER(ToolCategory.EVENTS, "Сменить погоду", "Погода", false),
  RANDOM_EVENT(ToolCategory.EVENTS, "Случайное событие", "Событие", false),
  EMP_STRIKE(ToolCategory.EVENTS, "ЭМИ-импульс", "ЭМИ", false),

  // Destruction
  REMOVE_BUILDINGS(ToolCategory.DESTRUCTION, "Удалить здания", "Снос", true),
  CREATE_RUINS(ToolCategory.DESTRUCTION, "Создать разрушения", "Руины", true),
  IGNITE_FIRE(ToolCategory.DESTRUCTION, "Пожар", "Пожар", true),
  DROP_METEOR(ToolCategory.DESTRUCTION, "Метеорит", "Метеорит", false),
  RESTORE_AREA(ToolCategory.DESTRUCTION, "Восстановление", "Ремонт", true),
  NUKE_STRIKE(ToolCategory.DESTRUCTION, "Ядерный удар", "Ядерка", false),
}

sealed class SelectionTarget {
  data object None : SelectionTarget()

  data class TileInfo(
    val x: Int,
    val y: Int,
    val terrain: TerrainType,
    val nationId: Int,
    val fireLevel: Int,
    val building: Building?,
  ) : SelectionTarget()

  data class CityInfo(val cityId: Int) : SelectionTarget()

  data class NationInfo(val nationId: Int) : SelectionTarget()

  data class CitizenInfo(val citizenId: Int) : SelectionTarget()

  data class MilitaryUnitInfo(val unitId: Int) : SelectionTarget()
}
