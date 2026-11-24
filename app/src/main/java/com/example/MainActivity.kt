package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.SelectionTarget
import com.example.ui.ActiveDialog
import com.example.ui.WorldViewModel
import com.example.ui.components.ConflictsDialog
import com.example.ui.components.GodToolbar
import com.example.ui.components.InspectorPanel
import com.example.ui.components.MinimapWidget
import com.example.ui.components.NewWorldDialog
import com.example.ui.components.NewsHistoryDialog
import com.example.ui.components.SaveLoadDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TopHudBar
import com.example.ui.components.WorldCanvasView
import com.example.ui.components.WorldStatsDialog
import com.example.ui.theme.HudSurfaceDark
import com.example.ui.theme.PixelWorldTheme
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PixelWorldTheme {
        PixelWorldGameScreen()
      }
    }
  }
}

@Composable
fun PixelWorldGameScreen(viewModel: WorldViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val frame by viewModel.renderFrame.collectAsStateWithLifecycle()
  val focusRequest by viewModel.cameraFocusRequest.collectAsStateWithLifecycle()
  val saveSummaries by viewModel.saveSummaries.collectAsStateWithLifecycle()

  var zoomCommand by remember { mutableIntStateOf(0) }
  var viewLeft by remember { mutableFloatStateOf(0f) }
  var viewTop by remember { mutableFloatStateOf(0f) }
  var viewRight by remember { mutableFloatStateOf(104f) }
  var viewBottom by remember { mutableFloatStateOf(104f) }

  LaunchedEffect(uiState.statusBannerMessage) {
    if (uiState.statusBannerMessage != null) {
      delay(2600L)
      viewModel.clearStatusBanner()
    }
  }

  BackHandler(enabled = uiState.activeDialog != ActiveDialog.NONE || uiState.selection != SelectionTarget.None) {
    if (uiState.activeDialog != ActiveDialog.NONE) {
      viewModel.openDialog(ActiveDialog.NONE)
    } else {
      viewModel.clearSelection()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 1. Full-bleed Interactive 16-bit World Canvas
    WorldCanvasView(
      frame = frame,
      isPanInspectMode = uiState.isPanInspectMode,
      brushRadius = uiState.brushRadius,
      usesBrush = uiState.selectedTool.usesBrush,
      zoomCommand = zoomCommand,
      cameraFocusRequest = focusRequest,
      onCameraFocused = { viewModel.clearCameraFocusRequest() },
      onViewportChanged = { l, t, r, b ->
        viewLeft = l
        viewTop = t
        viewRight = r
        viewBottom = b
      },
      onMapTapOrPaint = { tx, ty, isDrag ->
        viewModel.onMapTapOrPaint(tx, ty, isDrag)
      },
      modifier = Modifier.fillMaxSize(),
    )

    // 2. Top Tactical HUD + Floating Minimap
    Column(
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding(),
      horizontalAlignment = Alignment.End,
    ) {
      TopHudBar(
        uiState = uiState,
        onSetSpeed = viewModel::setSpeed,
        onToggleWars = viewModel::toggleWarsEnabled,
        onTogglePanInspect = viewModel::togglePanInspectMode,
        onToggleSound = viewModel::toggleSound,
        onToggleBorders = viewModel::toggleTerritoryBorders,
        onToggleMinimap = viewModel::toggleMinimap,
        onCenterCamera = viewModel::centerCamera,
        onOpenDialog = viewModel::openDialog,
        onJumpToNews = { x, y -> viewModel.focusCameraOn(x.toFloat(), y.toFloat()) },
      )

      AnimatedVisibility(visible = uiState.showMinimap) {
        MinimapWidget(
          minimapBitmap = frame.minimapBitmap,
          mapWidth = frame.mapWidth,
          mapHeight = frame.mapHeight,
          viewLeft = viewLeft,
          viewTop = viewTop,
          viewRight = viewRight,
          viewBottom = viewBottom,
          onJumpToTile = { tx, ty -> viewModel.focusCameraOn(tx, ty) },
          modifier = Modifier.padding(end = 10.dp, top = 2.dp),
        )
      }
    }

    // 3. Bottom Stack: Status Toast + Inspector Panel + God Toolbar
    Column(
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
      horizontalAlignment = Alignment.Start,
    ) {
      AnimatedVisibility(
        visible = uiState.statusBannerMessage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp),
      ) {
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(HudSurfaceDark.copy(alpha = 0.94f))
              .border(1.dp, TacticalCyan, RoundedCornerShape(8.dp))
              .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            text = uiState.statusBannerMessage.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
          )
        }
      }

      if (uiState.selection !is SelectionTarget.None) {
        InspectorPanel(
          selection = uiState.selection,
          frame = frame,
          onClose = viewModel::clearSelection,
          onFocusTile = viewModel::focusCameraOn,
          onTriggerCityAlert = viewModel::triggerCityAirAlertById,
          onToggleWar = viewModel::toggleDiplomaticWar,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }

      GodToolbar(
        uiState = uiState,
        onSelectCategory = viewModel::selectCategory,
        onSelectTool = viewModel::selectTool,
        onSetBrushRadius = viewModel::setBrushRadius,
        onSelectNationColor = viewModel::setNationColor,
        onZoomIn = { zoomCommand++ },
        onZoomOut = { zoomCommand-- },
      )
    }

    // 4. Interactive Modals
    when (uiState.activeDialog) {
      ActiveDialog.NEW_WORLD -> {
        NewWorldDialog(
          initialConfig = uiState.genConfig,
          onDismiss = { viewModel.openDialog(ActiveDialog.NONE) },
          onGenerate = viewModel::generateNewWorld,
        )
      }
      ActiveDialog.STATS -> {
        WorldStatsDialog(
          uiState = uiState,
          frame = frame,
          onDismiss = { viewModel.openDialog(ActiveDialog.NONE) },
          onSelectNation = viewModel::selectNation,
          onGenerateChronicle = viewModel::generateChronicle,
        )
      }
      ActiveDialog.CONFLICTS -> {
        ConflictsDialog(
          frame = frame,
          currentYear = uiState.year,
          currentMonth = uiState.month,
          onToggleWar = viewModel::toggleDiplomaticWar,
          onJumpToNation = viewModel::selectNation,
          onDismiss = { viewModel.openDialog(ActiveDialog.NONE) },
        )
      }
      ActiveDialog.SETTINGS -> {
        SettingsDialog(
          settings = uiState.settings,
          onUpdateSettings = viewModel::updateSettings,
          onDismiss = { viewModel.openDialog(ActiveDialog.NONE) },
        )
      }
      ActiveDialog.NEWS -> {
        NewsHistoryDialog(
          uiState = uiState,
          onDismiss = { viewModel.openDialog(ActiveDialog.NONE) },
          onJumpToLocation = { x, y -> viewModel.focusCameraOn(x.toFloat(), y.toFloat()) },
        )
      }
      ActiveDialog.SAVES -> {
        SaveLoadDialog(
          summaries = saveSummaries,
          onSaveSlot = viewModel::saveToSlot,
          onLoadSlot = viewModel::loadFromSlot,
          onDeleteSlot = viewModel::deleteSaveSlot,
          onDismiss = { viewModel.openDialog(ActiveDialog.NONE) },
        )
      }
      ActiveDialog.NONE -> Unit
    }
  }
}
