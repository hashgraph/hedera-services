package com.swirlds.logging.log4j.factory;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerRegistry;

public class BaseLoggerContext implements LoggerContext {
    /**
     * The log event factory to create log events.
     * This is set by the swirlds-logging API.
     */
    private static volatile LogEventFactory logEventFactory;
    /**
     * The log event consumer to consume log events.
     * This is set by the swirlds-logging API.
     */
    private static volatile LogEventConsumer logEventConsumer;

    /**
     * The swirlds emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * A flag to ensure that the initialisation error is only printed once.
     */
    private final AtomicBoolean initialisationErrorPrinted = new AtomicBoolean(false);

    private final LoggerRegistry<ExtendedLogger> loggerRegistry = new LoggerRegistry<>();

    @Override
    public Object getExternalContext() {
        return null;
    }

    @Override
    public ExtendedLogger getLogger(final String name) {
        if (!loggerRegistry.hasLogger(name)) {
            if(logEventFactory == null || logEventConsumer == null) {
                if (initialisationErrorPrinted.compareAndSet(false, true)) {
                    EMERGENCY_LOGGER.log(Level.ERROR, "LogEventFactory and LogEventConsumer must be set before using the logger context.");
                }
            } else {
                loggerRegistry.putIfAbsent(name, null, new BaseLogger(name, logEventConsumer, logEventFactory));
            }
        }
        return loggerRegistry.getLogger(name);
    }

    @Override
    public ExtendedLogger getLogger(final String name, final MessageFactory messageFactory) {
        if (!loggerRegistry.hasLogger(name, messageFactory)) {
            if(logEventFactory == null || logEventConsumer == null) {
                if (initialisationErrorPrinted.compareAndSet(false, true)) {
                    EMERGENCY_LOGGER.log(Level.ERROR, "LogEventFactory and LogEventConsumer must be set before using the logger context.");
                }
            } else {
                loggerRegistry.putIfAbsent(name, null, new BaseLogger(name, messageFactory, logEventConsumer, logEventFactory));
            }
        }
        return loggerRegistry.getLogger(name, messageFactory);
    }

    @Override
    public boolean hasLogger(final String name) {
        return loggerRegistry.hasLogger(name);
    }

    @Override
    public boolean hasLogger(final String name, final MessageFactory messageFactory) {
        return loggerRegistry.hasLogger(name, messageFactory);
    }

    @Override
    public boolean hasLogger(final String name, final Class<? extends MessageFactory> messageFactoryClass) {
        return loggerRegistry.hasLogger(name, messageFactoryClass);
    }

    /**
     * Sets the log event consumer to consume log events.
     *
     * @param logEventConsumer the log event consumer from the swirlds-logging API.
     */
    public static void setLogEventConsumer(@NonNull final LogEventConsumer logEventConsumer) {
        if (logEventConsumer == null) {
            EMERGENCY_LOGGER.logNPE("logEventConsumer");
            return;
        }
        BaseLoggerContext.logEventConsumer = logEventConsumer;
    }

    /**
     * Sets the log event factory to create log events.
     *
     * @param logEventFactory the log event factory from the swirlds-logging API.
     */
    public static void setLogEventFactory(@NonNull final LogEventFactory logEventFactory) {
        if (logEventFactory == null) {
            EMERGENCY_LOGGER.logNPE("logEventFactory");
            return;
        }
        BaseLoggerContext.logEventFactory = logEventFactory;
    }
}