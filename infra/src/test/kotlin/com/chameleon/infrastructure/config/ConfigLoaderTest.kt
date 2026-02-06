package com.chameleon.infrastructure.config

import com.chameleon.config.domain.LogFormat
import com.chameleon.config.domain.LogLevel
import com.chameleon.plugin.telegram.TelegramConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ConfigLoaderTest {
    @Test
    fun failsWhenEnvMissing() {
        val configPath = createTempConfig(
            """
            {
              "models": {
                "providers": {
                  "kimi": {
                    "baseUrl": "https://api.moonshot.cn/v1",
                    "apiKey": "${'$'}{OPENCLAW_TEST_MISSING_12345}",
                    "models": [
                      {
                        "id": "kimi-k2.5",
                        "name": "Kimi K2.5",
                        "contextWindow": 256000,
                        "maxTokens": 8192,
                        "reasoning": true
                      }
                    ]
                  }
                }
              },
              "channels": {
                "entries": {
                  "telegram": {
                    "enabled": false,
                    "token": null,
                    "mode": "polling"
                  }
                }
              }
            }
            """.trimIndent()
        )
        val loader = ConfigLoader()
        assertFailsWith<IllegalStateException> {
            loader.load(ConfigPath.Explicit(configPath), emptyMap(), emptyList())
        }
    }

    @Test
    fun loadsWithEnvVars() {
        val env = mapOf(
            "KIMI_API_KEY" to "test-key",
            "TELEGRAM_TOKEN" to "test-token"
        )
        val configPath = createTempConfig(
            """
            {
              "models": {
                "providers": {
                  "kimi": {
                    "baseUrl": "https://api.moonshot.cn/v1",
                    "apiKey": "${'$'}{KIMI_API_KEY}",
                    "models": [
                      {
                        "id": "kimi-k2.5",
                        "name": "Kimi K2.5",
                        "contextWindow": 256000,
                        "maxTokens": 8192,
                        "reasoning": true
                      }
                    ]
                  }
                }
              },
              "channels": {
                "entries": {
                  "telegram": {
                    "enabled": true,
                    "token": "${'$'}{TELEGRAM_TOKEN}",
                    "mode": "polling"
                  }
                }
              }
            }
            """.trimIndent()
        )

        val loader = ConfigLoader()
        val loaded = loader.load(ConfigPath.Explicit(configPath), env, emptyList())

        assertNotNull(loaded.config.models.providers["kimi"])
        assertEquals("test-key", loaded.config.models.providers["kimi"]?.apiKey)
        val telegramConfig = parseTelegramConfig(loaded.config)
        assertEquals("test-token", telegramConfig.token)
    }

    @Test
    fun cliOverridesEnvAndConfig() {
        val env = mapOf(
            "KIMI_API_KEY" to "test-key",
            "TELEGRAM_TOKEN" to "test-token",
            "LOG_LEVEL" to "info"
        )
        val configPath = createTempConfig(
            """
            {
              "models": {
                "providers": {
                  "kimi": {
                    "baseUrl": "https://api.moonshot.cn/v1",
                    "apiKey": "${'$'}{KIMI_API_KEY}",
                    "models": [
                      {
                        "id": "kimi-k2.5",
                        "name": "Kimi K2.5",
                        "contextWindow": 256000,
                        "maxTokens": 8192,
                        "reasoning": true
                      }
                    ]
                  }
                }
              },
              "channels": {
                "entries": {
                  "telegram": {
                    "enabled": true,
                    "token": "${'$'}{TELEGRAM_TOKEN}",
                    "mode": "polling"
                  }
                }
              },
              "logging": {
                "level": "info",
                "format": "plain",
                "debug": false,
                "stacktrace": false
              }
            }
            """.trimIndent()
        )

        val loader = ConfigLoader()
        val loaded = loader.load(
            ConfigPath.Explicit(configPath),
            env,
            listOf("--log-level=debug", "--log-format=json", "--log-stacktrace=true")
        )

        assertEquals(LogLevel.DEBUG, loaded.config.logging.level)
        assertEquals(LogFormat.JSON, loaded.config.logging.format)
        assertEquals(true, loaded.config.logging.stacktrace)
    }

    @Test
    fun cliConfigPathOverridesAuto() {
        val env = mapOf(
            "KIMI_API_KEY" to "test-key",
            "TELEGRAM_TOKEN" to "test-token"
        )
        val configPath = createTempConfig(
            """
            {
              "models": {
                "providers": {
                  "kimi": {
                    "baseUrl": "https://api.moonshot.cn/v1",
                    "apiKey": "${'$'}{KIMI_API_KEY}",
                    "models": [
                      {
                        "id": "kimi-k2.5",
                        "name": "Kimi K2.5",
                        "contextWindow": 256000,
                        "maxTokens": 8192,
                        "reasoning": true
                      }
                    ]
                  }
                }
              },
              "channels": {
                "entries": {
                  "telegram": {
                    "enabled": true,
                    "token": "${'$'}{TELEGRAM_TOKEN}",
                    "mode": "polling"
                  }
                }
              }
            }
            """.trimIndent()
        )

        val loader = ConfigLoader()
        val loaded = loader.load(
            ConfigPath.Auto,
            env,
            listOf("--config=${configPath.toAbsolutePath()}")
        )

        assertNotNull(loaded.config.models.providers["kimi"])
    }

    private fun createTempConfig(content: String): java.nio.file.Path {
        val dir = java.nio.file.Files.createTempDirectory("config-test")
        val path = dir.resolve("config.json")
        java.nio.file.Files.writeString(path, content)
        return path
    }

    private fun parseTelegramConfig(config: com.chameleon.config.domain.PlatformConfig): TelegramConfig {
        val json = Json { ignoreUnknownKeys = true }
        val raw = config.channels.get("telegram") ?: return TelegramConfig()
        return json.decodeFromJsonElement(TelegramConfig.serializer(), raw)
    }
}
