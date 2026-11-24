package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.WorldSaveSummary
import com.example.model.DensityPreset
import com.example.model.GameSettings
import com.example.model.GraphicsQuality
import com.example.model.MapSizePreset
import com.example.model.ParticleQuality
import com.example.model.SimTickRate
import com.example.model.WorldEventCategory
import com.example.model.WorldGenConfig
import com.example.ui.RenderFrame
import com.example.ui.WorldUiState
import com.example.ui.theme.DeepSpaceBg
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudSurfaceDark
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.LifeEmerald
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarCrimson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun NewWorldDialog(
  initialConfig: WorldGenConfig,
  onDismiss: () -> Unit,
  onGenerate: (WorldGenConfig) -> Unit,
) {
  var sizePreset by remember { mutableStateOf(initialConfig.sizePreset) }
  var waterPercent by remember { mutableIntStateOf(initialConfig.waterPercent) }
  var forestPercent by remember { mutableIntStateOf(initialConfig.forestPercent) }
  var cityCount by remember { mutableIntStateOf(initialConfig.cityCount) }
  var popDensity by remember { mutableStateOf(initialConfig.popDensity) }
  var seed by remember { mutableLongStateOf(Random.nextLong(10000L, 999999L)) }
  var scenarioName by remember { mutableStateOf("Случайный") }

  DialogShell(title = "Генерация нового мира", onDismiss = onDismiss) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Сценарий мира", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        PresetChip("Случайный", scenarioName == "Случайный") {
          scenarioName = "Случайный"
          waterPercent = 34
          forestPercent = 38
          cityCount = 5
          popDensity = DensityPreset.NORMAL
          seed = Random.nextLong(10000L, 999999L)
        }
        PresetChip("Архипелаг", scenarioName == "Архипелаг") {
          scenarioName = "Архипелаг"
          waterPercent = 62
          forestPercent = 30
          cityCount = 6
          popDensity = DensityPreset.NORMAL
          seed = Random.nextLong(10000L, 999999L)
        }
        PresetChip("Сверхдержавы", scenarioName == "Сверхдержавы") {
          scenarioName = "Сверхдержавы"
          waterPercent = 25
          forestPercent = 35
          cityCount = 2
          popDensity = DensityPreset.HIGH
          seed = Random.nextLong(10000L, 999999L)
        }
        PresetChip("Королевская битва", scenarioName == "Королевская битва") {
          scenarioName = "Королевская битва"
          waterPercent = 18
          forestPercent = 30
          cityCount = 10
          popDensity = DensityPreset.NORMAL
          seed = Random.nextLong(10000L, 999999L)
        }
      }

      Text("Размер карты (до 256×256)", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        MapSizePreset.entries.forEach { preset ->
          PresetChip(
            label = preset.titleRu,
            selected = sizePreset == preset,
            onClick = { sizePreset = preset },
          )
        }
      }

      SliderSetting("Количество воды: $waterPercent%", waterPercent.toFloat(), 5f..75f) {
        waterPercent = it.roundToInt()
      }
      SliderSetting("Количество лесов: $forestPercent%", forestPercent.toFloat(), 0f..85f) {
        forestPercent = it.roundToInt()
      }
      SliderSetting("Городов и государств: $cityCount", cityCount.toFloat(), 0f..10f) {
        cityCount = it.roundToInt()
      }

      Text("Плотность населения", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DensityPreset.entries.forEach { preset ->
          PresetChip(
            label = preset.titleRu,
            selected = popDensity == preset,
            modifier = Modifier.weight(1f),
            onClick = { popDensity = preset },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Зерно мира: #$seed", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        OutlinedButton(onClick = { seed = Random.nextLong(10000L, 999999L) }) {
          Icon(Icons.Default.Casino, contentDescription = "Случайно", modifier = Modifier.size(16.dp))
          Spacer(Modifier.size(6.dp))
          Text("Случайно")
        }
      }

      Button(
        onClick = {
          onGenerate(
            WorldGenConfig(
              sizePreset = sizePreset,
              waterPercent = waterPercent,
              forestPercent = forestPercent,
              cityCount = cityCount,
              popDensity = popDensity,
              seed = seed,
            )
          )
        },
        colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = DeepSpaceBg),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_generate_world"),
      ) {
        Text("Создать мир", style = MaterialTheme.typography.titleSmall)
      }
    }
  }
}

