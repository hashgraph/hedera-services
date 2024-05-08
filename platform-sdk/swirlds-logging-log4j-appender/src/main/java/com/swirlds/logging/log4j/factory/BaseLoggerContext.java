package com.swirlds.logging.log4j.factory;

import com.swirlds.logging.api.Loggers;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerRegistry;

public class BaseLoggerContext implements LoggerContext {
    private final LoggerRegistry<ExtendedLogger> loggerRegistry = new LoggerRegistry<>();

    @Override
    public Object getExternalContext() {
        return null;
    }

    @Override
    public ExtendedLogger getLogger(final String name) {
        if (!loggerRegistry.hasLogger(name)) {
            loggerRegistry.putIfAbsent(name, null, new BaseLogger(name, Loggers.getLogger(name)));
        }
        return loggerRegistry.getLogger(name);
    }

    @Override
    public ExtendedLogger getLogger(final String name, final MessageFactory messageFactory) {
        if (!loggerRegistry.hasLogger(name, messageFactory)) {
            loggerRegistry.putIfAbsent(name, messageFactory,
                    new BaseLogger(name, messageFactory, Loggers.getLogger(name)));
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
}