package io.github.igormy.vocora.shizuku

/** Result of checking whether Shizuku is usable on this device. */
sealed interface ShizukuStatus {
    /** The Shizuku app is not installed. */
    data object NotInstalled : ShizukuStatus

    /** Shizuku is installed but its service is not running. */
    data object NotRunning : ShizukuStatus

    /** Shizuku is installed and its service is up and answering. */
    data object Running : ShizukuStatus
}
