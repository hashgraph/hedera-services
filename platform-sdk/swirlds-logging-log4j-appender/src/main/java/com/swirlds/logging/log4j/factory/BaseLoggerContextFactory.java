package com.swirlds.logging.log4j.factory;

import java.net.URI;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;

public class BaseLoggerContextFactory implements LoggerContextFactory {
    private static final LoggerContext context = new BaseLoggerContext();

    @Override
    public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
            final boolean currentContext) {
        return context;
    }

    @Override
    public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
            final boolean currentContext, final URI configLocation, final String name) {
        return context;
    }

    @Override
    public void removeContext(final LoggerContext ignored) {
    }

    @Override
    public boolean isClassLoaderDependent() {
        // context is always used
        return false;
    }
}
