package com.chameleon.infrastructure.config

import java.nio.file.Path

/**
 * Represents a strategy for resolving the configuration file path.
 */
sealed interface ConfigPath {
    data object Auto : ConfigPath
    data class Explicit(val path: Path) : ConfigPath
}

sealed interface ResolvedConfigPath {
    data class Found(val path: Path) : ResolvedConfigPath
    data object Missing : ResolvedConfigPath
}
