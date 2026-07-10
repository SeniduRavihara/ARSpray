package com.g37.arspray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.g37.arspray.ArObjectType

@Composable
fun StatusOverlay(
    isSprayMode: Boolean,
    selectedObjectType: ArObjectType,
    isWhiteboardMode: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    val modeTitle = when {
        isWhiteboardMode -> "AR Whiteboard Mode"
        isSprayMode -> "AR Spray Mode (Drawing)"
        else -> "AR Place Mode (${selectedObjectType.displayName})"
    }

    val modeInstruction = when {
        isWhiteboardMode -> "Tap on a surface to spawn the whiteboard!"
        isSprayMode -> "Move your finger on a surface to DRAW!"
        else -> "Tap on a plane surface to place a ${selectedObjectType.displayName}!"
    }

    Box(
        modifier = modifier
            .padding(top = 48.dp)
            .padding(horizontal = 24.dp)
    ) {
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable { isExpanded = false }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = modeTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = modeInstruction,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "(Tap banner to hide)",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            // Collapsed tiny pill
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { isExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$modeTitle ℹ",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
