package agent.platform.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoggingConfig(
    val level: LogLevel = LogLevel.INFO,
    val format: LogFormat = LogFormat.PLAIN,
    val debug: Boolean = false,
    val stacktrace: Boolean = false
)

@Serializable
enum class LogLevel {
    @SerialName("trace")
    TRACE,
    @SerialName("debug")
    DEBUG,
    @SerialName("info")
    INFO,
    @SerialName("warn")
    WARN,
    @SerialName("error")
    ERROR
}

@Serializable
enum class LogFormat {
    @SerialName("plain")
    PLAIN,
    @SerialName("json")
    JSON
}
