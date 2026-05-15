package io.legado.app.ui.book.readRecord.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.readRecord.readRecordBookStackSurfaceColor
import io.legado.app.ui.book.readRecord.readRecordCardBorder
import io.legado.app.ui.book.readRecord.readRecordMutedIconTint
import io.legado.app.ui.book.readRecord.readRecordSecondaryTextColor
import io.legado.app.ui.book.readRecord.readRecordSummaryCardContainerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SummaryCard(
    totalReadTime: Long,
    bookCount: Int,
    latestRecords: List<ReadRecord>,
    viewModel: ReadRecordViewModel
) {
    val hours = totalReadTime / (1000 * 60 * 60)
    val minutes = (totalReadTime / (1000 * 60)) % 60
    val timeString = if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
    val shape = RoundedCornerShape(16.dp)
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.18f
    val cardColor = if (isDarkBackground) {
        lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
            0.72f
        )
    } else {
        readRecordSummaryCardContainerColor()
    }
    val border = readRecordCardBorder()
    val secondaryTextColor = readRecordSecondaryTextColor()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(if (isDarkBackground) 0.dp else 8.dp, shape, clip = false),
        shape = shape,
        color = cardColor,
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "累计阅读成就",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "已读 ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$bookCount",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " 本书",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "共阅读 $timeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }

            if (latestRecords.isNotEmpty()) {
                BookStackView(
                    records = latestRecords.take(3),
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun BookStackView(
    records: List<ReadRecord>,
    viewModel: ReadRecordViewModel
) {
    val context = LocalContext.current
    val xOffsetStep = 12.dp
    val stackWidth = 48.dp + (xOffsetStep * (records.size - 1).coerceAtLeast(0))
    val stackSurfaceColor = readRecordBookStackSurfaceColor()
    val iconTint = readRecordMutedIconTint()

    val coverBitmaps = remember { mutableStateOf<Map<Int, Bitmap?>>(emptyMap()) }

    LaunchedEffect(records) {
        withContext(Dispatchers.IO) {
            val bitmaps = mutableMapOf<Int, Bitmap?>()
            records.forEachIndexed { index, record ->
                val coverPath = viewModel.getBookCover(record.bookName, record.bookAuthor)
                if (coverPath != null) {
                    try {
                        bitmaps[index] = ImageLoader.loadBitmap(context, coverPath)
                            .submit()
                            .get()
                    } catch (e: Exception) {
                        bitmaps[index] = null
                    }
                } else {
                    bitmaps[index] = null
                }
            }
            coverBitmaps.value = bitmaps
        }
    }

    Box(
        modifier = Modifier
            .width(stackWidth)
            .height(72.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        records.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .padding(start = xOffsetStep * index)
                    .zIndex(index.toFloat())
                    .rotate(if (index % 2 == 0) 3f else -3f)
            ) {
                Surface(
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(4.dp),
                    color = stackSurfaceColor
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = coverBitmaps.value[index]
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.width(24.dp).height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
