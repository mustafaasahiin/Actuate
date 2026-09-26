package com.actuate.app.ui.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.actuate.app.R
import com.actuate.core.components.BoldEyebrow
import com.actuate.core.components.CellPosition
import com.actuate.core.components.CupertinoGroupedCard
import com.actuate.core.components.CupertinoGroupedCell
import com.actuate.core.components.FeedbackRow
import com.actuate.core.components.cellShape
import com.actuate.core.haptics.rememberCupertinoHaptics
import com.actuate.core.theme.AppleBlue
import com.actuate.core.theme.Ash
import com.actuate.core.theme.AshDark
import com.actuate.core.theme.Capsule
import com.actuate.core.theme.CobaltVivid
import com.actuate.core.theme.DeepGraphite
import com.actuate.core.theme.ElevatedDark
import com.actuate.core.theme.Frost
import com.actuate.core.theme.Graphite
import com.actuate.core.theme.Ice
import com.actuate.core.theme.Mist
import com.actuate.core.theme.MistDark
import com.actuate.core.theme.NotionIndigo
import com.actuate.core.theme.Pebble
import com.actuate.core.theme.SignalBlue
import com.actuate.core.theme.SolarAmber
import com.actuate.core.theme.Spacing
import com.actuate.core.theme.Verge
import com.actuate.core.theme.VividEmerald
import com.actuate.domain.model.ServerListItem
import java.util.Locale

