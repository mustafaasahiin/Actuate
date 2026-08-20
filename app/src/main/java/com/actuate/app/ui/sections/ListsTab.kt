package com.actuate.app.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.actuate.core.components.ActuateCard
import com.actuate.core.theme.Carbon
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.domain.model.ServerListItem
import java.util.Locale

/** "Lists" tab: list items grouped by list name with tappable check-offs. */
@Composable
fun ListsTab(
    state: SectionsState,
    onConnect: () -> Unit,
    onToggle: (ServerListItem) -> Unit,
) {
    when {
        !state.serverConfigured -> ConnectHint(onConnect)
        state.loading && state.lists.isEmpty() -> LoadingPlaceholder()
        state.lists.isEmpty() -> EmptySection(
            title = "No lists yet",
            body = "Say \"add buy chicken to my shopping list\" and it will land here.",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
        ) {
            state.lists.forEach { list ->
                item(key = "header-${list.name}") {
                    SectionHeader(list.name.replaceFirstChar { it.titlecase(Locale.ROOT) })
                    Spacer(Modifier.height(Spacing.sm))
                }
                items(list.items, key = { it.id }) { item ->
                    ListItemRow(item, onToggle = { onToggle(item) })
                    Spacer(Modifier.height(Spacing.xs))
                }
                item(key = "spacer-${list.name}") { Spacer(Modifier.height(Spacing.lg)) }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}

@Composable
private fun ListItemRow(item: ServerListItem, onToggle: () -> Unit) {
    ActuateCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
        ) {
            Icon(
                imageVector = if (item.done) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = if (item.done) "Done" else "Not done",
                tint = if (item.done) Verge else Graphite,
            )
            Spacer(Modifier.padding(horizontal = Spacing.xs))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) Graphite else Carbon,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}