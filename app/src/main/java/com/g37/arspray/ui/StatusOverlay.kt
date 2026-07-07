// com/g37/arspray/ui/StatusOverlay.kt
package com.g37.arspray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Semi-transparent top overlay showing the current AR mode name and interaction hint.
 *
 * @param isSprayMode true = spray/draw mode, false = duck placement mode
 * @param modifier optional modifier to control positioning from the parent (e.g. align to top)
 */
@Composable
fun StatusOverlay(
    isSprayMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(top = 48.dp)
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isSprayMode) "AR Spray Mode (Drawing)" else "AR Place Mode (Duck)",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isSprayMode)
                "Move your finger on a surface to DRAW!"
            else "Tap on a surface to PLACE A DUCK!",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
