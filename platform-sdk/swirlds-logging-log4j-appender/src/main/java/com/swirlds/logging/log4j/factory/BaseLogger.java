package com.swirlds.logging.log4j.factory;

import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.util.Constants;


public class BaseLogger extends AbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final ThreadLocal<BaseLoggingBuilder> logBuilder = ThreadLocal.withInitial(BaseLoggingBuilder::new);

    private final LogEventConsumer logEventConsumer;
    private final LogEventFactory logEventFactory;


    public BaseLogger(final String name, final MessageFactory messageFactory, final LogEventConsumer logEventConsumer,
            final LogEventFactory logEventFactory) {
        super(name, messageFactory);
        this.logEventConsumer = logEventConsumer;
        this.logEventFactory = logEventFactory;
    }

    public BaseLogger(final String name, final LogEventConsumer logEventConsumer, final LogEventFactory logEventFactory) {
        super(name);
        this.logEventConsumer = logEventConsumer;
        this.logEventFactory = logEventFactory;
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
        if (logEventConsumer.isEnabled(getName(), com.swirlds.logging.api.Level.TRACE, null)) {
            return Level.TRACE;
        }
        if (logEventConsumer.isEnabled(getName(), com.swirlds.logging.api.Level.DEBUG, null)) {
            return Level.DEBUG;
        }
        if (logEventConsumer.isEnabled(getName(), com.swirlds.logging.api.Level.INFO, null)) {
            return Level.INFO;
        }
        if (logEventConsumer.isEnabled(getName(), com.swirlds.logging.api.Level.WARN, null)) {
            return Level.WARN;
        }
        if (logEventConsumer.isEnabled(getName(), com.swirlds.logging.api.Level.ERROR, null)) {
            return Level.ERROR;
        }

        return Level.OFF;
    }

    @Nullable
    private static com.swirlds.logging.api.Marker getMarker(@Nullable final Marker marker) {
        // No marker is provided in the common case, small methods
        // are optimized more effectively.
        return marker == null ? null : convertMarker(marker);
    }

    private static com.swirlds.logging.api.Marker convertMarker(final Marker marker) {
        if (marker.getParents() != null) {
            final List<Marker> parents = new ArrayList<>(List.of(marker.getParents()));
            final Marker parent = parents.getFirst();
            parents.removeFirst();
            return new com.swirlds.logging.api.Marker(marker.getName(), convertMarker(parent));
        }
        return new com.swirlds.logging.api.Marker(marker.getName(), null);
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
        final com.swirlds.logging.api.Marker baseMarker = getMarker(marker);
        return logEventConsumer.isEnabled(getName(), convertLevel(level), baseMarker);
    }

    @Override
    public void logMessage(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable t) {
        final com.swirlds.logging.api.Marker baseMarker = getMarker(marker);
        final Log4JMessage log4JMessage = new Log4JMessage(message);

        final LogEvent event = logEventFactory.createLogEvent(convertLevel(level), getName(), log4JMessage, t, baseMarker,
                ThreadContext.getImmutableContext());
        logEventConsumer.accept(event);
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
