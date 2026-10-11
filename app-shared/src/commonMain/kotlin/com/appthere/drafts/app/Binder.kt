package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Palette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.binder
import com.appthere.drafts.i18n.resources.binder_moved
import com.appthere.drafts.i18n.resources.binder_reading
import com.appthere.drafts.project.ProjectView
import org.jetbrains.compose.resources.stringResource

/**
 * The binder (`projects/binder`, `projects.md` 10): the project's folder as a tree, in its order,
 * with what has changed marked on it, and everything the writer can do to it.
 *
 * A `LazyColumn` keyed by path, so a row keeps its focus and any drag in progress while rows come
 * and go around it. Every row is reachable from the keyboard, and everything a drag can do can be
 * done without one -- *Move up*, *Move down*, *Move to…* -- from the keyboard and from assistive
 * technology's actions, because drag and drop alone shuts out everyone who cannot drag.
 */
@Composable
fun Binder(
    controller: ProjectController,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val view by controller.view.collectAsState()
    val ui by controller.ui.collectAsState()
    val title = stringResource(Res.string.binder)

    Column(
        modifier
            .semantics { paneTitle = title }
            .background(palette.background)
            .padding(panelPadding),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        BasicText(
            text = view?.project?.name ?: title,
            style = interfaceTextStyle(color = palette.ink, fontSize = headingSize, fontWeight = Prose.H4.weight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val current = view
        if (current == null) {
            BasicText(
                stringResource(Res.string.binder_reading),
                style = interfaceTextStyle(color = palette.muted, fontSize = labelSize),
            )
        } else {
            Rows(current, ui, controller, Modifier.weight(1f))
        }

        Announced(ui.announcement)
    }
}

@Composable
private fun Rows(
    view: ProjectView,
    ui: BinderUi,
    controller: ProjectController,
    modifier: Modifier = Modifier,
) {
    val rows = remember(view, ui.expanded) { binderRows(view, ui.expanded) }
    val list = rememberLazyListState()
    val drag = remember { BinderDrag() }

    LazyColumn(state = list, modifier = modifier.semantics { collectionInfo = CollectionInfo(rows.size, 1) }) {
        items(rows, key = { it.path.value }) { row ->
            BinderItem(
                row = row,
                selected = ui.selected == row.path,
                controller = controller,
                modifier =
                    Modifier
                        .alpha(if (drag.source?.path == row.path) DRAGGED_ALPHA else 1f)
                        .dragging(row, rows, list, drag, controller)
                        .semantics { collectionItemInfo = CollectionItemInfo(rows.indexOf(row), 1, 0, 1) },
                target = drag.target?.takeIf { it.first.path == row.path }?.second,
            )
        }
    }
}

/**
 * One line of the binder. Selecting it opens it -- a document in the editor, a folder open or shut
 * -- and its keys do the rest: arrows to move between rows and open and shut folders, Alt with an
 * arrow to move it among its siblings, F2 to rename, Delete to put it in the trash.
 */
@Composable
private fun BinderItem(
    row: BinderRow,
    selected: Boolean,
    controller: ProjectController,
    modifier: Modifier = Modifier,
    target: DropZone? = null,
) {
    val palette = LocalPalette.current
    val focus = LocalFocusManager.current
    val described = describe(row)
    val actions = rowActions(row, controller)
    val state = rowState(row)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(markGap),
        modifier =
            modifier
                .fillMaxWidth()
                .sizeIn(minHeight = minimumTarget)
                .marked(target != null, selected, palette)
                .selectable(selected = selected) {
                    controller.select(row.path)
                    controller.open(row.node)
                }.onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        rowKey(event.key, event.isAltPressed, row, controller, focus::moveFocus)
                }.semantics(mergeDescendants = true) {
                    contentDescription = described
                    state?.let { stateDescription = it }
                    customActions = actions.map { it.accessibility }
                }.padding(start = indent * (row.level - 1)),
    ) {
        RowContent(row, actions)
    }
}

/**
 * A row under a drag is outlined, so the writer can see where it will go; the chosen row sits on the
 * one ground every text colour is guaranteed to read on.
 */
private fun Modifier.marked(
    underDrag: Boolean,
    selected: Boolean,
    palette: Palette,
): Modifier =
    then(if (underDrag) Modifier.border(hairline, palette.accent, RoundedCornerShape(corner)) else Modifier)
        .then(if (selected) Modifier.background(palette.editing, RoundedCornerShape(corner)) else Modifier)

/** The keys a focused row answers. True if [key] was one of them. */
private fun rowKey(
    key: Key,
    alt: Boolean,
    row: BinderRow,
    controller: ProjectController,
    moveFocus: (FocusDirection) -> Boolean,
): Boolean {
    val action = if (alt) movingKey(key, row, controller) else plainKey(key, row, controller)
    return when {
        action != null -> true.also { action() }
        key == Key.DirectionUp -> moveFocus(FocusDirection.Up)
        key == Key.DirectionDown -> moveFocus(FocusDirection.Down)
        else -> false
    }
}

