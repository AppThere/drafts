package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Lucide
import com.appthere.drafts.design.WidthClass
import com.appthere.drafts.design.WindowSize
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.binder
import com.appthere.drafts.i18n.resources.close
import com.appthere.drafts.i18n.resources.open_trash
import com.appthere.drafts.i18n.resources.orphan_offer
import com.appthere.drafts.i18n.resources.outcome_conflict
import com.appthere.drafts.i18n.resources.outcome_failed
import com.appthere.drafts.i18n.resources.outcome_history
import com.appthere.drafts.i18n.resources.outcome_inside
import com.appthere.drafts.i18n.resources.outcome_newer
import com.appthere.drafts.i18n.resources.outcome_not_writable
import com.appthere.drafts.i18n.resources.outcome_taken
import com.appthere.drafts.i18n.resources.relink_accept
import com.appthere.drafts.i18n.resources.relink_decline
import com.appthere.drafts.i18n.resources.relink_folder_offer
import com.appthere.drafts.i18n.resources.relink_offer
import com.appthere.drafts.i18n.resources.remove
import com.appthere.drafts.project.ManifestState
import com.appthere.drafts.project.Offer
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectPath
import org.jetbrains.compose.resources.stringResource

/**
 * A project window (`projects/project-windows`): the binder beside an editor pane. On a Compact
 * width the binder is a drawer and the editor is where the window opens; opening a document from
 * the drawer closes it, and Back closes it too.
 *
 * [editor] is the host's: the document the writer opened, in the same editor a document window has,
 * placed here to fill what the binder leaves.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProjectWindow(
    controller: ProjectController,
    modifier: Modifier = Modifier,
    editor: @Composable () -> Unit,
) {
    val ui by controller.ui.collectAsState()
    val view by controller.view.collectAsState()
    var drawer by remember { mutableStateOf(false) }
    // One editor, whichever layout it is in: moving between them keeps the document's state.
    val pane =
        remember(editor) {
            movableContentOf { paneModifier: Modifier ->
                Box(paneModifier.fillMaxSize()) { editor() }
            }
        }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = WindowSize.of(maxWidth, maxHeight).width == WidthClass.Compact
        LaunchedEffect(ui.selected) { if (compact) drawer = false }
        BackHandler(enabled = compact && drawer) { drawer = false }

        if (compact) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(edge)) {
                    PanelIconButton(icon = Lucide.ListTree, description = stringResource(Res.string.binder), onClick = {
                        drawer =
                            true
                    })
                }
                Notices(controller, Modifier.padding(horizontal = edge))
                pane(Modifier.weight(1f))
            }
            if (drawer) Binder(controller, Modifier.fillMaxHeight().widthIn(max = drawerWidth))
        } else {
            Row(Modifier.fillMaxSize()) {
                Binder(controller, Modifier.width(binderWidth).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Notices(controller, Modifier.padding(edge))
                    pane(Modifier.weight(1f))
                }
            }
        }

        val prompt = ui.prompt
        val current = view
        if (prompt != null && current != null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { BinderPrompts(controller, current, prompt) }
        }
    }
}

/**
 * What the writer needs to know or answer, above the editor: a change that could not be made, the
 * relinks reconciling has offered, and notes left by files deleted outside the app.
 */
@Composable
private fun Notices(
    controller: ProjectController,
    modifier: Modifier = Modifier,
) {
    val ui by controller.ui.collectAsState()
    val view by controller.view.collectAsState()
    var orphans by remember { mutableStateOf<List<ProjectPath>>(emptyList()) }
    LaunchedEffect(view?.complete) { orphans = controller.trashCan.orphans() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        ui.problem?.let { problem ->
            NoticeCard(problemText(problem, view?.manifest)) {
                val close = stringResource(Res.string.close)
                PanelButton(text = close, description = close, onClick = controller::dismissProblem)
            }
        }
        view
            ?.pending
            ?.offers
            .orEmpty()
            .forEach { offer -> OfferNotice(offer, controller) }
        orphans.firstOrNull()?.let { path ->
            NoticeCard(stringResource(Res.string.orphan_offer, path.name)) {
                val remove = stringResource(Res.string.remove)
                PanelButton(text = remove, description = remove, onClick = {
                    controller.trashCan.removeOrphan(path).also {
                        orphans =
                            orphans - path
                    }
                })
            }
        }
        val trash = stringResource(Res.string.open_trash)
        PanelButton(text = trash, description = trash, onClick = { controller.ask(BinderPrompt.Trash) })
    }
}

@Composable
private fun OfferNotice(
    offer: Offer,
    controller: ProjectController,
) {
    val from = ProjectPath(offer.from).name
    val to = ProjectPath(offer.to).name
    val text = stringResource(if (offer.folder) Res.string.relink_folder_offer else Res.string.relink_offer, from, to)
    NoticeCard(text) {
        val link = stringResource(Res.string.relink_accept)
        val keep = stringResource(Res.string.relink_decline)
        PanelButton(text = link, description = link, onClick = { controller.answers.accept(offer) })
        PanelButton(text = keep, description = keep, onClick = { controller.answers.decline(offer) })
    }
}

/** What to tell the writer about a change that was not made -- in their terms, not the code's. */
@Composable
fun problemText(
    problem: Problem,
    manifest: ManifestState?,
): String =
    when (problem.outcome) {
        Outcome.Taken -> {
            stringResource(Res.string.outcome_taken, problem.name)
        }

        Outcome.Conflict -> {
            stringResource(Res.string.outcome_conflict)
        }

        Outcome.NotWritable -> {
            stringResource(
                if ((manifest as? ManifestState.NotWritable)?.newer ==
                    true
                ) {
                    Res.string.outcome_newer
                } else {
                    Res.string.outcome_not_writable
                },
            )
        }

        Outcome.HasHistory -> {
            stringResource(Res.string.outcome_history, problem.name)
        }

        Outcome.InsideProject -> {
            stringResource(Res.string.outcome_inside)
        }

        Outcome.Done, is Outcome.Failed -> {
            stringResource(Res.string.outcome_failed)
        }
    }

/** A notice: what happened, in words, said once to assistive technology, and what can be done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeCard(
    text: String,
    choices: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(cardPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        BasicText(
            text,
            style = interfaceTextStyle(color = palette.ink, fontSize = bodySize),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            choices()
        }
    }
}

private val binderWidth = 320.dp
private val drawerWidth = 360.dp
private val edge = 8.dp
private val gap = 8.dp
private val cardPadding = 12.dp
private val corner = 6.dp
private val hairline = 1.dp
private val bodySize = 14.sp
