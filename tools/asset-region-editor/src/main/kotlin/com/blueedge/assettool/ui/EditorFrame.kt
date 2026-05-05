/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: LICENSE.md
 */
package com.blueedge.assettool.ui

import com.blueedge.assettool.codegen.KotlinCatalogGenerator
import com.blueedge.assettool.io.AssetIndex
import com.blueedge.assettool.io.AssetRegionsIo
import com.blueedge.assettool.io.RepoPaths
import com.blueedge.assettool.io.ValidationIssue
import com.blueedge.assettool.model.AssetRegionsFile
import com.blueedge.assettool.model.AssetType
import com.blueedge.assettool.model.Category
import com.blueedge.assettool.model.CharacterSlot
import com.blueedge.assettool.model.RegionEntry
import com.blueedge.assettool.ui.components.Card
import com.blueedge.assettool.ui.components.RoundedLineBorder
import com.blueedge.assettool.ui.components.ghostButton
import com.blueedge.assettool.ui.components.primaryButton
import com.blueedge.assettool.ui.components.secondaryButton
import com.blueedge.assettool.ui.components.vSeparator
import com.blueedge.assettool.ui.theme.EditorTheme
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Main window of the asset region editor. Laid out as four clearly separated zones:
 *
 *   ┌─────────────────────────── TopBar (actions + cell + zoom) ───────────────────────────┐
 *   │ Assets │                         Canvas (scrollable)                      │Inspector │
 *   │  list  │  • checker background                                            │  form +  │
 *   │  +     │  • grid, assigned cells, hover, selection                        │  table   │
 *   │ search │                                                                  │          │
 *   ├────────┴──────────────────── StatusBar (hover coord + totals + toast) ─────────────────┤
 *   └───────────────────────────────────────────────────────────────────────────────────────┘
 */
class EditorFrame : JFrame("Blue Edge · Asset Region Editor") {

  // --- Left panel -------------------------------------------------------------------------
  private val searchField = JTextField()
  private val assetList = JList<File>()
  private val assetListModel = DefaultListModel<File>()
  private val assetCount = JLabel()

  // --- Canvas -----------------------------------------------------------------------------
  private val canvas = CanvasPanel()
  private val canvasScroll = JScrollPane(canvas).apply {
    border = BorderFactory.createEmptyBorder()
    viewport.background = EditorTheme.background
    horizontalScrollBar.unitIncrement = 24
    verticalScrollBar.unitIncrement = 24
  }

  // --- Right inspector --------------------------------------------------------------------
  private val regionsModel = RegionsTableModel()
  private val regionsTable = JTable(regionsModel)

  // --- Top bar controls -------------------------------------------------------------------
  private val cellSizeField = JSpinner(SpinnerNumberModel(16, 1, 256, 1))
  private val zoomField = JSpinner(SpinnerNumberModel(3, 1, 16, 1))

  // --- Status bar -------------------------------------------------------------------------
  private val statusLeft = JLabel(" ")
  private val statusCenter = JLabel(" ")
  private val statusRight = JLabel(" ")
  private var toastTimer: Timer? = null

  // --- Inspector form widgets -------------------------------------------------------------
  private val idField = JTextField()
  private val labelField = JTextField()
  private val categoryBox = JComboBox(Category.entries.toTypedArray())
  private val slotBox = JComboBox<Any>(arrayOf<Any>("(n/a)", *CharacterSlot.entries.toTypedArray()))
  private val charIdField = JTextField()

  private val selectedTypePlantCheck = JCheckBox("Planta (regar)")
  private val selectedTypeSolidCheck = JCheckBox("Sólido (picar)")
  private val selectedTypeNormalCheck = JCheckBox("Normal (orbitar)")
  private val selectedAssetTypeGroup = ButtonGroup().apply {
    add(selectedTypePlantCheck); add(selectedTypeSolidCheck); add(selectedTypeNormalCheck)
  }
  private val selectedTypeStatus = JLabel(
    "Selecciona uno o varios task sprites en la lista para asignarles tipo en lote."
  )

  private fun selectedAssetTypeOrNull(): AssetType? = when {
    selectedTypePlantCheck.isSelected  -> AssetType.PLANT
    selectedTypeSolidCheck.isSelected  -> AssetType.SOLID
    selectedTypeNormalCheck.isSelected -> AssetType.NORMAL
    else -> null
  }

  private fun setSelectedAssetTypeSelection(type: AssetType?) {
    selectedAssetTypeGroup.clearSelection()
    when (type) {
      AssetType.PLANT  -> selectedTypePlantCheck.isSelected = true
      AssetType.SOLID  -> selectedTypeSolidCheck.isSelected = true
      AssetType.NORMAL -> selectedTypeNormalCheck.isSelected = true
      null -> Unit
    }
  }

  private fun setSelectedTypeControlsEnabled(enabled: Boolean) {
    listOf(selectedTypePlantCheck, selectedTypeSolidCheck, selectedTypeNormalCheck).forEach {
      it.isEnabled = enabled
    }
  }

