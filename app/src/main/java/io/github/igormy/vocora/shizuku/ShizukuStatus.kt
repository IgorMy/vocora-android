package io.github.igormy.vocora.shizuku

/** Result of checking whether Shizuku is usable on this device. */
sealed interface ShizukuStatus {
    /** The Shizuku app is not installed. */
    data object NotInstalled : ShizukuStatus

    /** Shizuku is installed, its service was down and we are trying to start it. */
    data object Starting : ShizukuStatus

    /** Shizuku is installed and its service is up and answering. */
    data object Running : ShizukuStatus

    /** Shizuku is installed but its service is down and we could not start it. */
    data object CannotStart : ShizukuStatus
}
