package com.nuvio.app.features.livetv.ui.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The small floating button that shows how many streams are open in multi-view and goes there.
 * It sits over every screen while at least one stream is open.
 */
@Composable
internal fun MultiViewQuickNavBadge(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .background(Color.Black.copy(alpha = 0.10f), CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable(onClickLabel = "Open multi-view", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFE53935),
            fontWeight = FontWeight.Bold,
        )
    }
}
