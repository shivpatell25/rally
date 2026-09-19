package com.shiv.rally.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A TV shelf that deliberately advances in pages instead of allowing a LazyRow to
 * drag the viewport one card at a time. Exactly [pageSize] equal-width slots are
 * presented. Pressing right from the last visible card reveals the next page and
 * puts focus on its first card; left from the first card performs the inverse.
 */
@Composable
fun <T> RallyPagedRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    pageSize: Int = 5,
    spacing: Dp = 10.dp,
    firstFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    itemContent: @Composable (item: T, modifier: Modifier, width: Dp) -> Unit
) {
    val stableKeys = remember(items) { items.map(key) }
    val pageCount = ((items.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    var page by remember(stableKeys, pageSize) { mutableIntStateOf(0) }
    var pendingFocusSlot by remember { mutableIntStateOf(-1) }
    val pageFocusRequesters = remember(pageSize) { List(pageSize) { FocusRequester() } }

    LaunchedEffect(pageCount) {
        if (page >= pageCount) page = pageCount - 1
    }
    LaunchedEffect(page, pendingFocusSlot) {
        val target = pendingFocusSlot
        if (target >= 0) {
            delay(32)
            runCatching { pageFocusRequesters[target].requestFocus() }
            pendingFocusSlot = -1
        }
    }

    val pageItems = items.drop(page * pageSize).take(pageSize)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - spacing * (pageSize - 1)) / pageSize
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            pageItems.forEachIndexed { slot, item ->
                var itemModifier = Modifier
                    .focusRequester(pageFocusRequesters[slot])
                    .focusProperties {
                        upFocusRequester?.let { up = it }
                        downFocusRequester?.let { down = it }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // Ignore key-repeat bursts while a page transition is handing
                        // focus to its destination. This prevents stale card nodes from
                        // starting a second transition on slower TV hardware.
                        if (pendingFocusSlot >= 0) return@onPreviewKeyEvent true
                        when {
                            event.key == Key.DirectionRight && slot == pageItems.lastIndex -> {
                                if (page < pageCount - 1) {
                                    page += 1
                                    pendingFocusSlot = 0
                                }
                                true
                            }
                            event.key == Key.DirectionLeft && slot == 0 && page > 0 -> {
                                page -= 1
                                pendingFocusSlot = pageSize - 1
                                true
                            }
                            else -> false
                        }
                    }
                if (page == 0 && slot == 0 && firstFocusRequester != null) {
                    itemModifier = itemModifier.focusRequester(firstFocusRequester)
                }
                itemContent(item, itemModifier, cardWidth)
            }
        }
    }
}
