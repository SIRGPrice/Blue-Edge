/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.ui.assets

/**
 * Maps every workflow capability id to a *fixed*, UNIQUE 1-cell sprite drawn where the task
 * lives on the scene. Every capability resolves to a different entry inside
 * [TaskSpriteCatalog] so the gallery's "Tareas" tab shows a distinct preselected asset per
 * capability out of the box.
 *
 * The ROOT task of a flow is drawn as the user-picked `RootCatalog` sprite by the scene
 * renderer, regardless of capability, so every flow's start is visually unmistakable.
 */
object TaskSpriteRegistry {

  /** A sprite footprint inside a named sheet, expressed in source-tile cells. */
  data class Entry(
    val assetPath: String,
    val col: Int,
    val row: Int,
    val widthCells: Int = 1,
    val heightCells: Int = 1,
    /** Behavioural tag â€” see [AssetType]. `null` means "inherit default from id". */
    val assetType: AssetType? = null,
  )

  /**
   * Defaults Ãºnicos y estables para todas las capabilities builtin actuales. La lista de ids
   * de assets sÃ³lo usa entradas vÃ¡lidas del catÃ¡logo generado para evitar colisiones cuando el
   * JSON sustituye a los fallbacks manuales.
   */
  private val knownCapabilityIds = listOf(
    "ai.llm",
    "calendar.add",
    "clock.alarm",
    "clock.timer",
    "comm.dial",
    "comm.email",
    "comm.maps",
    "comm.share",
    "comm.sms",
    "comm.web_search",
    "control.branch",
    "control.counter",
    "control.delay",
    "control.fail",
    "control.passthrough",
    "control.set_var",
    "control.success",
    "device.battery",
    "device.flashlight",
    "device.ringer_mode",
    "device.toast",
    "device.tts",
    "device.vibrate",
    "intent.airplane_settings",
    "intent.bluetooth_settings",
    "intent.display_settings",
    "intent.generic",
    "intent.location_settings",
    "intent.open_app",
    "intent.sound_settings",
    "intent.view_uri",
    "intent.wifi_settings",
    "logic.compare",
    "math.eval",
    "media.camera",
    "net.http_get",
    "net.ping",
    "notify.push",
    "random.choice",
    "random.coin",
    "random.number",
    "system.clipboard_copy",
    "system.clipboard_read",
    "text.format",
    "time.now",
    "time.weekday",
  )

  private val preferredEntryIds = listOf(
    "crop_07",
    "boat_00",
    "camp_00",
    "fountain_00",
    "fountain_01",
    "fountain_02",
    "fountain_03",
    "fountain_04",
    "fountain_05",
    "house_00",
    "crop_02",
    "hs_04",
    "bn_00",
    "crop_00",
    "crop_01",
    "crop_05",
    "crop_14",
    "crop_12",
    "crop_08",
    "crop_10",
    "crop_11",
    "crop_03",
    "crop_04",
    "crop_13",
    "ext_05",
    "ext_00",
    "hs_05",
    "ext_01",
    "bn_02",
    "ext_03",
    "ext_02",
    "bn_01",
    "nat_03",
    "nat_00",
    "nat_01",
    "nat_06",
    "nat_05",
    "nat_04",
    "crop_15",
    "nat_07",
    "house_01",
    "house_02",
    "house_03",
    "house_04",
    "house_07",
    "house_08",
  )

  private val defaultEntryIdByCap: Map<String, String> =
    knownCapabilityIds.zip(preferredEntryIds).toMap()

  /**
   * Deterministic fallback id for capabilities not present in [defaultEntryIdByCap]: walks the
   * [TaskSpriteCatalog.ENTRIES] list using the stable hash of [capabilityId], so additions
   * remain distinct from each other without needing to hand-map every new capability.
   */
  fun defaultEntryId(capabilityId: String): String {
    val entries = TaskSpriteCatalog.ENTRIES
    val ids = entries.map { it.id }
    defaultEntryIdByCap[capabilityId]?.takeIf { it in ids }?.let { return it }
    if (entries.isEmpty()) return TaskSpriteCatalog.DEFAULT.id
    val reserved = defaultEntryIdByCap.values.toSet()
    val pool = ids.filterNot { it in reserved }.ifEmpty { ids }
    val idx = (capabilityId.hashCode().toLong() and 0x7fffffffL).rem(pool.size.toLong()).toInt()
    return pool[idx]
  }

  /** Fallback sprite when a capability id is not mapped and the catalog is empty. */
  val DEFAULT: Entry = Entry(
    assetPath = TaskSpriteCatalog.DEFAULT.assetPath,
    col = TaskSpriteCatalog.DEFAULT.col,
    row = TaskSpriteCatalog.DEFAULT.row,
    widthCells = TaskSpriteCatalog.DEFAULT.colSpan,
    heightCells = TaskSpriteCatalog.DEFAULT.rowSpan,
    assetType = TaskSpriteCatalog.DEFAULT.assetType ?: defaultAssetTypeFor(TaskSpriteCatalog.DEFAULT.id),
  )

  fun get(capabilityId: String): Entry {
    val id = defaultEntryId(capabilityId)
    val e = TaskSpriteCatalog.ENTRIES.firstOrNull { it.id == id }
      ?: TaskSpriteCatalog.DEFAULT
    return Entry(
      assetPath = e.assetPath,
      col = e.col,
      row = e.row,
      widthCells = e.colSpan,
      heightCells = e.rowSpan,
      assetType = e.assetType ?: defaultAssetTypeFor(e.id),
    )
  }

  /** Mailbox sprite override for the root (initial) task of every flow. */
  val ROOT_MAILBOX = Entry(MailboxSprite.ASSET, MailboxSprite.MAILBOX_TILE.first,
    MailboxSprite.MAILBOX_TILE.second)
}

