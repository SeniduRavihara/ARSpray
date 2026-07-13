// com/g37/arspray/ui/LobbyScreens.kt
package com.g37.arspray.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.g37.arspray.ar.generateQrCode

private val GradientBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF12101F), // Very dark violet
        Color(0xFF07060E)  // Almost black
    )
)

private val CardBgColor = Color(0xFF1A1829)
private val PrimaryAccent = Color(0xFF9C52FD) // Purple
private val SecondaryAccent = Color(0xFF4D2587) // Dark Purple

@Composable
fun LobbyScreen(
    onSoloMode: () -> Unit,
    onHostMode: () -> Unit,
    onJoinMode: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "AR Spray Sync",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                text = "Draw together in Augmented Reality",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, bottom = 48.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Choose Connection Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LobbyButton(
                        text = "Solo Mode (Offline)",
                        onClick = onSoloMode,
                        containerColor = PrimaryAccent
                    )

                    LobbyButton(
                        text = "Host Shared Room",
                        onClick = onHostMode,
                        containerColor = SecondaryAccent
                    )

                    LobbyButton(
                        text = "Scan QR / Join Room",
                        onClick = onJoinMode,
                        containerColor = SecondaryAccent
                    )
                }
            }
        }
    }
}

@Composable
fun HostLobbyScreen(
    roomId: String,
    onStartHosting: () -> Unit,
    onBack: () -> Unit
) {
    val qrBitmap = remember(roomId) {
        generateQrCode(roomId, 512)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Host Shared Session",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan to Join this Session",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Session QR Code",
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }

                    Text(
                        text = "Room ID: $roomId",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryAccent
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LobbyButton(
                        text = "Start Session",
                        onClick = onStartHosting,
                        containerColor = PrimaryAccent
                    )

                    LobbyButton(
                        text = "Cancel",
                        onClick = onBack,
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun JoinLobbyScreen(
    onScanQr: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBgColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Join Shared Session",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Scan the QR code displayed on the host device to resolve the shared workspace anchor.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LobbyButton(
                    text = "Scan QR Code",
                    onClick = onScanQr,
                    containerColor = PrimaryAccent
                )

                LobbyButton(
                    text = "Cancel",
                    onClick = onBack,
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                )
            }
        }
    }
}

@Composable
private fun LobbyButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
