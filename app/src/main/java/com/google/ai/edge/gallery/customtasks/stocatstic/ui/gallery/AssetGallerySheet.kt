/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.gallery

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.customtasks.stocatstic.data.StocatsticPreferences
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.StocatsticViewModel
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.CharacterCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.CharacterEntry
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.GalleryEntry
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.PathCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.RootCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.SpriteAssets
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.TaskSpriteCatalog
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets.TaskSpriteRegistry
import com.google.ai.edge.gallery.customtasks.stocatstic.ui.theme.PixelPalette
import kotlinx.coroutines.delay

/**
 * Single entry point for the asset gallery. Presents four tabs (Personaje / Inicio / Camino /
 * Tareas). When [onboarding] is true the user MUST pick a character AND a path sprite before
 * the sheet can be dismissed.
 *
 * Features on top of raw catalog browsing:
 *   • A top-level search bar that filters every tab's content by label.
 *   • "Consumed" highlighting (orange border) on any asset already picked by ANOTHER task —
 *     tapping it opens a Toast pointing to the conflicting task instead of selecting it.
 *   • In the Tareas tab the preselected sprite of every capability auto-scrolls to the
 *     horizontal center of the row so the user sees the current default at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetGallerySheet(
  vm: StocatsticViewModel,
  onboarding: Boolean,
  onDismiss: () -> Unit,
) {
  val prefs by vm.preferences.collectAsState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val tabs = listOf("Personaje", "Inicio", "Camino", "Tareas")
  var tab by rememberSaveable { mutableStateOf(0) }
  var query by rememberSaveable { mutableStateOf("") }

  val canFinish = prefs.characterId.isNotBlank() && prefs.pathAssetId.isNotBlank()

  ModalBottomSheet(
    onDismissRequest = { if (!onboarding || canFinish) onDismiss() },
    sheetState = sheetState,
    containerColor = PixelPalette.deepSky,
    dragHandle = null,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            "Galería de assets",
            color = PixelPalette.onDark,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            if (onboarding) "Elige personaje y camino para empezar"
            else "Personaliza cómo se ven los flujos",
            color = PixelPalette.onDarkMuted,
            style = MaterialTheme.typography.bodySmall,
          )
        }
        if (!onboarding) {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted)
          }
        }
      }
      Spacer(Modifier.size(8.dp))
      TabRow(
        selectedTabIndex = tab,
        containerColor = PixelPalette.softGlow.copy(alpha = 0.4f),
        contentColor = PixelPalette.onDark,
      ) {
        tabs.forEachIndexed { i, t ->
          Tab(selected = tab == i, onClick = { tab = i }, text = {
            Text(t, fontSize = 13.sp, fontWeight = FontWeight.Medium)
          })
        }
      }
      Spacer(Modifier.size(10.dp))
      // --- Search bar (filters every tab) --------------------------------------------------
      GallerySearchBar(query = query, onQueryChange = { query = it })
      Spacer(Modifier.size(10.dp))
      Box(Modifier.heightIn(min = 240.dp, max = 520.dp)) {
        when (tab) {
          0 -> CharacterTab(prefs, query, onPick = { vm.preferencesStore.setCharacter(it.id) })
          1 -> AssetGrid(
            entries = RootCatalog.ENTRIES,
            selectedId = prefs.rootAssetId,
            query = query,
            consumedIds = emptyMap(), // root is a singleton: no cross-consumption conflict.
            onPick = { vm.preferencesStore.setRoot(it.id) },
          )
          2 -> AssetGrid(
            entries = PathCatalog.ENTRIES,
            selectedId = prefs.pathAssetId,
            query = query,
            consumedIds = emptyMap(),
            onPick = { vm.preferencesStore.setPath(it.id) },
          )
          else -> TasksTab(vm = vm, prefs = prefs, query = query)
        }
      }
      if (onboarding) {
        Spacer(Modifier.size(12.dp))
        Button(
          onClick = onDismiss,
          enabled = canFinish,
          colors = ButtonDefaults.buttonColors(containerColor = PixelPalette.catBlueDeep),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (canFinish) "¡Empezar!" else "Elige personaje y camino")
        }
      }
    }
  }
}

/** Reusable sticky search bar used at the top of the gallery. */
@Composable
private fun GallerySearchBar(query: String, onQueryChange: (String) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(PixelPalette.softGlow.copy(alpha = 0.65f))
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, null, tint = PixelPalette.onDarkMuted,
      modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Box(Modifier.weight(1f)) {
      if (query.isEmpty()) {
        Text(
          "Buscar asset…",
          color = PixelPalette.onDarkMuted,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        textStyle = TextStyle(color = PixelPalette.onDark, fontSize = 15.sp),
        cursorBrush = SolidColor(PixelPalette.moon),
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (query.isNotEmpty()) {
      IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(22.dp)) {
        Icon(Icons.Outlined.Close, null, tint = PixelPalette.onDarkMuted,
          modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun CharacterTab(
  prefs: StocatsticPreferences,
  query: String,
  onPick: (CharacterEntry) -> Unit,
) {
  val filtered = remember(query) {
    if (query.isBlank()) CharacterCatalog.ALL
    else CharacterCatalog.ALL.filter { it.label.contains(query, ignoreCase = true) }
  }
  LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    contentPadding = PaddingValues(6.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    gridItems(filtered, key = { it.id }) { ch ->
      val selected = ch.id == prefs.characterId
      Column(
        Modifier
          .clip(RoundedCornerShape(14.dp))
          .background(PixelPalette.softGlow.copy(alpha = if (selected) 0.9f else 0.45f))
          .border(
            width = if (selected) 2.dp else 0.dp,
            color = if (selected) PixelPalette.moon else PixelPalette.onDarkMuted,
            shape = RoundedCornerShape(14.dp),
          )
          .clickable { onPick(ch) }
          .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CharacterPreview(ch)
        Spacer(Modifier.size(6.dp))
        Text(
          ch.label, color = PixelPalette.onDark, fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CharacterPreview(ch: CharacterEntry) {
  val ctx = LocalContext.current
  val idleBmp = remember(ch.id) { SpriteAssets.load(ctx, ch.idle) }
  val runBmp = remember(ch.id) { SpriteAssets.load(ctx, ch.run) }
  val hoeBmp = remember(ch.id) { SpriteAssets.load(ctx, ch.hoe) }
  val waterBmp = remember(ch.id) { SpriteAssets.load(ctx, ch.water) }
  var tick by remember(ch.id) { mutableIntStateOf(0) }
  LaunchedEffect(ch.id) {
    while (true) {
      delay(130L)
      tick++
    }
  }
  data class PreviewStrip(val bmp: ImageBitmap?, val cols: Int, val durationTicks: Int)
  val strips = listOf(
    PreviewStrip(idleBmp, ch.idleCols, 8),
    PreviewStrip(runBmp, ch.runCols, 8),
    PreviewStrip(hoeBmp, ch.hoeCols, 8),
    PreviewStrip(waterBmp, ch.waterCols, 8),
  ).filter { it.bmp != null && it.cols > 0 }
  val totalTicks = strips.sumOf { it.durationTicks }.coerceAtLeast(1)
  val phase = tick % totalTicks
  var cumulative = 0
  val activeStrip = strips.firstOrNull { strip ->
    val match = phase < cumulative + strip.durationTicks
    if (!match) cumulative += strip.durationTicks
    match
  } ?: PreviewStrip(idleBmp, ch.idleCols, 1)
  val localTick = (phase - cumulative).coerceAtLeast(0)
  Box(
    Modifier
      .size(72.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(PixelPalette.deepSky),
    contentAlignment = Alignment.Center,
  ) {
    val bmp = activeStrip.bmp
    if (bmp != null) {
      val cols = activeStrip.cols.coerceAtLeast(1)
      val frameW = bmp.width / cols
      val frameH = bmp.height / 4
      val frame = when {
        cols <= 1 -> 0
        activeStrip === strips.firstOrNull() -> localTick.coerceAtMost(cols - 1)
        else -> (localTick % cols).coerceAtMost(cols - 1)
      }
      Image(
        painter = BitmapPainter(
          image = bmp,
          srcOffset = IntOffset(frame * frameW, 0),
          srcSize = IntSize(frameW, frameH),
          filterQuality = FilterQuality.None,
        ),
        contentDescription = ch.label,
        modifier = Modifier.size(60.dp),
        contentScale = ContentScale.Fit,
      )
    } else {
      Text(ch.label.first().toString(), color = PixelPalette.onDark, fontSize = 24.sp)
    }
  }
}

@Composable
private fun AssetGrid(
  entries: List<GalleryEntry>,
  selectedId: String,
  query: String,
  consumedIds: Map<String, String>,
  onPick: (GalleryEntry) -> Unit,
) {
  val ctx = LocalContext.current
  val visible = remember(entries) {
    entries.mapNotNull { e ->
      val bmp = SpriteAssets.load(ctx, e.assetPath) ?: return@mapNotNull null
      val preview = previewFor(e, bmp) ?: return@mapNotNull null
      Triple(e, bmp, preview)
    }
  }
  val filtered = remember(visible, query) {
    if (query.isBlank()) visible
    else visible.filter { it.first.label.contains(query, ignoreCase = true) }
  }
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    contentPadding = PaddingValues(6.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    gridItems(filtered, key = { it.first.id }) { (e, bmp, preview) ->
      val consumer = consumedIds[e.id]
      GalleryEntryCell(
        entry = e, bmp = bmp, preview = preview,
        selected = e.id == selectedId,
        consumerLabel = consumer,
        onClick = { onPick(e) },
      )
    }
  }
}

private data class CatalogPreview(
  val offset: IntOffset,
  val size: IntSize,
)

private fun previewFor(entry: GalleryEntry, bmp: ImageBitmap): CatalogPreview? {
  val tile = 16
  val startX = entry.col * tile
  val startY = entry.row * tile
  val width = entry.colSpan * tile
  val height = entry.rowSpan * tile
  if (startX < 0 || startY < 0 || width <= 0 || height <= 0) return null
  if (startX + width > bmp.width || startY + height > bmp.height) return null
  return CatalogPreview(IntOffset(startX, startY), IntSize(width, height))
}

@Composable
private fun GalleryEntryCell(
  entry: GalleryEntry,
  bmp: ImageBitmap,
  preview: CatalogPreview,
  selected: Boolean,
  consumerLabel: String? = null,
  onClick: () -> Unit,
) {
  val ctx = LocalContext.current
  val consumed = consumerLabel != null && !selected
  val borderColor: Color = when {
    selected -> PixelPalette.moon
    consumed -> PixelPalette.failure // distinctive "taken" tint.
    else -> PixelPalette.onDarkMuted
  }
  val borderWidth = when {
    selected -> 2.dp
    consumed -> 2.dp
    else -> 0.dp
  }
  val bg = when {
    selected -> PixelPalette.softGlow.copy(alpha = 0.9f)
    consumed -> PixelPalette.failure.copy(alpha = 0.22f)
    else -> PixelPalette.softGlow.copy(alpha = 0.4f)
  }
  Column(
    Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(bg)
      .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(12.dp))
      .clickable {
        if (consumed) {
          Toast.makeText(
            ctx,
            "Ese asset ya lo usa “$consumerLabel”. Cambia ese primero.",
            Toast.LENGTH_LONG,
          ).show()
        } else {
          onClick()
        }
      }
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier
        .size(56.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(PixelPalette.deepSky),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = BitmapPainter(
          image = bmp,
          srcOffset = preview.offset,
          srcSize = preview.size,
          filterQuality = FilterQuality.None,
        ),
        contentDescription = entry.label,
        modifier = Modifier.size(48.dp),
        contentScale = ContentScale.Fit,
      )
    }
    Spacer(Modifier.size(4.dp))
    Text(
      entry.label, color = PixelPalette.onDark, fontSize = 11.sp,
      fontWeight = FontWeight.Medium, maxLines = 1,
    )
    if (consumed) {
      Text(
        "En uso",
        color = PixelPalette.failure,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }
  }
}

/**
 * Per-capability sprite assignment. Shows a vertical list of every registered capability plus
 * a horizontal strip of [TaskSpriteCatalog] entries the user can tap to override the default
 * sprite. The currently selected entry for every capability auto-scrolls to the horizontal
 * centre of its row so the user immediately sees what's picked.
 */
@Composable
private fun TasksTab(
  vm: StocatsticViewModel,
  prefs: StocatsticPreferences,
  query: String,
) {
  val ctx = LocalContext.current
  val caps = remember { vm.registry.all() }
  val resolvedEntries = remember {
    TaskSpriteCatalog.ENTRIES.mapNotNull { e ->
      val bmp = SpriteAssets.load(ctx, e.assetPath) ?: return@mapNotNull null
      val preview = previewFor(e, bmp) ?: return@mapNotNull null
      Triple(e, bmp, preview)
    }
  }
  val filteredEntries = remember(resolvedEntries, query) {
    if (query.isBlank()) resolvedEntries
    else resolvedEntries.filter { it.first.label.contains(query, ignoreCase = true) }
  }
  val filteredCaps = remember(caps, query) {
    if (query.isBlank()) caps
    else caps.filter {
      it.label.contains(query, ignoreCase = true) ||
        it.category.label.contains(query, ignoreCase = true)
    }
  }
  LazyVerticalGrid(
    columns = GridCells.Fixed(1),
    contentPadding = PaddingValues(4.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    gridItems(filteredCaps, key = { it.id }) { cap ->
      val effectiveId = prefs.taskOverrides[cap.id] ?: TaskSpriteRegistry.defaultEntryId(cap.id)
      val consumed = remember(prefs.taskOverrides, cap.id) {
        // Collect consumer LABELS (not capability ids) so the toast is user-friendly.
        val res = HashMap<String, String>()
        prefs.taskOverrides.forEach { (capId, entryId) ->
          if (capId == cap.id) return@forEach
          val label = vm.registry.get(capId)?.label ?: capId
          res.putIfAbsent(entryId, label)
        }
        res
      }
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(PixelPalette.softGlow.copy(alpha = 0.35f))
          .padding(10.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(cap.category.color.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
          ) { Icon(cap.icon, null, tint = cap.category.color, modifier = Modifier.size(16.dp)) }
          Spacer(Modifier.width(8.dp))
          Column(Modifier.weight(1f)) {
            Text(cap.label, color = PixelPalette.onDark, fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(cap.category.label, color = PixelPalette.onDarkMuted, fontSize = 11.sp, maxLines = 1)
          }
          if (prefs.taskOverrides.containsKey(cap.id)) {
            Text(
              "Reset",
              color = PixelPalette.moon,
              fontSize = 11.sp,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { vm.preferencesStore.clearTaskOverride(cap.id) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
        Spacer(Modifier.size(6.dp))
        val currentLabel =
          filteredEntries.firstOrNull { it.first.id == effectiveId }?.first?.label
            ?: TaskSpriteCatalog.ENTRIES.firstOrNull { it.id == effectiveId }?.label
            ?: "Por defecto"
        Text(
          "Actual: $currentLabel",
          color = PixelPalette.onDarkMuted, fontSize = 11.sp,
          modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        // Per-row LazyRow state so we can scroll the preselected entry to the horizontal centre.
        val rowState = rememberLazyListState()
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxWidth()) {
          val viewportPx = with(density) { maxWidth.toPx() }.toInt()
          val cellPx = with(density) { 80.dp.toPx() }.toInt() // ≈ cell + spacing (see 6.dp + 8+56).
          val selectedIndex = filteredEntries.indexOfFirst { it.first.id == effectiveId }
          LaunchedEffect(effectiveId, filteredEntries.size, viewportPx) {
            if (selectedIndex >= 0 && viewportPx > 0) {
              val offset = -(viewportPx / 2 - cellPx / 2)
              runCatching { rowState.scrollToItem(selectedIndex, offset) }
            }
          }
          LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            rowItems(filteredEntries, key = { it.first.id }) { (e, bmp, preview) ->
              val sel = effectiveId == e.id
              GalleryEntryCell(
                entry = e, bmp = bmp, preview = preview,
                selected = sel,
                consumerLabel = consumed[e.id],
                onClick = { vm.preferencesStore.setTaskOverride(cap.id, e.id) },
              )
            }
          }
        }
      }
    }
  }
}
