package agent.platform.logging

import org.slf4j.Logger

object LogWrapper {
    fun error(logger: Logger, message: String, throwable: Throwable?, stacktrace: Boolean) {
        if (throwable == null) {
            logger.error(message)
            return
        }
        if (stacktrace) {
            logger.error(message, throwable)
        } else {
            logger.error("{} ({})", message, throwable::class.java.simpleName)
        }
    }

    fun warn(logger: Logger, message: String, throwable: Throwable?, stacktrace: Boolean) {
        if (throwable == null) {
            logger.warn(message)
            return
        }
        if (stacktrace) {
            logger.warn(message, throwable)
        } else {
            logger.warn("{} ({})", message, throwable::class.java.simpleName)
        }
    }
}
