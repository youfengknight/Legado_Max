package io.legado.app.ui.book.readRecord

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageCardElevatedContainerColor
import io.legado.app.ui.theme.pageHeaderContainerColor
import io.legado.app.ui.theme.pageMutedIconTint
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.theme.pageSurfaceVariantColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import androidx.compose.ui.graphics.luminance

@Composable
fun readRecordTopBarContainerColor() = pageTopBarContainerColor()

@Composable
fun readRecordCardContainerColor() = pageCardElevatedContainerColor()

@Composable
fun readRecordSummaryCardContainerColor() = pageCardElevatedContainerColor()

@Composable
fun readRecordHeaderContainerColor() = pageHeaderContainerColor()

@Composable
fun readRecordSecondaryTextColor() = pageSecondaryTextColor()

@Composable
fun readRecordTimelineAccentColor() = pageAccentColor()

@Composable
fun readRecordBookStackSurfaceColor() = pageSurfaceVariantColor()

@Composable
fun readRecordMutedIconTint() = pageMutedIconTint()

@Composable
fun readRecordCardBorder(): BorderStroke? {
    val background = MaterialTheme.colorScheme.background
    return if (background.luminance() < 0.18f) {
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
        )
    } else {
        null
    }
}
