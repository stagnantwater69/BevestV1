package com.jtexpress.bevest.domain.safety

/**
 * Which side runs alert detection and escalation, set at `settings/engine.mode`.
 *
 * Exactly one must, or every fall opens two alerts. The project is on Firebase's free
 * plan, so the default is [DEVICE]: officers' phones do it ([SafetyWatcher]). Once the
 * Cloud Functions are deployed (Blaze plan), set the mode to `cloud` and the phones only
 * show and sound the alerts the functions open.
 */
enum class EngineMode {
    DEVICE,
    CLOUD;

    companion object {
        fun fromRaw(raw: String?): EngineMode =
            if (raw.equals("cloud", ignoreCase = true)) CLOUD else DEVICE
    }
}
