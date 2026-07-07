// com/g37/arspray/ar/ArSessionConfig.kt
package com.g37.arspray.ar

import com.google.ar.core.Config
import com.google.ar.core.Session

/**
 * Configures the ARCore session with the recommended settings for ARSpray:
 * - Horizontal and vertical plane detection
 * - Ambient intensity light estimation
 * - Automatic depth mode (falls back to DISABLED if not supported by the device)
 * - Auto-focus
 */
fun configureArSession(session: Session, config: Config) {
    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
    config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
    config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
        Config.DepthMode.AUTOMATIC
    else
        Config.DepthMode.DISABLED
    config.focusMode = Config.FocusMode.AUTO
}
