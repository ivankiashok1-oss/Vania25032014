package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
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
import com.example.model.WeatherType
import com.example.ui.ActiveDialog
import com.example.ui.WorldUiState
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurfaceDark
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudSurfaceHighlight
import com.example.ui.theme.LifeEmerald
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarCrimson

@Composable
fun TopHudBar(
  uiState: WorldUiState,
  onSetSpeed: (Int) -> Unit,
  onToggleWars: () -> Unit,
  onTogglePanInspect: () -> Unit,
  onToggleSound: () -> Unit,
  onToggleBorders: () -> Unit,
  onToggleMinimap: () -> Unit,
  onCenterCamera: () -> Unit,
  onOpenDialog: (ActiveDialog) -> Unit,
  onJumpToNews: (Int, Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(HudSurfaceDark.copy(alpha = 0.92f))
          .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(HudSurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = if (uiState.isNight) Icons.Default.NightsStay else Icons.Default.WbSunny,
          contentDescription = if (uiState.isNight) "Ночь" else "День",
          tint = if (uiState.isNight) TacticalCyan else TacticalAmber,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "${uiState.year}.${uiState.month.toString().padStart(2, '0')}",
          style = MaterialTheme.typography.labelMedium,
          color = TextPrimary,
        )
        Text(
          text = "• ${weatherShort(uiState.weather)}",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
      }

      SpeedChip("II", uiState.speedMultiplier == 0, TacticalAmber, "speed_pause") { onSetSpeed(0) }
      SpeedChip("x1", uiState.speedMultiplier == 1, TacticalCyan, "speed_1x") { onSetSpeed(1) }
      SpeedChip("x2", uiState.speedMultiplier == 2, TacticalCyan, "speed_2x") { onSetSpeed(2) }
      SpeedChip("x5", uiState.speedMultiplier == 5, LifeEmerald, "speed_5x") { onSetSpeed(5) }

      Spacer(Modifier.width(2.dp))

      HudActionChip(
        icon = if (uiState.isPanInspectMode) Icons.Default.OpenWith else Icons.Default.Brush,
        label = if (uiState.isPanInspectMode) "Обзор" else "Кисть",
        active = uiState.isPanInspectMode,
        activeColor = TacticalCyan,
        tag = "mode_toggle_button",
        onClick = onTogglePanInspect,
      )

      HudActionChip(
        icon = if (uiState.warsEnabled) Icons.Default.Security else Icons.Default.Shield,
        label = if (uiState.warsEnabled) "Войны ВКЛ" else "Мир",
        active = uiState.warsEnabled,
        activeColor = WarCrimson,
        tag = "wars_toggle_button",
        onClick = onToggleWars,
      )

      HudIconChip(Icons.Default.Gavel, "Военные конфликты", "conflicts_button", uiState.activeWarsCount > 0) {
        onOpenDialog(ActiveDialog.CONFLICTS)
      }
      HudIconChip(Icons.Default.BarChart, "Статистика мира", "stats_button") { onOpenDialog(ActiveDialog.STATS) }
      HudIconChip(Icons.Default.Newspaper, "Журнал новостей", "news_button") { onOpenDialog(ActiveDialog.NEWS) }
      HudIconChip(Icons.Default.Save, "Сохранения", "save_menu_button") { onOpenDialog(ActiveDialog.SAVES) }
      HudIconChip(Icons.Default.Public, "Новый мир", "new_world_button") { onOpenDialog(ActiveDialog.NEW_WORLD) }
      HudIconChip(Icons.Default.Settings, "Настройки", "settings_button") { onOpenDialog(ActiveDialog.SETTINGS) }
      HudIconChip(Icons.Default.CenterFocusStrong, "Центр мира", "center_camera_button", onClick = onCenterCamera)
      HudIconChip(Icons.Default.BorderOuter, "Границы государств", "borders_toggle_button", uiState.showTerritoryBorders, onToggleBorders)
      HudIconChip(Icons.Default.Map, "Мини-карта", "minimap_toggle_button", uiState.showMinimap, onToggleMinimap)
      HudIconChip(
        icon = if (uiState.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
        contentDesc = "Звук",
        tag = "sound_toggle_button",
        active = uiState.soundEnabled,
        onClick = onToggleSound,
      )
    }

    AnimatedVisibility(visible = uiState.activeAirAlerts.isNotEmpty()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xDC7F1D1D))
            .border(1.dp, WarCrimson, RoundedCornerShape(8.dp))
            .clickable { onOpenDialog(ActiveDialog.NEWS) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(Icons.Default.Warning, contentDescription = "Тревога", tint = TacticalAmber, modifier = Modifier.size(18.dp))
        Text(
          text = "ВОЗДУШНАЯ ТРЕВОГА: ${uiState.activeAirAlerts.joinToString(", ")}",
          style = MaterialTheme.typography.labelLarge,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val news = uiState.latestNews
      if (news != null) {
        Row(
          modifier =
            Modifier.weight(1f, fill = false)
              .clip(RoundedCornerShape(6.dp))
              .background(HudSurfaceDark.copy(alpha = 0.82f))
              .border(1.dp, HudBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
              .clickable { onJumpToNews(news.x, news.y) }
              .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier.size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(news.nationColorArgb))
          )
          Text(
            text = "${news.title}: ${news.description}",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Spacer(Modifier.width(8.dp))

      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(HudSurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, HudBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .clickable { onOpenDialog(ActiveDialog.CONFLICTS) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Насел: ${uiState.population}", style = MaterialTheme.typography.labelSmall, color = LifeEmerald)
        Text("Армии: ${uiState.militaryUnitsCount}", style = MaterialTheme.typography.labelSmall, color = TacticalAmber)
        Text("Стран: ${uiState.nationCount}", style = MaterialTheme.typography.labelSmall, color = TacticalCyan)
        if (uiState.activeWarsCount > 0) {
          Text("Войн: ${uiState.activeWarsCount}", style = MaterialTheme.typography.labelSmall, color = WarCrimson)
        }
      }
    }
  }
}

@Composable
private fun SpeedChip(
  label: String,
  selected: Boolean,
  accent: Color,
  tag: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.height(34.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(if (selected) accent.copy(alpha = 0.24f) else HudSurfaceElevated)
        .border(1.dp, if (selected) accent else HudBorder, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp)
        .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    if (label == "II") {
      Icon(
        Icons.Default.Pause,
        contentDescription = "Пауза",
        tint = if (selected) accent else TextSecondary,
        modifier = Modifier.size(16.dp),
      )
    } else {
      Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) accent else TextPrimary)
    }
  }
}

@Composable
private fun HudActionChip(
  icon: ImageVector,
  label: String,
  active: Boolean,
  activeColor: Color,
  tag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.height(34.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(if (active) activeColor.copy(alpha = 0.22f) else HudSurfaceElevated)
        .border(1.dp, if (active) activeColor else HudBorder, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 9.dp)
        .testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Icon(icon, contentDescription = label, tint = if (active) activeColor else TextPrimary, modifier = Modifier.size(15.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) activeColor else TextPrimary)
  }
}

@Composable
private fun HudIconChip(
  icon: ImageVector,
  contentDesc: String,
  tag: String,
  active: Boolean = false,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.size(34.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(if (active) HudSurfaceHighlight else HudSurfaceElevated)
        .border(1.dp, if (active) TacticalCyan else HudBorder, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick)
        .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = contentDesc, tint = if (active) TacticalCyan else TextPrimary, modifier = Modifier.size(18.dp))
  }
}

private fun weatherShort(w: WeatherType): String =
  when (w) {
    WeatherType.CLEAR -> "Ясно"
    WeatherType.RAIN -> "Дождь"
    WeatherType.FOG -> "Туман"
    WeatherType.STORM -> "Гроза"
  }
