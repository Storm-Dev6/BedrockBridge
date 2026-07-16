package io.bedrockbridge.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Central logger factory that keeps the application independent from the logging backend. */
public final class Logging {
    private Logging() {}

    /** Returns an SLF4J logger for a documented owner type. */
    public static Logger logger(Class<?> owner) {
        return LoggerFactory.getLogger(owner);
    }
}
