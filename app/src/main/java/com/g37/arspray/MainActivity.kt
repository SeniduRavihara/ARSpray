// com/g37/arspray/MainActivity.kt
package com.g37.arspray

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.g37.arspray.ui.PermissionScreen
import com.g37.arspray.ui.theme.ARSprayTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Single-activity entry point.
 *
 * Responsibilities (only):
 * - Check and request the CAMERA permission at startup.
 * - Choose between [PermissionScreen] and [ArSprayScreen] based on permission state.
 */
class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase programmatically using web config keys
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyDAp_UaaV2H3E-Mb95feP4LOPTetQvUNrw")
                .setApplicationId("1:843424361604:web:703446234d8f6e971ee6d1")
                .setProjectId("arspray-97d30")
                .setStorageBucket("arspray-97d30.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            ARSprayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasCameraPermission) {
                        ArSprayScreen()
                    } else {
                        PermissionScreen {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }
    }
}
