package com.chameleon.agent.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThinkingLevel {
    @SerialName("off")
    OFF,
    @SerialName("minimal")
    MINIMAL,
    @SerialName("low")
    LOW,
    @SerialName("medium")
    MEDIUM,
    @SerialName("high")
    HIGH,
    @SerialName("xhigh")
    XHIGH
}

@Serializable
enum class VerboseLevel {
    @SerialName("off")
    OFF,
    @SerialName("on")
    ON,
    @SerialName("full")
    FULL
}
