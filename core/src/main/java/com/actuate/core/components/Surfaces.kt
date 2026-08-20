package com.actuate.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.Spacing

/** 8dp container with hairline border — separation via borders, never shadows. */
@Composable
fun ActuateCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (highlighted) Ice else Color.White, RoundedCornerShape(8.dp))
            .padding(Spacing.md),
        content = content,
    )
}

/** 1dp hairline divider in Mist. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = Mist,
    )
}

/** Section label — caption style, Ash color. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Small status chip. Filled surfaces only — no shadows. */
@Composable
fun StatusChip(text: String, background: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}