@Composable
fun WorldStatsDialog(
  uiState: WorldUiState,
  frame: RenderFrame,
  onDismiss: () -> Unit,
  onSelectNation: (Int) -> Unit,
  onGenerateChronicle: () -> Unit,
) {
  var activeTab by remember { mutableIntStateOf(0) }

  DialogShell(title = "Статистика мира", onDismiss = onDismiss) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PresetChip("Показатели", activeTab == 0, Modifier.weight(1f)) { activeTab = 0 }
        PresetChip("Хроника", activeTab == 1, Modifier.weight(1f)) { activeTab = 1 }
        PresetChip("Таймлапс", activeTab == 2, Modifier.weight(1f)) { activeTab = 2 }
      }

      if (activeTab == 0) {
        // 12 Requested World Metrics in 3 clean rows of 4 cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StatKpiCard("Население", "${uiState.population}", LifeEmerald, Modifier.weight(1f))
          StatKpiCard("Государств", "${uiState.nationCount}", TacticalCyan, Modifier.weight(1f))
          StatKpiCard("Городов", "${uiState.cityCount}", TacticalAmber, Modifier.weight(1f))
          StatKpiCard("Зданий", "${uiState.buildingCount}", TextPrimary, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StatKpiCard("Животных", "${uiState.animalCount}", LifeEmerald, Modifier.weight(1f))
          StatKpiCard("Армии", "${uiState.militaryUnitsCount}", TacticalAmber, Modifier.weight(1f))
          StatKpiCard("Конфликтов", "${uiState.activeWarsCount}", WarCrimson, Modifier.weight(1f))
          StatKpiCard("Войн всего", "${uiState.totalWarsCount}", WarCrimson, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          StatKpiCard("Возд. событ.", "${uiState.totalEventsCount}", TacticalCyan, Modifier.weight(1f))
          StatKpiCard("Потерь", "${uiState.totalCasualties}", WarCrimson, Modifier.weight(1f))
          StatKpiCard("Разрушено", "${uiState.totalDestroyedBuildings}", TacticalAmber, Modifier.weight(1f))
          StatKpiCard("Восстанов.", "${uiState.totalRebuiltBuildings}", LifeEmerald, Modifier.weight(1f))
        }

        if (frame.nations.isNotEmpty()) {
          val nationStats =
            frame.nations.map { n ->
              n to frame.citizens.count { it.nationId == n.id }
            }.sortedByDescending { it.second }
          val maxPop = (nationStats.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

          Text("Численность государств (ПВО перехватов: ${uiState.totalPvoIntercepts})", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
          Canvas(
            modifier =
              Modifier.fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HudSurfaceElevated)
                .padding(8.dp)
          ) {
            val barW = (size.width / nationStats.size.coerceAtLeast(1)) * 0.65f
            val step = size.width / nationStats.size.coerceAtLeast(1)
            nationStats.forEachIndexed { idx, (nation, pop) ->
              val ratio = (pop.toFloat() / maxPop).coerceIn(0.08f, 1f)
              val bh = size.height * ratio
              drawRect(
                color = Color(nation.colorArgb),
                topLeft = Offset(idx * step + (step - barW) / 2f, size.height - bh),
                size = Size(barW, bh),
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nationStats.forEach { (nation, pop) ->
              val cities = frame.cities.count { it.nationId == nation.id }
              val units = frame.militaryUnits.count { it.nationId == nation.id }
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudSurfaceElevated)
                    .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
                    .clickable { onSelectNation(nation.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  PixelFlagView(nation.flag)
                  Column {
                    Text(nation.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                      "Гор: $cities • Нас: $pop • Арм: $units • Потерь: ${nation.totalCasualties}",
                      style = MaterialTheme.typography.labelSmall,
                      color = TextSecondary,
                    )
                  }
                }
                if (nation.isAtWar()) {
                  Text("ВОЙНА", style = MaterialTheme.typography.labelSmall, color = WarCrimson)
                }
              }
            }
          }
        }
      } else if (activeTab == 1) {
        // AI / Offline Era Chronicle Card + Year-by-year Events Chronicle
        Button(
          onClick = onGenerateChronicle,
          enabled = !uiState.isGeneratingChronicle,
          colors = ButtonDefaults.buttonColors(containerColor = TacticalCyan, contentColor = DeepSpaceBg),
          modifier = Modifier.fillMaxWidth().height(42.dp).testTag("generate_chronicle_button"),
        ) {
          if (uiState.isGeneratingChronicle) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = DeepSpaceBg,
              strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(8.dp))
            Text("Составление сводки...", style = MaterialTheme.typography.labelLarge)
          } else {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Сводка хрониста", style = MaterialTheme.typography.labelLarge)
          }
        }

        val chronicle = uiState.chronicleText
        if (!chronicle.isNullOrBlank()) {
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(HudSurfaceElevated)
                .border(1.dp, TacticalAmber.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text("ВЕСТНИК ЭПОХИ", style = MaterialTheme.typography.labelMedium, color = TacticalAmber)
            Text(chronicle, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
          }
        }

        val grouped = uiState.newsLog.groupBy { it.year }.toSortedMap(compareByDescending { it })
        if (grouped.isEmpty()) {
          Text("История пока пуста.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (year, items) ->
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HudSurfaceElevated)
                    .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text("Год $year", style = MaterialTheme.typography.titleSmall, color = TacticalCyan)
                items.forEach { ev ->
                  Text(
                    "• [${ev.month.toString().padStart(2, '0')}] ${ev.title} — ${ev.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                  )
                }
              }
            }
          }
        }
      } else {
        val frames = frame.historyFrames
        if (frames.isEmpty()) {
          Text("Снимки истории мира ещё не накоплены.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
          var frameIdx by remember(frames.size) { mutableIntStateOf(frames.lastIndex) }
          var isPlaying by remember { mutableStateOf(false) }
          val clampedIdx = frameIdx.coerceIn(0, frames.lastIndex)
          val (snapYear, snapGrid) = frames[clampedIdx]

          LaunchedEffect(isPlaying, frames.size) {
            while (isPlaying && frames.size > 1) {
              delay(420L)
              frameIdx = (frameIdx + 1) % frames.size
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "Год $snapYear (кадр ${clampedIdx + 1}/${frames.size})",
              style = MaterialTheme.typography.titleSmall,
              color = TacticalCyan,
            )
            OutlinedButton(
              onClick = { isPlaying = !isPlaying },
              enabled = frames.size > 1,
              modifier = Modifier.height(34.dp).testTag("timelapse_play_pause"),
            ) {
              Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.size(4.dp))
              Text(if (isPlaying) "Пауза" else "Старт", style = MaterialTheme.typography.labelSmall)
            }
          }

          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(
              modifier =
                Modifier.size(224.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(DeepSpaceBg)
                  .border(1.5.dp, HudBorderBright, RoundedCornerShape(8.dp))
            ) {
              val cellW = size.width / 32f
              val cellH = size.height / 32f
              for (sy in 0 until 32) {
                for (sx in 0 until 32) {
                  val idx = sy * 32 + sx
                  val c = if (idx < snapGrid.size) Color(snapGrid[idx]) else DeepSpaceBg
                  drawRect(
                    color = c,
                    topLeft = Offset(sx * cellW, sy * cellH),
                    size = Size(cellW + 0.5f, cellH + 0.5f),
                  )
                }
              }
            }
          }

          if (frames.size > 1) {
            Slider(
              value = clampedIdx.toFloat(),
              onValueChange = {
                isPlaying = false
                frameIdx = it.roundToInt().coerceIn(0, frames.lastIndex)
              },
              valueRange = 0f..frames.lastIndex.toFloat(),
              colors = SliderDefaults.colors(thumbColor = TacticalCyan, activeTrackColor = TacticalCyan),
            )
          }
        }
      }
    }
  }
}

