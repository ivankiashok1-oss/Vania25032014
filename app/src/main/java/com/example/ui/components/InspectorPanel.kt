package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.BuildingType
import com.example.model.RelationType
import com.example.model.SelectionTarget
import com.example.ui.RenderFrame
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudSurfaceDark
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.LifeEmerald
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarCrimson

@Composable
fun InspectorPanel(
  selection: SelectionTarget,
  frame: RenderFrame,
  onClose: () -> Unit,
  onFocusTile: (Float, Float) -> Unit,
  onTriggerCityAlert: (Int) -> Unit,
  onToggleWar: (Int, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (selection is SelectionTarget.None) return

  Column(
    modifier =
      modifier
        .widthIn(max = 380.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(HudSurfaceDark.copy(alpha = 0.95f))
        .border(1.5.dp, HudBorderBright, RoundedCornerShape(12.dp))
        .padding(12.dp)
        .testTag("inspector_panel"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when (selection) {
      is SelectionTarget.MilitaryUnitInfo -> {
        val u = frame.militaryUnits.find { it.id == selection.unitId }
        if (u == null) {
          InspectorHeader("Подразделение расформировано", onClose)
        } else {
          val nation = frame.nations.find { it.id == u.nationId }
          InspectorHeader(u.type.titleRu, onClose)
          Text(
            "Государство: ${nation?.name ?: "Нет"} • Приказ: ${u.order.titleRu}",
            style = MaterialTheme.typography.bodySmall,
            color = TacticalAmber,
          )
          Text(
            "Боеспособность: ${u.hp}/${u.maxHp} • Сила: ${u.strength}",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
          )
          InspectorButton("Наблюдать", Icons.Default.CenterFocusStrong, TacticalCyan) {
            onFocusTile(u.x, u.y)
          }
        }
      }

      is SelectionTarget.CitizenInfo -> {
        val c = frame.citizens.find { it.id == selection.citizenId }
        if (c == null) {
          InspectorHeader("Житель покинул мир", onClose)
        } else {
          val nation = frame.nations.find { it.id == c.nationId }
          val city = frame.cities.find { it.id == c.cityId }
          InspectorHeader(c.name, onClose)
          Text(
            "Профессия: ${c.profession.titleRu} • Возраст: ${c.age}",
            style = MaterialTheme.typography.bodyMedium,
            color = TacticalCyan,
          )
          Text(
            "Состояние: ${c.state.titleRu} • Здоровье: ${c.hp}/${c.maxHp}",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
          )
          Text(
            "Город: ${city?.name ?: "Кочевник"} • Гос-во: ${nation?.name ?: "Нет"}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          InspectorButton("Наблюдать", Icons.Default.CenterFocusStrong, TacticalCyan) {
            onFocusTile(c.x, c.y)
          }
        }
      }

      is SelectionTarget.CityInfo -> {
        val city = frame.cities.find { it.id == selection.cityId }
        if (city == null) {
          InspectorHeader("Город разрушен", onClose)
        } else {
          val nation = frame.nations.find { it.id == city.nationId }
          val pop = frame.citizens.count { it.cityId == city.id }
          val bCount = frame.buildings.count { it.cityId == city.id }
          val radars = frame.buildings.count { it.cityId == city.id && it.type == BuildingType.RADAR }
          val ewStations = frame.buildings.count { it.cityId == city.id && it.type == BuildingType.EW_STATION }
          val shelters = frame.buildings.count { it.cityId == city.id && it.type == BuildingType.SHELTER }
          val bases = frame.buildings.count { it.cityId == city.id && it.type == BuildingType.MILITARY_BASE }
          val garrison = frame.militaryUnits.count { it.cityId == city.id }
          val defense = (radars * 25 + ewStations * 20 + shelters * 15 + bases * 30 + garrison * 10).coerceIn(0, 100)
          InspectorHeader("${if (city.isCapital) "★ " else ""}${city.name}", onClose)
          Text(
            "Государство: ${nation?.name ?: "Независимый"} • Основан: ${city.foundedYear}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Насел: $pop", style = MaterialTheme.typography.labelMedium, color = LifeEmerald)
            Text("Здания: $bCount", style = MaterialTheme.typography.labelMedium, color = TacticalCyan)
            Text("ПВО/Защита: $defense%", style = MaterialTheme.typography.labelMedium, color = TacticalAmber)
          }
          if (city.blackoutTimer > 0f) {
            Text("⚡ БЛЭКАУТ — электросети отключены!", style = MaterialTheme.typography.labelMedium, color = TacticalAmber)
          }
          if (city.underAirAlert) {
            Text("АКТИВНА ВОЗДУШНАЯ ТРЕВОГА!", style = MaterialTheme.typography.labelMedium, color = WarCrimson)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InspectorButton("Камера", Icons.Default.CenterFocusStrong, TacticalCyan) {
              onFocusTile(city.centerX.toFloat(), city.centerY.toFloat())
            }
            InspectorButton("Тревога", Icons.Default.NotificationsActive, WarCrimson) {
              onTriggerCityAlert(city.id)
            }
          }
        }
      }

      is SelectionTarget.NationInfo -> {
        val nation = frame.nations.find { it.id == selection.nationId }
        if (nation == null) {
          InspectorHeader("Государство не найдено", onClose)
        } else {
          val cities = frame.cities.filter { it.nationId == nation.id }
          val pop = frame.citizens.count { it.nationId == nation.id }
          val totalUnits = frame.militaryUnits.count { it.nationId == nation.id }
          val armorCount = frame.militaryUnits.count { it.nationId == nation.id && it.type.isArmor }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              PixelFlagView(nation.flag)
              Column {
                Text(nation.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                  "Городов: ${cities.size} • Насел: $pop • ПВО: ${nation.airDefenseIntercepts}",
                  style = MaterialTheme.typography.labelSmall,
                  color = TextSecondary,
                )
                Text(
                  "Тех. ур.: ${nation.techLevel} • Стаб: ${(nation.stability * 100).toInt()}%",
                  style = MaterialTheme.typography.labelSmall,
                  color = TacticalCyan,
                )
              }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextSecondary)
            }
          }

          // Military Stats Box
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(HudSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Армия: $totalUnits (Тех: $armorCount)", style = MaterialTheme.typography.labelSmall, color = TacticalAmber)
            Text("Побед: ${nation.wins}/${nation.losses}", style = MaterialTheme.typography.labelSmall, color = LifeEmerald)
            Text("Потерь: ${nation.totalCasualties}", style = MaterialTheme.typography.labelSmall, color = WarCrimson)
            Text("Захв: ${nation.territoriesCaptured}", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
          }

          // Resources
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(HudSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Еда: ${nation.food}", style = MaterialTheme.typography.labelSmall, color = LifeEmerald)
            Text("Мат: ${nation.materials}", style = MaterialTheme.typography.labelSmall, color = TacticalAmber)
            Text("Энерг: ${nation.energy}", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
            Text("Казна: ${nation.treasury}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
          }

          val others = frame.nations.filter { it.id != nation.id }
          if (others.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Дипломатия (нажмите для войны/мира):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
              Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                others.forEach { other ->
                  val rel = nation.relations[other.id] ?: RelationType.NEUTRAL
                  val relColor =
                    when (rel) {
                      RelationType.ALLIANCE -> LifeEmerald
                      RelationType.TRADE -> TacticalCyan
                      RelationType.NEUTRAL -> TextSecondary
                      RelationType.TENSION -> TacticalAmber
                      RelationType.WAR -> WarCrimson
                    }
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(HudSurfaceElevated)
                        .clickable { onToggleWar(nation.id, other.id) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Box(Modifier.size(8.dp).background(Color(other.colorArgb), RoundedCornerShape(2.dp)))
                      Text(other.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                      Text(rel.titleRu, style = MaterialTheme.typography.labelSmall, color = relColor)
                      Icon(Icons.Default.Gavel, contentDescription = "Изменить", tint = relColor, modifier = Modifier.size(13.dp))
                    }
                  }
                }
              }
            }
          }

          val cap = cities.firstOrNull { it.isCapital } ?: cities.firstOrNull()
          if (cap != null) {
            InspectorButton("Наблюдать за столицей", Icons.Default.CenterFocusStrong, TacticalCyan) {
              onFocusTile(cap.centerX.toFloat(), cap.centerY.toFloat())
            }
          }
        }
      }

      is SelectionTarget.TileInfo -> {
        InspectorHeader("Сектор (${selection.x}, ${selection.y})", onClose)
        Text(
          "Местность: ${selection.terrain.titleRu}",
          style = MaterialTheme.typography.bodyMedium,
          color = LifeEmerald,
        )
        if (selection.building != null) {
          Text(
            "Объект: ${selection.building.type.titleRu} (HP ${selection.building.hp}/${selection.building.maxHp})",
            style = MaterialTheme.typography.bodySmall,
            color = TacticalCyan,
          )
        }
        if (selection.fireLevel > 0) {
          Text("Активный пожар!", style = MaterialTheme.typography.labelMedium, color = WarCrimson)
        }
      }

      SelectionTarget.None -> Unit
    }
  }
}

@Composable
private fun InspectorHeader(title: String, onClose: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    IconButton(onClick = onClose, modifier = Modifier.size(32.dp).testTag("close_inspector_button")) {
      Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextSecondary)
    }
  }
}

@Composable
private fun InspectorButton(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: Color,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.height(34.dp)
        .clip(RoundedCornerShape(7.dp))
        .background(color.copy(alpha = 0.18f))
        .border(1.dp, color, RoundedCornerShape(7.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
  }
}
