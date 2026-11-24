package com.example.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.NameGenerator
import com.example.model.GodTool
import com.example.model.ToolCategory
import com.example.ui.WorldUiState
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurfaceDark
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudSurfaceHighlight
import com.example.ui.theme.LifeEmerald
import com.example.ui.theme.RoyalViolet
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarCrimson

@Composable
fun GodToolbar(
  uiState: WorldUiState,
  onSelectCategory: (ToolCategory) -> Unit,
  onSelectTool: (GodTool) -> Unit,
  onSetBrushRadius: (Int) -> Unit,
  onSelectNationColor: (Int) -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier =
          Modifier.weight(1f, fill = false)
            .clip(RoundedCornerShape(8.dp))
            .background(HudSurfaceDark.copy(alpha = 0.88f))
            .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        if (uiState.selectedTool.usesBrush) {
          Text("Кисть:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          listOf(0 to "1×1", 1 to "3×3", 2 to "5×5", 4 to "9×9").forEach { (rad, label) ->
            val sel = uiState.brushRadius == rad
            Box(
              modifier =
                Modifier.height(28.dp)
                  .clip(RoundedCornerShape(5.dp))
                  .background(if (sel) TacticalCyan.copy(alpha = 0.25f) else HudSurfaceElevated)
                  .border(1.dp, if (sel) TacticalCyan else HudBorder, RoundedCornerShape(5.dp))
                  .clickable { onSetBrushRadius(rad) }
                  .padding(horizontal = 8.dp)
                  .testTag("brush_size_$rad"),
              contentAlignment = Alignment.Center,
            ) {
              Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) TacticalCyan else TextPrimary)
            }
          }
        }

        if (uiState.selectedCategory == ToolCategory.NATIONS || uiState.selectedCategory == ToolCategory.MILITARY) {
          if (uiState.selectedTool.usesBrush) Spacer(Modifier.width(4.dp))
          Text("Цвет:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          NameGenerator.nationColors.forEachIndexed { idx, argb ->
            val sel = uiState.selectedNationColor == argb
            Box(
              modifier =
                Modifier.size(24.dp)
                  .clip(CircleShape)
                  .background(Color(argb))
                  .border(if (sel) 2.5.dp else 1.dp, if (sel) Color.White else HudBorder, CircleShape)
                  .clickable { onSelectNationColor(argb) }
                  .testTag("nation_color_$idx")
            )
          }
        }

        if (!uiState.selectedTool.usesBrush &&
          uiState.selectedCategory != ToolCategory.NATIONS &&
          uiState.selectedCategory != ToolCategory.MILITARY
        ) {
          Text(
            text =
              if (uiState.isPanInspectMode) "Режим обзора: нажмите на объект"
              else "Инструмент: ${uiState.selectedTool.titleRu}",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
          )
        }
      }

      Spacer(Modifier.width(8.dp))

      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(8.dp))
            .background(HudSurfaceDark.copy(alpha = 0.9f))
            .border(1.dp, HudBorder, RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(HudSurfaceElevated)
              .clickable(onClick = onZoomOut)
              .testTag("zoom_out_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.Remove, contentDescription = "Отдалить", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
        Box(
          modifier =
            Modifier.size(34.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(HudSurfaceElevated)
              .clickable(onClick = onZoomIn)
              .testTag("zoom_in_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.Add, contentDescription = "Приблизить", tint = TextPrimary, modifier = Modifier.size(18.dp))
        }
      }
    }

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(HudSurfaceDark.copy(alpha = 0.94f))
          .border(1.5.dp, HudBorder, RoundedCornerShape(12.dp))
          .padding(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val tools = GodTool.entries.filter { it.category == uiState.selectedCategory }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        tools.forEach { tool ->
          val selected = !uiState.isPanInspectMode && uiState.selectedTool == tool
          val accent = categoryAccent(tool.category)
          Column(
            modifier =
              Modifier.width(70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) accent.copy(alpha = 0.22f) else HudSurfaceElevated)
                .border(1.5.dp, if (selected) accent else HudBorder, RoundedCornerShape(8.dp))
                .clickable { onSelectTool(tool) }
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .testTag("tool_${tool.name.lowercase()}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Icon(
              imageVector = toolIcon(tool),
              contentDescription = tool.titleRu,
              tint = if (selected) accent else TextPrimary,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = tool.shortLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) accent else TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // 7 Scrollable Categories: Мир, Жизнь, Армия, Воздух, Государства, События, Разрушения
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        ToolCategory.entries.forEach { category ->
          val selected = uiState.selectedCategory == category
          val accent = categoryAccent(category)
          Row(
            modifier =
              Modifier.height(38.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) HudSurfaceHighlight else HudSurfaceElevated.copy(alpha = 0.7f))
                .border(1.dp, if (selected) accent else HudBorder, RoundedCornerShape(7.dp))
                .clickable { onSelectCategory(category) }
                .padding(horizontal = 10.dp)
                .testTag("category_${category.name.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = categoryIcon(category),
              contentDescription = category.titleRu,
              tint = if (selected) accent else TextSecondary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = category.titleRu,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) TextPrimary else TextSecondary,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

private fun categoryAccent(category: ToolCategory): Color =
  when (category) {
    ToolCategory.WORLD -> LifeEmerald
    ToolCategory.LIFE -> TacticalCyan
    ToolCategory.MILITARY -> Color(0xFFF97316)
    ToolCategory.AIR_EVENTS -> Color(0xFF38BDF8)
    ToolCategory.NATIONS -> RoyalViolet
    ToolCategory.EVENTS -> TacticalAmber
    ToolCategory.DESTRUCTION -> WarCrimson
  }

private fun categoryIcon(category: ToolCategory): ImageVector =
  when (category) {
    ToolCategory.WORLD -> Icons.Default.Public
    ToolCategory.LIFE -> Icons.Default.GroupAdd
    ToolCategory.MILITARY -> Icons.Default.MilitaryTech
    ToolCategory.AIR_EVENTS -> Icons.Default.Flight
    ToolCategory.NATIONS -> Icons.Default.Flag
    ToolCategory.EVENTS -> Icons.Default.Bolt
    ToolCategory.DESTRUCTION -> Icons.Default.LocalFireDepartment
  }

private fun toolIcon(tool: GodTool): ImageVector =
  when (tool) {
    GodTool.PAINT_GRASS -> Icons.Default.Landscape
    GodTool.PAINT_WATER -> Icons.Default.WaterDrop
    GodTool.PAINT_FOREST -> Icons.Default.Forest
    GodTool.PAINT_MOUNTAIN -> Icons.Default.Terrain
    GodTool.PAINT_SAND -> Icons.Default.Waves
    GodTool.PAINT_ROAD -> Icons.Default.EditRoad
    GodTool.ERASE_TERRAIN -> Icons.Default.DeleteSweep
    GodTool.SPAWN_CITIZENS -> Icons.Default.GroupAdd
    GodTool.SPAWN_ANIMALS -> Icons.Default.Pets
    GodTool.FOUND_CITY -> Icons.Default.LocationCity
    GodTool.FOUND_SETTLEMENT -> Icons.Default.HomeWork
    GodTool.SPAWN_INFANTRY -> Icons.Default.Shield
    GodTool.SPAWN_ARMOR -> Icons.Default.DirectionsCar
    GodTool.BUILD_MILITARY_BASE -> Icons.Default.MilitaryTech
    GodTool.BUILD_PVO_RADAR -> Icons.Default.Radar
    GodTool.TOGGLE_GLOBAL_WARS -> Icons.Default.Security
    GodTool.TRIGGER_WAR_EVENT -> Icons.Default.Gavel
    GodTool.BUILD_EW_STATION -> Icons.Default.SettingsInputAntenna
    GodTool.TRIGGER_AIR_ALERT -> Icons.Default.NotificationsActive
    GodTool.SPAWN_RECON_DRONE -> Icons.Default.FlightTakeoff
    GodTool.SPAWN_STRIKE_DRONE -> Icons.Default.Flight
    GodTool.TRIGGER_ROCKET_EVENT -> Icons.Default.RocketLaunch
    GodTool.SPAWN_JET_PATROL -> Icons.Default.AirplanemodeActive
    GodTool.SPAWN_PVO_INTERCEPTOR -> Icons.Default.Radar
    GodTool.SPAWN_MILITARY_UAV -> Icons.Default.FlightTakeoff
    GodTool.SPAWN_HEAVY_BOMBER -> Icons.Default.AirplanemodeActive
    GodTool.CREATE_NATION -> Icons.Default.Flag
    GodTool.PAINT_TERRITORY -> Icons.Default.Public
    GodTool.MERGE_NATIONS -> Icons.Default.MergeType
    GodTool.SPLIT_NATION -> Icons.Default.CallSplit
    GodTool.TOGGLE_NATION_WAR -> Icons.Default.Gavel
    GodTool.CYCLE_DIPLOMACY -> Icons.Default.Handshake
    GodTool.TRIGGER_EXPLOSION -> Icons.Default.Flare
    GodTool.CYCLE_WEATHER -> Icons.Default.Cloud
    GodTool.RANDOM_EVENT -> Icons.Default.Bolt
    GodTool.EMP_STRIKE -> Icons.Default.Bolt
    GodTool.REMOVE_BUILDINGS -> Icons.Default.DeleteSweep
    GodTool.CREATE_RUINS -> Icons.Default.BrokenImage
    GodTool.IGNITE_FIRE -> Icons.Default.LocalFireDepartment
    GodTool.DROP_METEOR -> Icons.Default.WbTwilight
    GodTool.RESTORE_AREA -> Icons.Default.AutoFixHigh
    GodTool.NUKE_STRIKE -> Icons.Default.Dangerous
  }