/** Alt with an arrow: *Move up*, *Move down*. */
private fun movingKey(
    key: Key,
    row: BinderRow,
    controller: ProjectController,
): (() -> Unit)? =
    when (key) {
        Key.DirectionUp -> { -> controller.step(row.path, -1) }
        Key.DirectionDown -> { -> controller.step(row.path, 1) }
        else -> null
    }

/** Right and Left open and shut a folder; Enter opens; F2 renames; Delete puts it in the trash. */
private fun plainKey(
    key: Key,
    row: BinderRow,
    controller: ProjectController,
): (() -> Unit)? =
    when {
        row.isFolder && key == (if (row.expanded) Key.DirectionLeft else Key.DirectionRight) -> { ->
            controller.toggle(
                row.path,
            )
        }

        key == Key.Enter -> { -> controller.open(row.node) }

        key == Key.F2 && row.present -> { -> controller.ask(BinderPrompt.Rename(row.path)) }

        key == Key.Delete && row.present -> { -> controller.delete(row.node) }

        else -> null
    }

/** The drag that is in progress, if one is: what is being dragged, and the row and zone under it. */
private class BinderDrag {
    var source by mutableStateOf<BinderRow?>(null)
    var target by mutableStateOf<Pair<BinderRow, DropZone>?>(null)
    var y by mutableFloatStateOf(0f)
}

/**
 * A long press, then a drag, moves [row]: onto the upper or lower edge of another row to go beside
 * it, onto the middle of a folder to go into it. Long press first so that on a touch screen a drag
 * that starts on a row scrolls the list, as it does everywhere else.
 */
private fun Modifier.dragging(
    row: BinderRow,
    rows: List<BinderRow>,
    list: LazyListState,
    drag: BinderDrag,
    controller: ProjectController,
): Modifier =
    pointerInput(row.path, rows) {
        detectDragGesturesAfterLongPress(
            onDragStart = { at ->
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.path.value }
                drag.source = row.takeIf { it.present }
                drag.y = (item?.offset ?: 0) + at.y
            },
            onDrag = { change, amount ->
                change.consume()
                drag.y += amount.y
                drag.target = targetAt(drag.y, rows, list)
            },
            onDragEnd = {
                val source = drag.source
                val (target, zone) = drag.target ?: (null to null)
                if (source != null && target != null &&
                    zone != null
                ) {
                    dropOf(rows, source, target, zone)?.let { controller.drop(source, it) }
                }
                drag.source = null
                drag.target = null
            },
            onDragCancel = {
                drag.source = null
                drag.target = null
            },
        )
    }

/** The row under [y], in the list's own coordinates, and which part of it. */
private fun targetAt(
    y: Float,
    rows: List<BinderRow>,
    list: LazyListState,
): Pair<BinderRow, DropZone>? {
    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
    val row = item?.let { found -> rows.firstOrNull { it.path.value == found.key } }
    return if (item == null || row == null) null else row to zoneOf((y - item.offset) / item.size, row.isFolder)
}

/**
 * The outer quarter of a row is beside it; the middle of a folder is into it, and the middle of
 * anything else is beside its nearer half.
 */
private fun zoneOf(
    within: Float,
    folder: Boolean,
): DropZone =
    when {
        within < EDGE -> DropZone.Before
        within > 1 - EDGE -> DropZone.After
        folder -> DropZone.Into
        within < HALF -> DropZone.Before
        else -> DropZone.After
    }

/** A drop carried out: a reorder within the folder, or a move to another one. */
private fun ProjectController.drop(
    source: BinderRow,
    drop: Drop,
) = when (drop) {
    is Drop.Reorder -> reorder(drop.folder, drop.names)
    is Drop.Move -> moveTo(source.path, drop.folder, drop.at)
}

/** Where a moved row went, said once to assistive technology: the place it took. */
@Composable
private fun Announced(announcement: Announcement?) {
    val palette = LocalPalette.current
    val text =
        when (announcement) {
            is Announcement.Moved -> {
                stringResource(
                    Res.string.binder_moved,
                    announcement.position.toString(),
                    announcement.of.toString(),
                )
            }

            null -> {
                ""
            }
        }
    BasicText(
        text = text,
        style = interfaceTextStyle(color = palette.muted, fontSize = labelSize),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 10.2: "Touch targets >= 48dp." A row is at least that, whatever is in it. */
private val minimumTarget = 48.dp
private val panelPadding = 12.dp
private val rowGap = 4.dp
private val markGap = 8.dp
private val hairline = 1.dp
private val corner = 6.dp
private val indent = 16.dp
private val labelSize = 14.sp
private val headingSize = 18.sp

/** The outer quarter of a row, top or bottom, is beside it; the middle of a folder is into it. */
private const val EDGE = 0.25f
private const val HALF = 0.5f
private const val DRAGGED_ALPHA = 0.4f