  private fun selectedRegionModelRows(): List<Int> =
    regionsTable.selectedRows
      .map { regionsTable.convertRowIndexToModel(it) }
      .distinct()
      .sorted()

  private fun selectedRegions(): List<RegionEntry> = selectedRegionModelRows().map(regionsModel::get)

  private fun refreshSelectedTypeControls() {
    val selected = selectedRegions()
    val taskSprites = selected.takeIf { it.isNotEmpty() && it.all { row -> row.category == Category.TASK_SPRITE } }
    if (taskSprites == null) {
      setSelectedTypeControlsEnabled(false)
      setSelectedAssetTypeSelection(null)
      selectedTypeStatus.text = when {
        selected.isEmpty() -> "Selecciona uno o varios task sprites en la lista para asignarles tipo en lote."
        else -> "El tipo en lote solo se puede editar cuando toda la selección es task_sprite."
      }
      return
    }
    setSelectedTypeControlsEnabled(true)
    val distinctTypes = taskSprites.map { it.assetType }.distinct()
    setSelectedAssetTypeSelection(distinctTypes.singleOrNull())
    selectedTypeStatus.text = when {
      taskSprites.size == 1 -> "1 task sprite seleccionado."
      distinctTypes.size > 1 -> "${taskSprites.size} task sprites seleccionados con tipos mezclados."
      else -> "${taskSprites.size} task sprites seleccionados."
    }
  }

  private fun restoreRegionSelection(keys: Set<Pair<Category, String>>) {
    regionsTable.clearSelection()
    state.regions.forEachIndexed { index, region ->
      if (region.category to region.id in keys) {
        val viewRow = regionsTable.convertRowIndexToView(index)
        if (viewRow >= 0) regionsTable.addRowSelectionInterval(viewRow, viewRow)
      }
    }
  }

  private fun applyAssetTypeToSelectedRows(type: AssetType?) {
    val rows = selectedRegionModelRows()
    if (rows.isEmpty()) return toastWarn("Selecciona task sprites en la tabla primero.")
    val selection = rows.map(regionsModel::get)
    if (selection.any { it.category != Category.TASK_SPRITE }) {
      return toastWarn("El tipo solo se puede asignar a filas task_sprite.")
    }
    val selectedKeys = selection.map { it.category to it.id }.toSet()
    val updated = state.regions.toMutableList()
    rows.forEach { row -> updated[row] = updated[row].copy(assetType = type) }
    state = state.copy(regions = updated)
    regionsModel.fireTableDataChanged()
    restoreRegionSelection(selectedKeys)
    refreshSelectedTypeControls()
    val typeLabel = when (type) {
      AssetType.PLANT -> "planta"
      AssetType.SOLID -> "sólido"
      AssetType.NORMAL -> "normal"
      null -> "sin tipo"
    }
    toastInfo("Tipo '$typeLabel' aplicado a ${rows.size} task sprite(s).")
  }

  private var suppressAssetSelection = false

  private var state: AssetRegionsFile = AssetRegionsIo.load()

  init {
    defaultCloseOperation = EXIT_ON_CLOSE
    preferredSize = Dimension(1440, 920)
    minimumSize = Dimension(1100, 720)
    contentPane.background = EditorTheme.background
    layout = BorderLayout()

    add(buildTopBar(), BorderLayout.NORTH)
    add(buildCenter(), BorderLayout.CENTER)
    add(buildStatusBar(), BorderLayout.SOUTH)

    populateAssetList("")
    refreshStatus()
    wireCanvasCallbacks()
    refreshSelectedTypeControls()
    pack()
    setLocationRelativeTo(null)
  }

  // ===== Top bar ==========================================================================