@Composable
fun ListsTab(
    state: SectionsState,
    onConnect: () -> Unit,
    onToggle: (ServerListItem) -> Unit,
    onTrySample: (String) -> Unit = {},
    onCreateList: (String) -> Unit = {},
    onRenameList: (String, String) -> Unit = { _, _ -> },
    onDeleteList: (String) -> Unit = {},
    onEditItem: (ServerListItem, String) -> Unit = { _, _ -> },
    onDeleteItem: (ServerListItem) -> Unit = {},
    onAddItem: (String, String) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCupertinoHaptics()
    val isDark = isSystemInDarkTheme()

    var selectedFilter by rememberSaveable { mutableStateOf("all") }
    var inlineInputText by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    var showCreateListDialog by rememberSaveable { mutableStateOf(false) }
    var listToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var listToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var itemToEdit by remember { mutableStateOf<ServerListItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ServerListItem?>(null) }

    val allLists = state.lists
    val allItems = allLists.flatMap { it.items }
    val totalCount = allItems.size
    val completedCount = allItems.count { it.done }

    val filteredLists = if (selectedFilter == "all") {
        allLists
    } else {
        allLists.filter { it.name.lowercase() == selectedFilter.lowercase() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(Capsule),
                color = AppleBlue,
            )
        }

        if (state.error != null) {
            Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                FeedbackRow(
                    message = state.error ?: "Viewing cached content · Server sync pending",
                    action = "Retry",
                    onAction = onRefresh,
                )
            }
        }

        when {
            state.lists.isEmpty() -> ListsEmptyState(onTrySample = onTrySample)
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.md),
            ) {
            item(key = "progress-and-filters") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (totalCount > 0) {
                            Text(
                                text = stringResource(R.string.lists_progress_metric, completedCount, totalCount),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "Your Lists",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            onClick = {
                                haptics.selectionChanged()
                                showCreateListDialog = true
                            },
                            shape = Capsule,
                            color = if (isDark) ElevatedDark else Ice,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = AppleBlue, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New List", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = AppleBlue)
                            }
                        }
                    }

                    if (totalCount > 0) {
                        val progressFraction by animateFloatAsState(
                            targetValue = completedCount.toFloat() / totalCount.toFloat(),
                            animationSpec = tween(400, easing = FastOutSlowInEasing),
                            label = "listProgress",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (completedCount == totalCount) Verge else AppleBlue,
                            )
                        }
                        Spacer(Modifier.height(Spacing.xxs))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(Capsule),
                            color = if (completedCount == totalCount) Verge else AppleBlue,
                            trackColor = if (isDark) ElevatedDark else Pebble,
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }

                    val availableCategories = listOf("all") + allLists.map { it.name.lowercase() }.distinct()
                    if (availableCategories.size > 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            availableCategories.forEach { category ->
                                val isSelected = selectedFilter == category
                                val count = if (category == "all") {
                                    totalCount
                                } else {
                                    allLists.filter { it.name.lowercase() == category }.flatMap { it.items }.size
                                }
                                val label = if (category == "all") "All" else category.replaceFirstChar { it.titlecase(Locale.ROOT) }

                                Surface(
                                    onClick = {
                                        haptics.selectionChanged()
                                        selectedFilter = category
                                    },
                                    shape = Capsule,
                                    color = if (isSelected) AppleBlue else if (isDark) ElevatedDark else Ice,
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (isSelected) AppleBlue else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            ),
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                }
            }

            filteredLists.forEach { list ->
                item(key = "header-${list.name}") {
                    // Notion-grade header: emoji identity plus a completion ring, which
                    // communicates "4 of 7 done" far faster than a 4/7 capsule does.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm, bottom = Spacing.xs, start = Spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CyberCategoryHeader(
                            name = list.name,
                            done = list.items.count { it.done },
                            total = list.items.size,
                            modifier = Modifier.weight(1f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var listMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = {
                                        haptics.selectionChanged()
                                        listMenuExpanded = true
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "List actions for ${list.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = listMenuExpanded,
                                    onDismissRequest = { listMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename List") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            listMenuExpanded = false
                                            listToRename = list.name
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete List", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            listMenuExpanded = false
                                            listToDelete = list.name
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "card-${list.name}") {
                    CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
                        list.items.forEachIndexed { index, item ->
                            val position = when {
                                list.items.size == 1 -> CellPosition.SINGLE
                                index == 0 -> CellPosition.FIRST
                                index == list.items.size - 1 -> CellPosition.LAST
                                else -> CellPosition.MIDDLE
                            }

                            GroupedTaskItemRow(
                                item = item,
                                position = position,
                                onToggle = {
                                    if (!item.done) haptics.impactSuccess() else haptics.impactLight()
                                    onToggle(item)
                                },
                                onEdit = { itemToEdit = item },
                                onDelete = { itemToDelete = item },
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            item(key = "quick-add-footer") {
                CupertinoGroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs, bottom = Spacing.xxl),
                    radius = 12.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Item",
                            tint = AppleBlue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        OutlinedTextField(
                            value = inlineInputText,
                            onValueChange = { inlineInputText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.add_task_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) AshDark else Ash,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val text = inlineInputText.trim()
                                    if (text.isNotEmpty()) {
                                        haptics.impactLight()
                                        inlineInputText = ""
                                        focusManager.clearFocus()
                                        val targetList = if (selectedFilter != "all") selectedFilter else (allLists.firstOrNull()?.name ?: "Todo")
                                        onAddItem(targetList, text)
                                    }
                                },
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = AppleBlue,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (inlineInputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val text = inlineInputText.trim()
                                    if (text.isNotEmpty()) {
                                        haptics.impactLight()
                                        inlineInputText = ""
                                        focusManager.clearFocus()
                                        val targetList = if (selectedFilter != "all") selectedFilter else (allLists.firstOrNull()?.name ?: "Todo")
                                        onAddItem(targetList, text)
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Submit",
                                    tint = AppleBlue,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showCreateListDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text("New List", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("List name (e.g. Groceries, Projects)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue,
                        cursorColor = AppleBlue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            haptics.impactSuccess()
                            onCreateList(trimmed)
                            showCreateListDialog = false
                        }
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Create", color = if (name.isNotBlank()) AppleBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    listToRename?.let { currentName ->
        var newName by rememberSaveable(currentName) { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { listToRename = null },
            title = { Text("Rename List", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("List name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue,
                        cursorColor = AppleBlue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty() && trimmed != currentName) {
                            haptics.impactSuccess()
                            onRenameList(currentName, trimmed)
                        }
                        listToRename = null
                    },
                    enabled = newName.isNotBlank() && newName.trim() != currentName,
                ) {
                    Text("Save", color = AppleBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { listToRename = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    listToDelete?.let { targetList ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Delete \"$targetList\"?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = { Text("This will delete the list and all items in it. You can undo this action.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.impactWarning()
                        onDeleteList(targetList)
                        listToDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    itemToEdit?.let { item ->
        var text by rememberSaveable(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = { Text("Edit Item", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Item text") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppleBlue,
                        cursorColor = AppleBlue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty() && trimmed != item.text) {
                            haptics.impactSuccess()
                            onEditItem(item, trimmed)
                        }
                        itemToEdit = null
                    },
                    enabled = text.isNotBlank() && text.trim() != item.text,
                ) {
                    Text("Save", color = AppleBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Item?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = { Text("\"${item.text}\" will be removed. You can undo this action.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.impactWarning()
                        onDeleteItem(item)
                        itemToDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun GroupedTaskItemRow(
    item: ServerListItem,
    position: CellPosition,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = cellShape(position, 16.dp)

    val checkColor by animateColorAsState(
        targetValue = if (item.done) Verge else if (isDark) MistDark else Mist.copy(alpha = 0.5f),
        animationSpec = tween(250),
        label = "checkColor",
    )

    // Swipe right to complete (or reopen), swipe left to delete — the two gestures a
    // list item needs, with the keyboard-free path preserved through the checkbox.
    SwipeActionRow(
        onSwipeRight = onToggle,
        onSwipeLeft = onDelete,
        rightLabel = if (item.done) "Reopen" else "Complete",
        leftLabel = "Delete",
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
        ) {
            CyberCheckbox(
                done = item.done,
                onToggle = onToggle,
                contentDescription = if (item.done) {
                    stringResource(R.string.not_done)
                } else {
                    stringResource(R.string.strike_through_hint)
                },
            )

            Spacer(Modifier.width(Spacing.sm))

            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClickLabel = "Edit item",
                        onClick = onEdit,
                    ),
            )

            var itemMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = {
                        itemMenuExpanded = true
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Item actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = itemMenuExpanded,
                    onDismissRequest = { itemMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            itemMenuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            itemMenuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }

        if (position == CellPosition.FIRST || position == CellPosition.MIDDLE) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp),
                thickness = 0.5.dp,
                color = if (isDark) MistDark else Mist.copy(alpha = 0.3f),
            )
        }
    }
    }
}

@Composable
internal fun ListsEmptyState(onTrySample: (String) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val badgeBg = if (isDark) ElevatedDark else Ice
    val haptics = rememberCupertinoHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(badgeBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Checklist,
                contentDescription = null,
                tint = SignalBlue,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.no_lists_yet),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.no_lists_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.md),
        )
        Spacer(Modifier.height(Spacing.lg))

        CupertinoGroupedCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp) {
            CupertinoGroupedCell(
                title = "Put milk and eggs on shopping list",
                subtitle = "Lists · Adds to 'Shopping' list",
                icon = Icons.Rounded.ShoppingCart,
                iconBackground = Verge,
                position = CellPosition.FIRST,
                onClick = {
                    haptics.selectionChanged()
                    onTrySample("Put milk and eggs on shopping list")
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Run",
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            CupertinoGroupedCell(
                title = "Add buy groceries to my todo list",
                subtitle = "Lists · Adds to 'To-Do' list",
                icon = Icons.Rounded.Checklist,
                iconBackground = SignalBlue,
                position = CellPosition.MIDDLE,
                onClick = {
                    haptics.selectionChanged()
                    onTrySample("Add buy groceries and call mechanic to my todo list")
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Run",
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            CupertinoGroupedCell(
                title = "Add review pitch deck to work list",
                subtitle = "Lists · Adds to 'Work' list",
                icon = Icons.Rounded.TaskAlt,
                iconBackground = AppleBlue,
                position = CellPosition.LAST,
                onClick = {
                    haptics.selectionChanged()
                    onTrySample("Add review pitch deck to work list")
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Run",
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
internal fun ConnectHint(onConnect: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val buttonBg = if (isDark) ElevatedDark else Ice

    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        CupertinoGroupedCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.sync_your_lists),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.sync_lists_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    onClick = onConnect,
                    shape = Capsule,
                    color = buttonBg,
                    border = BorderStroke(1.dp, if (isDark) MistDark else Mist.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = stringResource(R.string.open_settings),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppleBlue,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = AppleBlue,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp),
        )
    }
}