@Composable
fun ConflictsDialog(
  frame: RenderFrame,
  currentYear: Int,
  currentMonth: Int = 1,
  onToggleWar: (Int, Int) -> Unit,
  onJumpToNation: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  DialogShell(title = "Военные конфликты", onDismiss = onDismiss) {
    val conflicts = frame.warConflicts
    if (conflicts.isEmpty()) {
      Text(
        "Военных конфликтов пока не зафиксировано. Включите войны или используйте инструмент «Конфликт».",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(conflicts, key = { it.id }) { wc ->
          val a = frame.nations.find { it.id == wc.nationAId }
          val b = frame.nations.find { it.id == wc.nationBId }
          val unitsA = frame.militaryUnits.count { it.nationId == wc.nationAId }
          val unitsB = frame.militaryUnits.count { it.nationId == wc.nationBId }
          val durationYears = ((if (wc.isActive) currentYear else wc.endYear) - wc.startYear).coerceAtLeast(0)
          val durationText =
            if (durationYears == 0) {
              if (wc.isActive) {
                val months = ((currentYear - wc.startYear) * 12 + (currentMonth - wc.startMonth)).coerceAtLeast(0)
                if (months <= 0) "< 1 мес." else "$months мес."
              } else {
                "< 1 г."
              }
            } else {
              "$durationYears г."
            }

          Column(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HudSurfaceElevated)
                .border(1.5.dp, if (wc.isActive) WarCrimson else HudBorder, RoundedCornerShape(10.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (wc.isActive) "АКТИВНЫЙ КОНФЛИКТ" else "ПЕРЕМИРИЕ (${wc.startYear}–${wc.endYear})",
                style = MaterialTheme.typography.labelMedium,
                color = if (wc.isActive) WarCrimson else LifeEmerald,
              )
              Text("Длит: $durationText", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }

            // Side A vs Side B
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).clickable { a?.let { onJumpToNation(it.id) } }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  if (a != null) PixelFlagView(a.flag)
                  Text(a?.name ?: "Гос. #${wc.nationAId}", style = MaterialTheme.typography.labelLarge, color = Color(a?.colorArgb ?: 0xFFFFFFFF.toInt()))
                }
                Text("Армия: $unitsA • Потерь: ${wc.casualtiesA}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                Text("Захвачено: ${wc.tilesCapturedA}", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
              }

              Text("VS", style = MaterialTheme.typography.titleSmall, color = TacticalAmber, modifier = Modifier.padding(horizontal = 6.dp))

              Column(
                modifier = Modifier.weight(1f).clickable { b?.let { onJumpToNation(it.id) } },
                horizontalAlignment = Alignment.End,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(b?.name ?: "Гос. #${wc.nationBId}", style = MaterialTheme.typography.labelLarge, color = Color(b?.colorArgb ?: 0xFFFFFFFF.toInt()))
                  if (b != null) PixelFlagView(b.flag)
                }
                Text("Армия: $unitsB • Потерь: ${wc.casualtiesB}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                Text("Захвачено: ${wc.tilesCapturedB}", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                "Возд. событий: ${wc.airEventsCount} • ПВО: ${wc.pvoInterceptsCount}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
              )
              if (a != null && b != null) {
                OutlinedButton(
                  onClick = { onToggleWar(a.id, b.id) },
                  modifier = Modifier.height(32.dp),
                ) {
                  Icon(
                    if (wc.isActive) Icons.Default.Handshake else Icons.Default.Gavel,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(Modifier.size(4.dp))
                  Text(if (wc.isActive) "Перемирие" else "Возобновить", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingsDialog(
  settings: GameSettings,
  onUpdateSettings: (GameSettings) -> Unit,
  onDismiss: () -> Unit,
) {
  DialogShell(title = "Настройки игры", onDismiss = onDismiss) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SliderSetting("Громкость звуков: ${(settings.soundVolume * 100).roundToInt()}%", settings.soundVolume, 0f..1f) {
        onUpdateSettings(settings.copy(soundVolume = it))
      }
      SliderSetting("Громкость сирены: ${(settings.sirenVolume * 100).roundToInt()}%", settings.sirenVolume, 0f..1f) {
        onUpdateSettings(settings.copy(sirenVolume = it))
      }

      SettingSwitchRow("Фоновая эмбиент-музыка", settings.musicEnabled) {
        onUpdateSettings(settings.copy(musicEnabled = it))
      }
      SettingSwitchRow("Границы государств на карте", settings.showTerritoryBorders) {
        onUpdateSettings(settings.copy(showTerritoryBorders = it))
      }
      SettingSwitchRow("Отображение мини-карты", settings.showMinimap) {
        onUpdateSettings(settings.copy(showMinimap = it))
      }
      SettingSwitchRow("Автосохранение мира (45 сек)", settings.autosaveEnabled) {
        onUpdateSettings(settings.copy(autosaveEnabled = it))
      }

      Text("Качество графики", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GraphicsQuality.entries.forEach { q ->
          PresetChip(q.titleRu, settings.graphicsQuality == q, Modifier.weight(1f)) {
            onUpdateSettings(settings.copy(graphicsQuality = q))
          }
        }
      }

      Text("Качество частиц", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ParticleQuality.entries.forEach { pq ->
          PresetChip(pq.titleRu, settings.particleQuality == pq, Modifier.weight(1f)) {
            onUpdateSettings(settings.copy(particleQuality = pq))
          }
        }
      }

      Text("Частота обновления симуляции", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SimTickRate.entries.forEach { rate ->
          PresetChip(rate.titleRu, settings.simTickRate == rate, Modifier.weight(1f)) {
            onUpdateSettings(settings.copy(simTickRate = rate))
          }
        }
      }
    }
  }
}

@Composable
fun NewsHistoryDialog(
  uiState: WorldUiState,
  onDismiss: () -> Unit,
  onJumpToLocation: (Int, Int) -> Unit,
) {
  var filter by remember { mutableStateOf<WorldEventCategory?>(null) }

  DialogShell(title = "Журнал новостей мира", onDismiss = onDismiss) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        PresetChip("Все", filter == null) { filter = null }
        PresetChip("Войны", filter == WorldEventCategory.WAR) { filter = WorldEventCategory.WAR }
        PresetChip("ПВО", filter == WorldEventCategory.PVO) { filter = WorldEventCategory.PVO }
        PresetChip("Тревоги", filter == WorldEventCategory.AIR_ALERT) { filter = WorldEventCategory.AIR_ALERT }
        PresetChip("Города", filter == WorldEventCategory.CITY) { filter = WorldEventCategory.CITY }
      }

      val filtered = uiState.newsLog.filter { filter == null || it.category == filter }
      if (filtered.isEmpty()) {
        Text("Событий пока нет.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(filtered, key = { it.id }) { item ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(HudSurfaceElevated)
                  .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
                  .clickable {
                    onJumpToLocation(item.x, item.y)
                    onDismiss()
                  }
                  .padding(10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Box(
                modifier =
                  Modifier.padding(top = 4.dp)
                    .size(10.dp)
                    .background(Color(item.nationColorArgb), RoundedCornerShape(2.dp))
              )
              Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                  Text("${item.year}.${item.month.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
                }
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SaveLoadDialog(
  summaries: List<WorldSaveSummary>,
  onSaveSlot: (String, String) -> Unit,
  onLoadSlot: (String, String) -> Unit,
  onDeleteSlot: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val slots =
    listOf(
      "autosave" to "Автосохранение",
      "slot_1" to "Слот сохранения 1",
      "slot_2" to "Слот сохранения 2",
      "slot_3" to "Слот сохранения 3",
    )
  val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

  DialogShell(title = "Сохранение и загрузка", onDismiss = onDismiss) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      slots.forEach { (slotId, title) ->
        val existing = summaries.find { it.slotId == slotId }
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(HudSurfaceElevated)
              .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
              .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (existing != null) {
              Text(
                "Год ${existing.year} • ${existing.mapSize}×${existing.mapSize} • Насел: ${existing.population} • ${fmt.format(Date(existing.savedAtMillis))}",
                style = MaterialTheme.typography.labelSmall,
                color = TacticalCyan,
              )
            } else {
              Text("Пустой слот", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (slotId != "autosave") {
              IconButton(
                onClick = { onSaveSlot(slotId, title) },
                modifier = Modifier.size(36.dp).testTag("save_$slotId"),
              ) {
                Icon(Icons.Default.Save, contentDescription = "Сохранить", tint = LifeEmerald)
              }
            }
            if (existing != null) {
              IconButton(
                onClick = { onLoadSlot(slotId, title) },
                modifier = Modifier.size(36.dp).testTag("load_$slotId"),
              ) {
                Icon(Icons.Default.Download, contentDescription = "Загрузить", tint = TacticalCyan)
              }
              IconButton(
                onClick = { onDeleteSlot(slotId) },
                modifier = Modifier.size(36.dp).testTag("delete_$slotId"),
              ) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = WarCrimson)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(HudSurfaceElevated)
        .padding(horizontal = 10.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = DeepSpaceBg, checkedTrackColor = TacticalCyan),
    )
  }
}

@Composable
private fun DialogShell(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.widthIn(max = 480.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(HudSurfaceDark)
          .border(1.5.dp, HudBorderBright, RoundedCornerShape(14.dp))
          .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).testTag("close_dialog_button")) {
          Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextSecondary)
        }
      }
      content()
    }
  }
}

@Composable
private fun SliderSetting(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Column {
    Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = range,
      colors = SliderDefaults.colors(thumbColor = TacticalCyan, activeTrackColor = TacticalCyan),
    )
  }
}

@Composable
private fun PresetChip(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      modifier
        .height(34.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(if (selected) TacticalCyan.copy(alpha = 0.22f) else HudSurfaceElevated)
        .border(1.dp, if (selected) TacticalCyan else HudBorder, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) TacticalCyan else TextPrimary)
  }
}

@Composable
private fun StatKpiCard(
  label: String,
  value: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(HudSurfaceElevated)
        .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
        .padding(6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(value, style = MaterialTheme.typography.titleSmall, color = color)
    Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
  }
}
