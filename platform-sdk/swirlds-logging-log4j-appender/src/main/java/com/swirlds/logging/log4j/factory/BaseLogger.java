package com.swirlds.logging.log4j.factory;

import java.io.Serial;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.util.Constants;


public class BaseLogger extends AbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final ThreadLocal<BaseLoggingBuilder> logBuilder = ThreadLocal.withInitial(BaseLoggingBuilder::new);

    private final com.swirlds.logging.api.Logger logger;

    public BaseLogger(final String name, final MessageFactory messageFactory, final com.swirlds.logging.api.Logger logger) {
        super(name, messageFactory);
        this.logger = logger;
    }

    public BaseLogger(final String name, final com.swirlds.logging.api.Logger logger) {
        super(name);
        this.logger = logger;
    }

    private com.swirlds.logging.api.Level convertLevel(final Level level) {
        return switch (level.getStandardLevel()) {
            case DEBUG -> com.swirlds.logging.api.Level.DEBUG;
            case TRACE -> com.swirlds.logging.api.Level.TRACE;
            case WARN -> com.swirlds.logging.api.Level.WARN;
            case ERROR -> com.swirlds.logging.api.Level.ERROR;
            default -> com.swirlds.logging.api.Level.INFO;
        };
    }

    @Override
    public Level getLevel() {
        if (logger.isTraceEnabled()) {
            return Level.TRACE;
        }
        if (logger.isDebugEnabled()) {
            return Level.DEBUG;
        }
        if (logger.isInfoEnabled()) {
            return Level.INFO;
        }
        if (logger.isWarnEnabled()) {
            return Level.WARN;
        }
        if (logger.isErrorEnabled()) {
            return Level.ERROR;
        }
        // Option: throw new IllegalStateException("Unknown SLF4JLevel");
        // Option: return Level.ALL;
        return Level.OFF;
    }

    public com.swirlds.logging.api.Logger getLogger() {
        return logger;
    }

    private static String getMarker(final Marker marker) {
        // No marker is provided in the common case, small methods
        // are optimized more effectively.
        return marker == null ? null : convertMarker(marker);
    }

    private static String convertMarker(final Marker marker) {
        final StringBuilder slf4jMarker = new StringBuilder();
        final Marker[] parents = marker.getParents();
        if (parents != null) {
            for (final Marker parent : parents) {
                final String slf4jParent = getMarker(parent);
                slf4jMarker.append(",").append(slf4jParent);
            }
        }
        return slf4jMarker.toString();
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final Message data, final Throwable t) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final CharSequence data, final Throwable t) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final Object data, final Throwable t) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String data) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String data, final Object... p1) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String message, final Object p0,
            final Object p1, final Object p2, final Object p3,
            final Object p4, final Object p5, final Object p6,
            final Object p7, final Object p8, final Object p9) {
        return isEnabledFor(level, marker);
    }

    @Override
    public boolean isEnabled(final Level level, final Marker marker, final String data, final Throwable t) {
        return isEnabledFor(level, marker);
    }

    private boolean isEnabledFor(final Level level, final Marker marker) {
        final String slf4jMarker = getMarker(marker);
        return logger.isEnabled(convertLevel(level), slf4jMarker);
    }

    @Override
    public void logMessage(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable t) {
        final String slf4jMarker = getMarker(marker);
        final String formattedMessage = message.getFormattedMessage();

        logger.log(convertLevel(level), slf4jMarker, formattedMessage, t);
    }

    @Override
    public LogBuilder always() {
        return atLevel(Level.OFF);
    }

    @Override
    public LogBuilder atTrace() {
        return atLevel(Level.TRACE);
    }

    @Override
    public LogBuilder atDebug() {
        return atLevel(Level.DEBUG);
    }

    @Override
    public LogBuilder atInfo() {
        return atLevel(Level.INFO);
    }

    @Override
    public LogBuilder atWarn() {
        return atLevel(Level.WARN);
    }

    @Override
    public LogBuilder atError() {
        return atLevel(Level.ERROR);
    }

    @Override
    public LogBuilder atFatal() {
        return atLevel(Level.TRACE);
    }

    @Override
    protected LogBuilder getLogBuilder(final Level level) {
        final BaseLoggingBuilder builder = logBuilder.get();
        return Constants.ENABLE_THREADLOCALS && !builder.isInUse() ? builder.reset(this, level)
                : new BaseLoggingBuilder(this, level);
    }

    @Override
    public LogBuilder atLevel(final Level level) {
        return super.atLevel(level);
    }
}
