package com.actum.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import com.actum.ui.act.MicGlyph
import com.actum.ui.act.VoiceSheet
import com.actum.voice.Listener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.actum.data.Memory
import com.actum.embed.EmbedText
import com.actum.imports.ImportBar
import com.actum.open.Originals
import com.actum.ui.keep.rememberKeepState
import com.actum.ui.theme.AmberBright
import com.actum.ui.theme.AmberOnContainer
import com.actum.ui.theme.BoneDim
import com.actum.ui.theme.Ink
import com.actum.act.Actor
import com.actum.ui.act.ActSheet
import com.actum.ui.act.ListsSheet
import com.actum.ui.act.ProofSheet

/**
 * The app's face: the mosaic of everything kept, or the ranked list
 * while searching, with the one search field underneath and the "+" beside it,
 * and one memory opened in a sheet over either, or the "+" sheet.
 *
 * [onOpenDebug] is passed only in debug builds; null, the wordmark has no
 * gesture at all. [keepRequests] counts the launcher shortcut's requests; each
 * new one opens the "+" sheet.
 */
@Composable
fun HomeScreen(
    onOpenDebug: (() -> Unit)?,
    keepRequests: Int = 0,
    viewModel: HomeViewModel = viewModel(),
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    // The proof nothing leaves the phone, opened from "privacy" under the wordmark.
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    // Actum: a request being worked on or waiting for its confirmations, and the lists they fill.
    val acting by viewModel.actor.state.collectAsStateWithLifecycle()
    var listsOpen by rememberSaveable { mutableStateOf(false) }
    // Actum: speaking a request or a diary entry. The listener holds the microphone only while its sheet is up.
    val appContext = LocalContext.current.applicationContext
    val listener = remember { Listener(appContext) }
    val hearing by listener.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { listener.release() } }
    val askToListen = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        // Refused: the sheet still opens, to say why it can't listen.
        if (allowed) listener.start() else listener.refused()
    }
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()
    val context = LocalContext.current

    // The memory opened in the sheet, by id: the row is live, so what is copied back appears in place.
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    // The "+" sheet's panel while it is open. The draft outlives the sheet: it is stored as it changes.
    var plus by rememberSaveable { mutableStateOf<PlusPanel?>(null) }
    val keepState = rememberKeepState()

    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }
    // Opening the app is the cue to get Gemma ready: loaded, with its instructions read in, before
    // anything is asked. (Sharing into Actum does not open this screen, so a save does not load it.)
    LaunchedEffect(Unit) { viewModel.actor.warmUp() }
    // A pause in typing starts reading what is typed, so the wait after "Do this" is shorter. A
    // direct command or a word or two is not read ahead; a change stops the reading under way.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            viewModel.actor.dropEarly()
        } else {
            delay(TYPING_PAUSE_MILLIS)
            viewModel.actor.readAhead(query)
        }
    }
    // Back clears a search before it leaves the app.
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    val searching = searchState is SearchState.Searching
    // The search being left stays drawn while it fades out.
    val lastSearch = remember { LastSearch() }
    (searchState as? SearchState.Searching)?.let { lastSearch.value = it }

    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val hideKeyboardOnDrag = remember(keyboard, focus) { HideKeyboardOnDrag(keyboard, focus) }
    // Opening a memory puts the keyboard away first, so the sheet never rises behind it.
    val open: (Memory) -> Unit = { memory ->
        keyboard?.hide()
        focus.clearFocus()
        openedId = memory.id
    }
    val openPlus: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
        plus = PlusPanel.opening(nothingKept = memories?.isEmpty() == true)
    }

    // Each new request from the launcher shortcut opens the composer, over any memory open.
    var keepRequestsAnswered by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(keepRequests) {
        if (keepRequests > keepRequestsAnswered) {
            keepRequestsAnswered = keepRequests
            keyboard?.hide()
            focus.clearFocus()
            openedId = null
            plus = PlusPanel.COMPOSER
        }
    }

    // One layer over everything, so a picture can travel between its tile and the sheet.
    PictureTravelLayer(openedId = openedId) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Ink)
                    .statusBarsPadding(),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(hideKeyboardOnDrag),
                ) {
                    // While Room has not answered, nothing is drawn.
                    memories?.let { list ->
                        // One "now" per screenful, so ages don't tick apart row by row.
                        val now = remember(list, searching) { System.currentTimeMillis() }
                        remember(list) { viewModel.entrances.submit(list.map { it.id }) }
                        val kept = ResultText.kept(list.size, unsent = 0)

                        AnimatedContent(
                            targetState = searching,
                            transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                            modifier = Modifier.fillMaxSize(),
                            label = "browse or search",
                        ) { isSearching ->
                            val shown = lastSearch.value
                            if (isSearching && shown != null) {
                                // The live rows, so a note written or a memory deleted shows at once.
                                val live = list.associateBy { it.id }
                                val current = shown.copy(
                                    results = shown.results.mapNotNull { r -> live[r.memory.id]?.let { r.copy(memory = it) } },
                                )
                                SearchResults(
                                    state = current,
                                    total = list.size,
                                    now = now,
                                    arrivals = viewModel.arrivals,
                                    onOpen = open,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Mosaic(
                                    memories = list,
                                    kept = kept,
                                    now = now,
                                    entrances = viewModel.entrances,
                                    state = gridState,
                                    onOpen = open,
                                    onLongPressWordmark = onOpenDebug,
                                    onPrivacy = { privacyOpen = true },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    // Content fades under the search field rather than stopping at it.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Ink))),
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ImportBar(importer = viewModel.importer)
                    UndoBar(
                        what = pendingDelete?.let { undoLine(ResultText.title(it)) },
                        onUndo = viewModel::undoDelete,
                    )
                    DoRow(
                        canDo = query.isNotBlank(),
                        onDo = {
                            keyboard?.hide()
                            focus.clearFocus()
                            viewModel.actor.ask(query)
                            // Asked, not searched for: the field is free for the next thing.
                            query = ""
                        },
                        onLists = { listsOpen = true },
                        onSpeak = {
                            keyboard?.hide()
                            focus.clearFocus()
                            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (allowed) listener.start() else askToListen.launch(Manifest.permission.RECORD_AUDIO)
                            viewModel.actor.warmUp()
                        },
                    )
                    SearchRow(
                        query = query,
                        onQueryChange = { query = it },
                        searching = searching,
                        ranking = (searchState as? SearchState.Searching)?.status == SearchStatus.RANKING,
                        hasDraft = keepState.hasDraft,
                        onPlus = openPlus,
                    )
                }
            }

            // The memory opened, in the app's own sheet so its picture can travel into it.
            val list = memories
            val opened = list?.firstOrNull { it.id == openedId }
            // A memory deleted or cleared while open closes its sheet.
            LaunchedEffect(opened == null, list != null) { if (opened == null && list != null) openedId = null }
            SheetLayer(item = opened, onDismiss = { openedId = null }) { memory, visibility ->
                val now = remember(memory.id) { System.currentTimeMillis() }
                val actions = remember(memory) {
                    val action = Originals.actionFor(context, memory)
                    val share = Originals.shareFor(context, memory)
                    DetailActions(
                        main = action,
                        onMain = { action.open(context) },
                        onShare = share?.let { s -> { s.share(context) } },
                        onDelete = {
                            viewModel.delete(memory)
                            openedId = null
                        },
                        onNote = { viewModel.setNote(memory.id, it) },
                        onFindActions = EmbedText.text(memory).takeIf { it.isNotBlank() }?.let { words ->
                            {
                                openedId = null
                                // Read in the background when it was saved, most often: its plain sentences by rule, the rest by Gemma.
                                viewModel.actor.askSaved(memory.id, words)
                            }
                        },
                    )
                }
                // Nothing is ever sent, so the detail says nothing about sending.
                CompositionLocalProvider(LocalCloudReads provides false) {
                    MemoryDetail(memory, now, visibility, actions)
                }
            }

            SheetLayer(item = if (privacyOpen) Unit else null, onDismiss = { privacyOpen = false }) { _, _ ->
                ProofSheet()
            }

            SheetLayer(item = if (listsOpen) Unit else null, onDismiss = { listsOpen = false }) { _, _ ->
                ListsSheet(viewModel)
            }

            // Speaking: the words as they are heard, then where to send them.
            SheetLayer(
                item = hearing.takeIf { it !is Listener.State.Idle },
                onDismiss = { listener.reset() },
                maxFraction = 0.92f,
            ) { state, _ ->
                VoiceSheet(
                    state = state,
                    listener = listener,
                    onDo = { words ->
                        listener.reset()
                        viewModel.actor.ask(words)
                    },
                    onDiary = { words, done ->
                        viewModel.actor.dropEarly()
                        viewModel.actor.writeDiary(words, context, done)
                    },
                    // Read as soon as the speech is words, and again whenever they are corrected.
                    onWords = viewModel.actor::readAhead,
                    onClose = {
                        viewModel.actor.dropEarly()
                        listener.reset()
                    },
                )
            }

            // Actum at work: progress, then a card per action, each waiting for its own tap.
            SheetLayer(
                item = acting.takeIf { it !is Actor.State.Idle },
                onDismiss = { viewModel.actor.clear() },
                maxFraction = 0.92f,
            ) { state, _ ->
                ActSheet(state, viewModel.actor, onClose = { viewModel.actor.clear() })
            }

            // Keeping something from inside the app, or bringing in what's already on the phone.
            SheetLayer(item = plus, onDismiss = { plus = null }) { panel, _ ->
                PlusSheet(
                    panel = panel,
                    keepState = keepState,
                    importer = viewModel.importer,
                    onPanel = { plus = it },
                    onClose = { plus = null },
                )
            }
        }
    }
}

/** Above the search field: do what is typed, or open what was done before. */
@Composable
private fun DoRow(canDo: Boolean, onDo: () -> Unit, onLists: () -> Unit, onSpeak: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onLists, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("to-dos · calendar · more", style = monoStyle(12.sp, BoneDim))
        }
        Spacer(Modifier.weight(1f))
        // With nothing typed, the microphone: say it instead.
        AnimatedVisibility(visible = !canDo, enter = fadeIn(tween(160)), exit = fadeOut(tween(80))) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.4.dp, AmberBright, CircleShape)
                    .clickable(onClickLabel = "Speak to Actum", onClick = onSpeak),
                contentAlignment = Alignment.Center,
            ) {
                MicGlyph(Modifier.size(22.dp), AmberBright)
            }
        }
        AnimatedVisibility(visible = canDo, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Button(
                onClick = onDo,
                colors = ButtonDefaults.buttonColors(containerColor = AmberBright, contentColor = AmberOnContainer),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Do this", style = sans(15.sp, AmberOnContainer, FontWeight.SemiBold)) }
        }
    }
}

private class LastSearch {
    var value: SearchState.Searching? = null
}

/** A drag on the content puts the keyboard away; a list moving on its own leaves it be. */
private class HideKeyboardOnDrag(
    private val keyboard: SoftwareKeyboardController?,
    private val focus: FocusManager,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
            keyboard?.hide()
            focus.clearFocus()
        }
        return Offset.Zero
    }
}

/** How long typing must pause before what is typed is read ahead. */
private const val TYPING_PAUSE_MILLIS = 900L
