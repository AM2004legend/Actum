package com.actum.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actum.ui.theme.AmberBright
import com.actum.ui.theme.Bone
import com.actum.ui.theme.BoneDim
import com.actum.ui.theme.Mono
import com.actum.model.GemmaRouter

/** Milestone M1 on the phone: run the fixed sentence, read the times. Replaced by Home later. */
@Composable
fun ModelGateScreen(state: ModelGate.State, onMeasure: (GemmaRouter.Processor) -> Unit) {
    val mono = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp, color = BoneDim)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Actum", style = MaterialTheme.typography.headlineMedium, color = Bone)
        Text("Model check · ${ModelGate.RUNS} runs of one sentence", style = mono)
        Text("“${ModelGate.SENTENCE}”", style = MaterialTheme.typography.bodyLarge, color = Bone)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onMeasure(GemmaRouter.Processor.CPU) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text("Run on CPU") }
            OutlinedButton(
                onClick = { onMeasure(GemmaRouter.Processor.GPU) },
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text("Run on GPU") }
        }

        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.status.isNotEmpty()) Text(state.status, style = mono.copy(color = Bone))

        state.runs.forEachIndexed { index, run ->
            Text(
                "${index + 1}.  ${"%.1f s".format(run.millis / 1000.0)}  ${run.outcome}",
                style = mono.copy(color = if (run.passed) Bone else MaterialTheme.colorScheme.error),
            )
        }
        state.summary?.let { Text(it, style = mono.copy(color = AmberBright)) }
        state.answer?.let {
            Text("last answer", style = mono)
            Text(it, style = mono.copy(fontSize = 11.sp, lineHeight = 16.sp))
        }
    }
}
