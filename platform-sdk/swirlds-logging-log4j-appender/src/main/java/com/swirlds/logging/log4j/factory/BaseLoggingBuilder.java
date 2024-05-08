package com.swirlds.logging.log4j.factory;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LambdaUtil;
import org.apache.logging.log4j.util.StackLocatorUtil;
import org.apache.logging.log4j.util.Supplier;

public class BaseLoggingBuilder implements LogBuilder {

    private static Message EMPTY_MESSAGE = new SimpleMessage("");
    private static final String FQCN = BaseLoggingBuilder.class.getName();
    private static final Logger LOGGER = StatusLogger.getLogger();

    private ExtendedLogger logger;
    private Level level;
    private Marker marker;
    private Throwable throwable;
    private volatile boolean inUse;
    private final long threadId;

    public BaseLoggingBuilder(final BaseLogger logger, final Level level) {
        this.logger = logger;
        this.level = level;
        this.threadId = Thread.currentThread().getId();
        this.inUse = level != null;
    }

    public BaseLoggingBuilder() {
        this(null, null);
    }

    public LogBuilder reset(final BaseLogger logger, final Level level) {
        this.logger = logger;
        this.level = level;
        this.marker = null;
        this.throwable = null;
        this.inUse = true;
        return this;
    }

    public boolean isInUse() {
        return this.inUse;
    }

    private boolean isValid() {
        if (!inUse) {
            LOGGER.warn("Attempt to reuse LogBuilder was ignored. {}", StackLocatorUtil.getCallerClass(2));
            return false;
        }
        if (this.threadId != Thread.currentThread().getId()) {
            LOGGER.warn("LogBuilder can only be used on the owning thread. {}", StackLocatorUtil.getCallerClass(2));
            return false;
        }
        return true;
    }

    private void logMessage(Message message) {
        try {
            logger.logMessage(FQCN, level, marker, message, throwable);
        } finally {
            inUse = false;
        }
    }

    @Override
    public LogBuilder withMarker(final Marker marker) {
        this.marker = marker;
        return this;
    }

    @Override
    public LogBuilder withThrowable(final Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    @Override
    public LogBuilder withLocation() {
        LOGGER.info("Call to withLocation() ignored since SLF4J does not support setting location information.");
        return this;
    }

    @Override
    public LogBuilder withLocation(final StackTraceElement location) {
        return withLocation();
    }

    @Override
    public void log(CharSequence message) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message));
        }
    }

    @Override
    public void log(String message) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message));
        }
    }

    @Override
    public void log(String message, Object... params) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, params));
        }
    }

    @Override
    public void log(String message, Supplier<?>... params) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, LambdaUtil.getAll(params)));
        }
    }

    @Override
    public void log(Message message) {
        if (isValid()) {
            logMessage(message);
        }
    }

    @Override
    public void log(final Supplier<Message> messageSupplier) {
        if (isValid()) {
            logMessage(messageSupplier.get());
        }
    }

    @Override
    public Message logAndGet(final Supplier<Message> messageSupplier) {
        Message message = null;
        if (isValid()) {
            logMessage(message = messageSupplier.get());
        }
        return message;
    }

    @Override
    public void log(Object message) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message));
        }
    }

    @Override
    public void log(String message, Object p0) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3, p4));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3, p4, p5));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3, p4, p5, p6));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6,
            Object p7) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6,
            Object p7, Object p8) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7, p8));
        }
    }

    @Override
    public void log(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6,
            Object p7, Object p8, Object p9) {
        if (isValid()) {
            logMessage(logger.getMessageFactory().newMessage(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9));
        }
    }

    @Override
    public void log() {
        if (isValid()) {
            logMessage(EMPTY_MESSAGE);
        }
    }

}
