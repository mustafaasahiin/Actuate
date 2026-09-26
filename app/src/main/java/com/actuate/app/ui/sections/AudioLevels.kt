package com.actuate.app.ui.sections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** A rolling history of measured microphone RMS, never a generated waveform. */
@Composable
fun AudioLevels(db: Float) {
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(db) {
        samples.add(((db + 60f) / 60f).coerceIn(0f, 1f))
        if (samples.size > 24) samples.removeAt(0)
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.width(144.dp).height(24.dp).semantics { contentDescription = "Live microphone input level" }) {
        samples.forEachIndexed { index, level ->
            val x = (index + 0.5f) * size.width / 24
            val half = (size.height / 2 * level).coerceAtLeast(1.dp.toPx())
            drawLine(color, Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}
