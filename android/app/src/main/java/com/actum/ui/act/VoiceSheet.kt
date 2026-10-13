package com.actum.ui.act

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actum.act.Outcome
import com.actum.ui.common.Grabber
import com.actum.ui.home.monoStyle
import com.actum.ui.home.sans
import com.actum.ui.home.serif
import com.actum.ui.theme.AmberBright
import com.actum.ui.theme.AmberOnContainer
import com.actum.ui.theme.Bone
import com.actum.ui.theme.BoneDim
import com.actum.ui.theme.InkOutline
import com.actum.voice.Listener

/**
 * Speaking to Actum. While it listens, the words appear as they are made out.
 * Then they are the person's to correct, and to send one of two ways: to be
 * read for actions, like anything typed, or straight into the diary as they
 * stand. Nothing is done with them until one of those is tapped.
 */
@Composable
fun ColumnScope.VoiceSheet(
    state: Listener.State,
    listener: Listener,
    onDo: (String) -> Unit,
    onDiary: (String, (Outcome) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    // The words once heard, as corrected by hand. Refilled by each new take.
    var words by remember { mutableStateOf("") }
    var written by remember { mutableStateOf<Outcome?>(null) }
    LaunchedEffect(state) { if (state is Listener.State.Heard) words = state.text }

    Grabber(Modifier.align(Alignment.CenterHorizontally))
    Column(
        Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            Listener.State.Idle -> Unit
            is Listener.State.Listening -> {
                Text("Listening", style = serif(26.sp, lineHeight = 30.sp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Pulse(state.level)
                    Text(
                        state.heard.ifEmpty { "Say what you want done, or what happened today." },
                        style = sans(18.sp, if (state.heard.isEmpty()) BoneDim else Bone, lineHeight = 26.sp),
                        modifier = Modifier.weight(1f),
                    )
                }
                Act("I'm done", AmberBright, AmberOnContainer, onClick = listener::stop)
            }
            is Listener.State.Heard -> when (val outcome = written) {
                null -> {
                    Text(if (words.isBlank() && state.text.isBlank()) "Didn't catch that" else "You said", style = serif(26.sp, lineHeight = 30.sp))
                    OutlinedTextField(
                        value = words,
                        onValueChange = { words = it },
                        placeholder = { Text("nothing was made out · type here, or try again", style = monoStyle(12.sp, InkOutline)) },
                        textStyle = sans(17.sp, Bone, lineHeight = 25.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberBright, unfocusedBorderColor = InkOutline, cursorColor = AmberBright),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (words.isNotBlank()) {
                        Act("Do this", AmberBright, AmberOnContainer) { onDo(words.trim()) }
                        Act("Save to diary", DiaryRose, OnIntent) { onDiary(words.trim()) { written = it } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (words.isNotBlank()) Link("say more") { listener.start(adding = true, soFar = words) }
                        Link(if (words.isBlank()) "try again" else "start over") { listener.start() }
                        Link("discard", onClose)
                    }
                }
                is Outcome.Done -> {
                    Text("In your diary", style = serif(26.sp, lineHeight = 30.sp))
                    Said(outcome.what, DiaryRose)
                    Act("Done", AmberBright, AmberOnContainer, onClose)
                }
                is Outcome.Failed -> {
                    Text("Not saved", style = serif(26.sp, lineHeight = 30.sp))
                    Said(outcome.why, Missing)
                    Link("back") { written = null }
                }
                Outcome.Skipped -> Unit
            }
            is Listener.State.Problem -> {
                Text("Can't listen", style = serif(26.sp, lineHeight = 30.sp))
                Text(state.reason, style = sans(15.sp, BoneDim, lineHeight = 22.sp))
                if (state.canFetch) Act("Ask Android for the speech pack", AmberBright, AmberOnContainer, onClick = listener::fetchPack)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Link("try again") { listener.start() }
                    Link("close", onClose)
                }
            }
        }
        Text("heard by this phone's own recognizer · Actum sends nothing", style = monoStyle(11.sp, InkOutline))
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

/** The microphone, in a ring that swells with the voice. */
@Composable
private fun Pulse(level: Float) {
    val swell by animateFloatAsState(1f + 0.55f * level, spring(dampingRatio = 0.5f, stiffness = 300f), label = "voice level")
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = swell
                    scaleY = swell
                }
                .background(tint(AmberBright, 0.28f), CircleShape),
        )
        MicGlyph(Modifier.size(24.dp), AmberBright)
    }
}

@Composable
private fun Said(words: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().background(tint(color, 0.14f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(words, style = sans(14.sp, color, FontWeight.Medium, lineHeight = 20.sp))
    }
}

@Composable
private fun Act(label: String, fill: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = ink),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(label, style = sans(15.sp, ink, FontWeight.SemiBold)) }
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Text(label, style = monoStyle(12.sp, BoneDim), modifier = Modifier.clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 6.dp))
}