  private fun buildTopBar(): JComponent {
    val left = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
      isOpaque = false
      add(logoLabel())
      add(Box.createHorizontalStrut(8))
      add(vSeparator())
      add(Box.createHorizontalStrut(4))
      add(fieldLabel("Cell"))
      add(cellSizeField.apply {
        preferredSize = Dimension(72, 30)
        addChangeListener {
          val v = value as Int
          canvas.cellSize = v
          currentAsset()?.let { rememberCellOverride(it, v) }
        }
      })
      add(Box.createHorizontalStrut(10))
      add(fieldLabel("Zoom"))
      add(zoomField.apply {
        preferredSize = Dimension(64, 30)
        addChangeListener { canvas.zoom = value as Int }
      })
    }
    val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
      isOpaque = false
      add(secondaryButton("✨ Nueva región JSON") { openNewRegionDialog() })
      add(secondaryButton("↻ Recargar") { reload() })
      add(secondaryButton("✔ Validar") { validateInteractive() })
      add(secondaryButton("⚙ Generar catálogos") { generateKotlin() })
      add(primaryButton("💾 Guardar JSON") { saveJson() })
    }
    return JPanel(BorderLayout()).apply {
      background = EditorTheme.surface
      border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, EditorTheme.border),
        EmptyBorder(10, 14, 10, 14),
      )
      add(left, BorderLayout.WEST)
      add(right, BorderLayout.EAST)
    }
  }

  private fun logoLabel(): JComponent {
    val badge = JLabel("BE").apply {
      font = Font(Font.SANS_SERIF, Font.BOLD, 12)
      foreground = Color.WHITE
      horizontalAlignment = JLabel.CENTER
      preferredSize = Dimension(30, 30)
      isOpaque = false
      border = BorderFactory.createCompoundBorder(
        RoundedLineBorder(EditorTheme.accent, 8),
        EmptyBorder(0, 0, 0, 0),
      )
      background = EditorTheme.accent
    }
    // Paint rounded background behind the label.
    val wrapper = object : JPanel() {
      override fun paintComponent(g: Graphics) {
        val g2 = g.create() as java.awt.Graphics2D
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = EditorTheme.accent
        g2.fillRoundRect(0, 0, width, height, 8, 8)
        g2.dispose()
        super.paintComponent(g)
      }
    }.apply {
      isOpaque = false
      layout = BorderLayout()
      preferredSize = Dimension(30, 30)
      add(badge, BorderLayout.CENTER)
    }

    val title = JLabel("Asset Region Editor").apply {
      font = EditorTheme.fontTitle
      foreground = EditorTheme.onSurface
    }
    val subtitle = JLabel("tools/asset-regions/asset_regions.json").apply {
      font = EditorTheme.fontSmall
      foreground = EditorTheme.onSurfaceMuted
    }
    val text = JPanel().apply {
      isOpaque = false
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(title)
      add(subtitle)
    }
    return JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
      isOpaque = false
      add(wrapper)
      add(text)
    }
  }

  private fun fieldLabel(text: String): JLabel = JLabel(text).apply {
    font = EditorTheme.fontSmall.deriveFont(Font.BOLD)
    foreground = EditorTheme.onSurfaceMuted
    border = EmptyBorder(0, 4, 0, 6)
  }

  // ===== Center layout =====================================================================

  private fun buildCenter(): JComponent {
    val leftPanel = buildLeftPanel()
    val rightPanel = buildRightPanel()

    val canvasCard = JPanel(BorderLayout()).apply {
      background = EditorTheme.background
      border = EmptyBorder(10, 10, 10, 10)
      add(canvasScroll, BorderLayout.CENTER)
    }

    val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasCard, rightPanel).apply {
      resizeWeight = 0.70
      dividerLocation = 900
      isContinuousLayout = true
      border = BorderFactory.createEmptyBorder()
    }
    val rootSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, centerSplit).apply {
      resizeWeight = 0.22
      dividerLocation = 320
      isContinuousLayout = true
      border = BorderFactory.createEmptyBorder()
    }
    return rootSplit
  }

  private fun buildLeftPanel(): JComponent {
    // Asset list.
    assetList.model = assetListModel
    assetList.selectionMode = ListSelectionModel.SINGLE_SELECTION
    assetList.background = EditorTheme.surface
    assetList.foreground = EditorTheme.onSurface
    assetList.selectionBackground = EditorTheme.accent
    assetList.selectionForeground = Color.WHITE
    assetList.border = EmptyBorder(4, 4, 4, 4)
    assetList.fixedCellHeight = 28
    assetList.cellRenderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
      ): Component {
        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        if (value is File) {
          val rel = RepoPaths.relativeAssetPath(value)
          val short = rel.substringAfter("stocatstic/")
          val count = state.regions.count { it.assetPath == rel }
          c.text = if (count > 0) "<html>$short <span style='color:#8A94A6'>· $count</span></html>" else short
        }
        c.font = EditorTheme.fontBody
        c.border = EmptyBorder(4, 10, 4, 10)
        if (!isSelected) c.background = EditorTheme.surface
        return c
      }
    }
    assetList.addListSelectionListener {
      if (!it.valueIsAdjusting) onAssetSelected(assetList.selectedValue)
    }

    searchField.putClientProperty("JTextField.placeholderText", "Buscar asset…")
    searchField.putClientProperty("JTextField.showClearButton", true)
    searchField.font = EditorTheme.fontBody
    searchField.document.addDocumentListener(simpleDocListener { populateAssetList(searchField.text.trim()) })

    val header = JPanel(BorderLayout(8, 8)).apply {
      isOpaque = false
      border = EmptyBorder(0, 0, 10, 0)
      add(JLabel("ASSETS").apply {
        font = EditorTheme.fontSmall.deriveFont(Font.BOLD)
        foreground = EditorTheme.onSurfaceMuted
      }, BorderLayout.NORTH)
      add(searchField, BorderLayout.CENTER)
    }

    val listScroll = JScrollPane(assetList).apply {
      border = RoundedLineBorder(EditorTheme.border, 10)
      background = EditorTheme.surface
      viewport.background = EditorTheme.surface
    }

    val footer = JPanel(BorderLayout()).apply {
      isOpaque = false
      border = EmptyBorder(8, 2, 0, 2)
      assetCount.font = EditorTheme.fontSmall
      assetCount.foreground = EditorTheme.onSurfaceMuted
      add(assetCount, BorderLayout.WEST)
    }

    val body = JPanel(BorderLayout()).apply {
      background = EditorTheme.background
      border = EmptyBorder(12, 12, 12, 8)
      add(header, BorderLayout.NORTH)
      add(listScroll, BorderLayout.CENTER)
      add(footer, BorderLayout.SOUTH)
    }
    return body
  }

  // ===== Right inspector ==================================================================

  private fun buildRightPanel(): JComponent {
    // --- Region form ---
    val form = JPanel(GridBagLayout()).apply { isOpaque = false }
    val c = GridBagConstraints().apply {
      insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL
    }
    fun addLabel(text: String, row: Int) {
      c.gridx = 0; c.gridy = row; c.weightx = 0.0
      form.add(JLabel(text).apply {
        font = EditorTheme.fontSmall.deriveFont(Font.BOLD)
        foreground = EditorTheme.onSurfaceMuted
      }, c)
    }
    fun addField(comp: JComponent, row: Int) {
      c.gridx = 1; c.gridy = row; c.weightx = 1.0
      if (comp is JTextField) comp.font = EditorTheme.fontBody
      form.add(comp, c)
    }

    addLabel("id",          0); addField(idField, 0)
    addLabel("label",       1); addField(labelField, 1)
    addLabel("category",    2); addField(categoryBox, 2)
    addLabel("character id",3); addField(charIdField, 3)
    addLabel("char. slot",  4); addField(slotBox, 4)

    val addBtn = primaryButton("➕ Añadir / actualizar") { addFromForm() }
    val delBtn = secondaryButton("🗑 Eliminar selección") { deleteSelectedRow() }
    val clearSelBtn = ghostButton("Limpiar selección canvas") { canvas.clearSelection() }
    val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
      isOpaque = false
      add(addBtn); add(delBtn); add(clearSelBtn)
    }
    c.gridx = 0; c.gridy = 5; c.gridwidth = 2; c.weightx = 1.0
    c.insets = Insets(12, 4, 4, 4)
    form.add(buttons, c)
    c.gridwidth = 1; c.insets = Insets(4, 4, 4, 4)

    val formCard = Card(title = "REGIÓN", content = form)

    val selectedTypeRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
      isOpaque = false
      listOf(selectedTypePlantCheck, selectedTypeSolidCheck, selectedTypeNormalCheck).forEach { cb ->
        cb.isOpaque = false
        cb.font = EditorTheme.fontBody
        cb.foreground = EditorTheme.onSurface
        add(cb)
      }
    }
    selectedTypeStatus.font = EditorTheme.fontSmall
    selectedTypeStatus.foreground = EditorTheme.onSurfaceMuted
    val applyTypeBtn = primaryButton("Aplicar a seleccionados") {
      applyAssetTypeToSelectedRows(selectedAssetTypeOrNull())
    }
    val clearTypeBtn = secondaryButton("Quitar tipo") {
      applyAssetTypeToSelectedRows(null)
    }
    val selectionTypePanel = JPanel(BorderLayout(0, 8)).apply {
      isOpaque = false
      add(selectedTypeStatus, BorderLayout.NORTH)
      add(selectedTypeRow, BorderLayout.CENTER)
      add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
        isOpaque = false
        add(applyTypeBtn)
        add(clearTypeBtn)
      }, BorderLayout.SOUTH)
    }
    val selectionTypeCard = Card(
      title = "TIPO DE TASK SPRITES SELECCIONADOS",
      content = selectionTypePanel,
    )

    // --- Regions table ---
    regionsTable.autoCreateRowSorter = true
    regionsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    regionsTable.fillsViewportHeight = true
    regionsTable.rowHeight = 26
    regionsTable.font = EditorTheme.fontBody
    regionsTable.tableHeader.font = EditorTheme.fontSmall.deriveFont(Font.BOLD)
    regionsTable.tableHeader.foreground = EditorTheme.onSurfaceMuted
    regionsTable.background = EditorTheme.surface
    regionsTable.foreground = EditorTheme.onSurface
    regionsTable.selectionBackground = EditorTheme.accent.darker()
    regionsTable.selectionForeground = Color.WHITE
    regionsTable.gridColor = EditorTheme.border
    regionsTable.getColumnModel().getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(
        table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
      ): Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (c is JLabel) {
          c.text = (value?.toString() ?: "").uppercase()
          c.font = EditorTheme.fontSmall.deriveFont(Font.BOLD)
          c.foreground = colorForCategoryName(value?.toString())
        }
        return c
      }
    }
    regionsTable.selectionModel.addListSelectionListener { e ->
      if (e.valueIsAdjusting) return@addListSelectionListener
      val rows = selectedRegionModelRows()
      if (rows.size == 1) {
        val r = regionsModel.get(rows.first())
        idField.text = r.id
        labelField.text = r.label
        categoryBox.selectedItem = r.category
        charIdField.text = r.characterId ?: ""
        slotBox.selectedItem = r.characterSlot ?: "(n/a)"
      }
      refreshSelectedTypeControls()
    }

    val tableScroll = JScrollPane(regionsTable).apply {
      border = RoundedLineBorder(EditorTheme.border, 10)
      background = EditorTheme.surface
      viewport.background = EditorTheme.surface
    }
    val tableCard = Card(title = "REGIONES EN EL JSON",
      content = JPanel(BorderLayout()).apply { isOpaque = false; add(tableScroll, BorderLayout.CENTER) })

    // Stack form + table with clear separation.
    val right = JPanel(BorderLayout()).apply {
      background = EditorTheme.background
      border = EmptyBorder(12, 8, 12, 12)
      val top = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = EmptyBorder(0, 0, 10, 0)
        add(formCard, BorderLayout.CENTER)
      }
      val center = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(JPanel(BorderLayout()).apply {
          isOpaque = false
          border = EmptyBorder(0, 0, 10, 0)
          add(selectionTypeCard, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(tableCard, BorderLayout.CENTER)
      }
      add(top, BorderLayout.NORTH)
      add(center, BorderLayout.CENTER)
    }
    right.preferredSize = Dimension(440, 0)
    return right
  }

  // ===== Status bar =======================================================================

  private fun buildStatusBar(): JComponent {
    statusLeft.font = EditorTheme.fontSmall; statusLeft.foreground = EditorTheme.onSurfaceMuted
    statusCenter.font = EditorTheme.fontSmall; statusCenter.foreground = EditorTheme.onSurface
    statusRight.font = EditorTheme.fontSmall; statusRight.foreground = EditorTheme.onSurfaceMuted

    return JPanel(BorderLayout()).apply {
      background = EditorTheme.surface
      border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, EditorTheme.border),
        EmptyBorder(6, 14, 6, 14),
      )
      add(statusLeft, BorderLayout.WEST)
      add(statusCenter, BorderLayout.CENTER)
      add(statusRight, BorderLayout.EAST)
      statusCenter.horizontalAlignment = JLabel.CENTER
    }
  }

  private fun wireCanvasCallbacks() {
    canvas.onHoverCellChanged = { cell ->
      val img = cell?.let { "celda (${it.first}, ${it.second})" } ?: "—"
      statusCenter.text = img
    }
    canvas.onZoomChanged = { z -> zoomField.value = z }
  }

  // ===== Data & actions ===================================================================

  private fun populateAssetList(query: String) {
    val items = AssetIndex.listPngs()
    val filtered = if (query.isBlank()) items else items.filter {
      RepoPaths.relativeAssetPath(it).contains(query, ignoreCase = true)
    }
    assetListModel.clear()
    filtered.forEach { assetListModel.addElement(it) }
    assetCount.text = "${filtered.size}/${items.size} assets"
  }

  private fun currentAsset(): File? = assetList.selectedValue

  private fun selectAssetFile(file: File) {
    searchField.text = ""
    populateAssetList("")
    val index = (0 until assetListModel.size()).firstOrNull { assetListModel.getElementAt(it) == file } ?: return
    suppressAssetSelection = true
    assetList.selectedIndex = index
    suppressAssetSelection = false
    onAssetSelected(file)
  }

  private fun onAssetSelected(f: File?) {
    if (suppressAssetSelection) return
    if (f == null) {
      canvas.setImage(null)
      return
    }
    try {
      val img = javax.imageio.ImageIO.read(f)
      canvas.setImage(img)
      val rel = RepoPaths.relativeAssetPath(f)
      val cs = state.cellSizeOverrides[rel] ?: 16
      cellSizeField.value = cs
      canvas.cellSize = cs
      reloadAssignedOverlay()
      statusLeft.text = "📄 $rel    ${img.width}×${img.height} px"
    } catch (t: Throwable) {
      toastWarn("No se pudo leer ${f.name}: ${t.message}")
    }
  }

  private fun rememberCellOverride(f: File, size: Int) {
    val rel = RepoPaths.relativeAssetPath(f)
    val newMap = state.cellSizeOverrides.toMutableMap()
    if (size == 16) newMap.remove(rel) else newMap[rel] = size
    state = state.copy(cellSizeOverrides = newMap)
  }

  private fun reloadAssignedOverlay() {
    val f = currentAsset() ?: run { canvas.setAssigned(emptyMap()); return }
    val rel = RepoPaths.relativeAssetPath(f)
    val map = mutableMapOf<Pair<Int, Int>, CanvasPanel.AssignedTag>()
    for (r in state.regions.filter { it.assetPath == rel }) {
      val color = categoryColor(r.category)
      for (dc in 0 until r.colSpan) for (dr in 0 until r.rowSpan) {
        map[r.col + dc to r.row + dr] = CanvasPanel.AssignedTag(r.id, color)
      }
    }
    canvas.setAssigned(map)
  }

  private fun categoryColor(c: Category): Color = when (c) {
    Category.CHARACTER    -> EditorTheme.catCharacter
    Category.ROOT         -> EditorTheme.catRoot
    Category.PATH         -> EditorTheme.catPath
    Category.TASK_SPRITE  -> EditorTheme.catTaskSprite
    Category.ENEMY        -> EditorTheme.catEnemy
    Category.ANIMAL       -> EditorTheme.catAnimal
    Category.UI           -> EditorTheme.catUi
  }

  private fun colorForCategoryName(name: String?): Color = when (name) {
    "character"   -> EditorTheme.catCharacter
    "root"        -> EditorTheme.catRoot
    "path"        -> EditorTheme.catPath
    "task_sprite" -> EditorTheme.catTaskSprite
    "enemy"       -> EditorTheme.catEnemy
    "animal"      -> EditorTheme.catAnimal
    "ui"          -> EditorTheme.catUi
    else          -> EditorTheme.onSurfaceMuted
  }

  private fun addFromForm() {
    val asset = currentAsset() ?: return toastWarn("Selecciona un asset primero.")
    val cells = canvas.currentSelection()
    if (cells.isEmpty()) return toastWarn("Marca al menos una celda en el canvas.")
    val id = idField.text.trim()
    val label = labelField.text.trim()
    if (id.isEmpty() || label.isEmpty()) return toastWarn("id y label son obligatorios.")
    val cat = categoryBox.selectedItem as Category
    val slot = slotBox.selectedItem.let { if (it is CharacterSlot) it else null }
    val charId = charIdField.text.trim().ifEmpty { null }
    if (cat == Category.CHARACTER && (charId.isNullOrBlank() || slot == null)) {
      return toastWarn("Las regiones character requieren characterId y char. slot.")
    }
    val relPath = RepoPaths.relativeAssetPath(asset)
    val existing = state.regions.firstOrNull { it.category == cat && it.id == id }
    val entry = RegionEntry(
      assetPath = relPath, category = cat, id = id, label = label,
      col = cells.minOf { it.first }, row = cells.minOf { it.second },
      colSpan = cells.maxOf { it.first } - cells.minOf { it.first } + 1,
      rowSpan = cells.maxOf { it.second } - cells.minOf { it.second } + 1,
      characterId = charId, characterSlot = slot,
      assetType = if (cat == Category.TASK_SPRITE) existing?.assetType else null,
    )
    addOrReplace(entry)
    idField.text = ""; labelField.text = ""; charIdField.text = ""
    canvas.clearSelection()
    reloadAssignedOverlay()
    toastInfo("Añadida '$id' (${cat.name.lowercase()}).")
  }

  private fun addOrReplace(e: RegionEntry) {
    val list = state.regions.toMutableList()
    val idx = list.indexOfFirst { it.category == e.category && it.id == e.id }
    if (idx >= 0) list[idx] = e else list += e
    state = state.copy(regions = list)
    regionsModel.fireTableDataChanged()
    refreshStatus()
    assetList.repaint()
  }

  private fun deleteSelectedRow() {
    val selected = selectedRegions()
    if (selected.isEmpty()) return
    state = state.copy(regions = state.regions.filterNot { it in selected })
    regionsModel.fireTableDataChanged()
    refreshSelectedTypeControls()
    reloadAssignedOverlay()
    refreshStatus()
    assetList.repaint()
    toastInfo(
      if (selected.size == 1) "Eliminada '${selected.first().id}'."
      else "Eliminadas ${selected.size} regiones."
    )
  }

  private fun reload() {
    state = AssetRegionsIo.load()
    regionsModel.fireTableDataChanged()
    reloadAssignedOverlay()
    refreshStatus()
    assetList.repaint()
    toastInfo("JSON recargado.")
  }

  private fun saveJson() {
    val issues = AssetRegionsIo.validateAll(state)
    val errors = issues.filter { it.severity == ValidationIssue.Severity.ERROR }
    if (errors.isNotEmpty()) {
      return toastWarn("No se puede guardar (${errors.size} errores): ${errors.first().message}")
    }
    AssetRegionsIo.save(state)
    toastInfo("Guardado en ${RepoPaths.regionsJson.relativeTo(RepoPaths.repoRoot).path}.")
  }

  private fun generateKotlin() {
    val issues = AssetRegionsIo.validateAll(state)
    val errors = issues.filter { it.severity == ValidationIssue.Severity.ERROR }
    if (errors.isNotEmpty()) {
      return toastWarn("Valida los errores antes de generar: ${errors.first().message}")
    }
    val out = KotlinCatalogGenerator.writeToFile(state)
    toastInfo("Generado ${out.relativeTo(RepoPaths.repoRoot).path}.")
  }

  private fun validateInteractive() {
    val issues = AssetRegionsIo.validateAll(state)
    if (issues.isEmpty()) return toastInfo("✅ Sin errores — ${state.regions.size} regiones.")
    val txt = issues.joinToString("\n") { it.toString() }
    JOptionPane.showMessageDialog(this, txt, "Resultado de validación",
      if (issues.any { it.severity == ValidationIssue.Severity.ERROR }) JOptionPane.ERROR_MESSAGE
      else JOptionPane.WARNING_MESSAGE)
  }

  private fun normalizeAssetPath(rawPath: String): String =
    rawPath.trim().replace('\\', '/').removePrefix("/")
      .removePrefix("stocatstic/")
      .let { if (it.endsWith(".png", ignoreCase = true)) it else "$it.png" }
      .let { "stocatstic/$it" }

  private fun buildRectSelection(col: Int, row: Int, colSpan: Int, rowSpan: Int): Set<Pair<Int, Int>> =
    buildSet {
      for (x in col until col + colSpan.coerceAtLeast(1)) {
        for (y in row until row + rowSpan.coerceAtLeast(1)) {
          add(x to y)
        }
      }
    }

  private fun openNewRegionDialog() {
    val originalAsset = currentAsset()
    val originalSelection = canvas.currentSelection()
    val currentRelPath = currentAsset()?.let { RepoPaths.relativeAssetPath(it) }.orEmpty()
    val cells = canvas.currentSelection().takeIf { it.isNotEmpty() }
    val defaultCol = cells?.minOf { it.first } ?: 0
    val defaultRow = cells?.minOf { it.second } ?: 0
    val defaultColSpan = cells?.let { it.maxOf { cell -> cell.first } - defaultCol + 1 } ?: 1
    val defaultRowSpan = cells?.let { it.maxOf { cell -> cell.second } - defaultRow + 1 } ?: 1

    val initialCategory = categoryBox.selectedItem as? Category ?: Category.ROOT
    val defaultAssetPath = currentRelPath.ifBlank { "stocatstic/Custom/new_region.png" }
    val categoryField = JComboBox(Category.entries.toTypedArray()).apply {
      selectedItem = initialCategory
    }
    val assetPathField = JTextField(defaultAssetPath)
    val idField = JTextField(
      currentAsset()?.nameWithoutExtension?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")
        ?.trim('_').orEmpty()
    )
    val labelField = JTextField(currentAsset()?.nameWithoutExtension?.replace('_', ' ')?.replace('-', ' ').orEmpty())
    val colField = JSpinner(SpinnerNumberModel(defaultCol, 0, 4096, 1))
    val rowField = JSpinner(SpinnerNumberModel(defaultRow, 0, 4096, 1))
    val colSpanField = JSpinner(SpinnerNumberModel(defaultColSpan, 1, 512, 1))
    val rowSpanField = JSpinner(SpinnerNumberModel(defaultRowSpan, 1, 512, 1))
    val charIdField = JTextField()
    val slotOptions = arrayOf<Any>("(n/a)", *CharacterSlot.entries.toTypedArray())
    val slotBox = JComboBox(slotOptions)
    val typeOptions = arrayOf<Any>("(sin tipo)", *AssetType.entries.toTypedArray())
    val typeBox = JComboBox(typeOptions)
    val panel = JPanel(GridBagLayout())
    val gc = GridBagConstraints().apply {
      insets = Insets(4, 4, 4, 4)
      anchor = GridBagConstraints.WEST
      fill = GridBagConstraints.HORIZONTAL
    }
    fun addRow(row: Int, label: String, comp: JComponent) {
      gc.gridx = 0; gc.gridy = row; gc.weightx = 0.0
      panel.add(JLabel(label), gc)
      gc.gridx = 1; gc.weightx = 1.0
      panel.add(comp, gc)
    }
    addRow(0, "category", categoryField)
    addRow(1, "asset path", assetPathField)
    addRow(2, "id", idField)
    addRow(3, "label", labelField)
    addRow(4, "col", colField)
    addRow(5, "row", rowField)
    addRow(6, "colSpan", colSpanField)
    addRow(7, "rowSpan", rowSpanField)
    addRow(8, "character id", charIdField)
    addRow(9, "char. slot", slotBox)
    addRow(10, "tipo", typeBox)

    fun selectedDialogCategory(): Category = categoryField.selectedItem as Category
    fun refreshDialogFieldState() {
      val selectedCategory = selectedDialogCategory()
      val isCharacter = selectedCategory == Category.CHARACTER
      val isTaskSprite = selectedCategory == Category.TASK_SPRITE
      charIdField.isEnabled = isCharacter
      slotBox.isEnabled = isCharacter
      typeBox.isEnabled = isTaskSprite
      if (!isTaskSprite) typeBox.selectedItem = "(sin tipo)"
    }
    categoryField.addActionListener { refreshDialogFieldState() }
    refreshDialogFieldState()

    try {
      while (true) {
        val result = JOptionPane.showOptionDialog(
          this,
          panel,
          "Crear región JSON",
          JOptionPane.DEFAULT_OPTION,
          JOptionPane.PLAIN_MESSAGE,
          null,
          arrayOf("Previsualizar", "Crear", "Cancelar"),
          "Crear",
        )
        if (result == 2 || result == JOptionPane.CLOSED_OPTION) {
          if (originalAsset != null) {
            selectAssetFile(originalAsset)
          } else {
            suppressAssetSelection = true
            assetList.clearSelection()
            suppressAssetSelection = false
            onAssetSelected(null)
          }
          canvas.setSelection(originalSelection)
          canvas.clearPreviewSelection()
          return
        }

        val category = selectedDialogCategory()
        val relPath = normalizeAssetPath(assetPathField.text)
        val id = idField.text.trim()
        val label = labelField.text.trim()
        if (relPath.isBlank() || id.isBlank() || label.isBlank()) {
          toastWarn("Asset path, id y label son obligatorios para crear una región.")
          continue
        }
        val characterId = charIdField.text.trim().ifEmpty { null }
        val slot = slotBox.selectedItem.let { if (it is CharacterSlot) it else null }
        if (category == Category.CHARACTER && (characterId.isNullOrBlank() || slot == null)) {
          toastWarn("Las regiones character requieren characterId y char. slot.")
          continue
        }
        val col = colField.value as Int
        val row = rowField.value as Int
        val colSpan = colSpanField.value as Int
        val rowSpan = rowSpanField.value as Int
        val assetFile = RepoPaths.resolveAsset(relPath)
        if (!assetFile.isFile) {
          toastWarn("El asset indicado no existe en disco: $relPath")
          continue
        }

        if (result == 0) {
          selectAssetFile(RepoPaths.resolveAsset(relPath))
          canvas.setPreviewSelection(buildRectSelection(col, row, colSpan, rowSpan))
          toastInfo("Previsualizando región '$id'.")
          continue
        }

        val existing = state.regions.firstOrNull { it.category == category && it.id == id }
        if (existing != null) {
          val confirm = JOptionPane.showConfirmDialog(
            this,
            "Ya existe una región '${existing.id}' en la categoría ${category.name.lowercase()}. ¿Quieres reemplazarla?",
            "Reemplazar región",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
          )
          if (confirm != JOptionPane.YES_OPTION) continue
        }

        val type = typeBox.selectedItem.let { if (it is AssetType) it else null }
        val entry = RegionEntry(
          assetPath = relPath,
          category = category,
          id = id,
          label = label,
          col = col,
          row = row,
          colSpan = colSpan,
          rowSpan = rowSpan,
          characterId = if (category == Category.CHARACTER) characterId else null,
          characterSlot = if (category == Category.CHARACTER) slot else null,
          assetType = if (category == Category.TASK_SPRITE) type else null,
        )
        addOrReplace(entry)
        selectAssetFile(RepoPaths.resolveAsset(relPath))
        canvas.clearPreviewSelection()
        canvas.setSelection(buildRectSelection(entry.col, entry.row, entry.colSpan, entry.rowSpan))
        reloadAssignedOverlay()
        toastInfo("Creada región '${entry.id}' (${entry.category.name.lowercase()}).")
        return
      }
    } finally {
      canvas.clearPreviewSelection()
    }
  }

  // ===== Status / toast ===================================================================

  private fun refreshStatus() {
    val totals = Category.entries.joinToString("  ·  ") { c ->
      "${c.name.lowercase()} ${state.regions.count { it.category == c }}"
    }
    statusRight.text = "Total ${state.regions.size}   │   $totals"
  }

  private fun toastInfo(msg: String) = showToast("✅ $msg", EditorTheme.success)
  private fun toastWarn(msg: String) = showToast("⚠ $msg", EditorTheme.warning)

  private fun showToast(msg: String, color: Color) {
    statusLeft.text = msg
    statusLeft.foreground = color
    toastTimer?.stop()
    toastTimer = Timer(4000) {
      statusLeft.foreground = EditorTheme.onSurfaceMuted
      statusLeft.text = currentAsset()?.let {
        val rel = RepoPaths.relativeAssetPath(it)
        "📄 $rel"
      } ?: " "
    }.apply { isRepeats = false; start() }
  }

  // ===== Table model ======================================================================

  private inner class RegionsTableModel : AbstractTableModel() {
    private val cols = listOf("cat.", "id", "label", "asset", "col", "row", "w", "h")
    override fun getRowCount(): Int = state.regions.size
    override fun getColumnCount(): Int = cols.size
    override fun getColumnName(column: Int): String = cols[column]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
      val r = state.regions[rowIndex]
      return when (columnIndex) {
        0 -> r.category.name.lowercase()
        1 -> r.id
        2 -> r.label
        3 -> r.assetPath.substringAfterLast('/')
        4 -> r.col
        5 -> r.row
        6 -> r.colSpan
        7 -> r.rowSpan
        else -> ""
      }
    }
    fun get(rowIndex: Int): RegionEntry = state.regions[rowIndex]
  }
}

private fun simpleDocListener(onChange: () -> Unit): javax.swing.event.DocumentListener =
  object : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { onChange() }
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { onChange() }
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { onChange() }
  }
