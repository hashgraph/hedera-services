/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.logging.api.internal;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import com.swirlds.logging.api.extensions.event.LogMessage;
import com.swirlds.logging.api.extensions.event.Marker;
import com.swirlds.logging.api.internal.event.ParameterizedLogMessage;
import com.swirlds.logging.api.internal.event.SimpleLogMessage;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The implementation of the logger.
 */
public class LoggerImpl implements Logger {

    /**
     * The emergency logger that is used to log errors that occur during the logging process.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The name of the logger.
     */
    private final String name;

    /**
     * The marker of the logger.
     */
    private final Marker marker;

    /**
     * The context of the logger.
     */
    private final Map<String, String> context;

    /**
     * The consumer that is used to consume the log events.
     */
    private final LogEventConsumer logEventConsumer;

    /**
     * The factory that is used to create the log events.
     */
    private final LogEventFactory logEventFactory;

    /**
     * Creates a new instance of the logger.
     *
     * @param name             the name of the logger
     * @param marker           the initial marker of the logger (if present)
     * @param context          the initial context of the logger
     * @param logEventConsumer the consumer that is used to consume the log events
     * @throws NullPointerException if the logEventConsumer is null. For all other use cases fallbacks are implemented
     */
    protected LoggerImpl(
            @NonNull String name,
            @Nullable final Marker marker,
            @NonNull Map<String, String> context,
            LogEventFactory logEventFactory,
            @NonNull LogEventConsumer logEventConsumer) {
        if (name == null) {
            EMERGENCY_LOGGER.logNPE("name");
            this.name = "";
        } else {
            this.name = name;
        }
        this.marker = marker;
        if (context == null) {
            this.context = Collections.emptyMap();
        } else {
            this.context = Collections.unmodifiableMap(context);
        }
        this.logEventFactory = Objects.requireNonNull(logEventFactory, "logEventFactory must not be null");
        this.logEventConsumer = Objects.requireNonNull(logEventConsumer, "logEventConsumer must not be null");
    }

    /**
     * Creates a new instance of the logger.
     *
     * @param name             the name of the logger
     * @param logEventConsumer the consumer that is used to consume the log events
     * @throws NullPointerException if the logEventConsumer is null. For all other use cases fallbacks are implemented
     */
    public LoggerImpl(String name, LogEventFactory logEventFactory, LogEventConsumer logEventConsumer) {
        this(name, null, Map.of(), logEventFactory, logEventConsumer);
    }

    /**
     * Returns the name of the logger.
     *
     * @return the name of the logger
     */
    public String getName() {
        return name;
    }

    @Override
    public void log(Level level, String message) {
        log(level, message, (Throwable) null);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        logImpl(level, new SimpleLogMessage(message), throwable);
    }

    @Override
    public void log(Level level, String message, Object... args) {
        logImpl(level, new ParameterizedLogMessage(message, args), null);
    }

    @Override
    public void log(Level level, String message, Object arg) {
        logImpl(level, new ParameterizedLogMessage(message, arg), null);
    }

    @Override
    public void log(Level level, String message, Object arg1, Object arg2) {
        logImpl(level, new ParameterizedLogMessage(message, arg1, arg2), null);
    }

    @Override
    public void log(Level level, String message, Throwable throwable, Object... args) {
        logImpl(level, new ParameterizedLogMessage(message, args), throwable);
    }

    @Override
    public void log(Level level, String message, Throwable throwable, Object arg1) {
        logImpl(level, new ParameterizedLogMessage(message, arg1), throwable);
    }

    @Override
    public void log(Level level, String message, Throwable throwable, Object arg1, Object arg2) {
        logImpl(level, new ParameterizedLogMessage(message, arg1, arg2), throwable);
    }

    @Override
    public Logger withMarker(String markerName) {
        if (markerName == null) {
            return this;
        } else {
            return withMarkerAndContext(new Marker(markerName, marker), context);
        }
    }

    @Override
    public Logger withContext(String key, String value) {
        if (key != null) {
            Map<String, String> newContext = new HashMap<>(context);
            newContext.put(key, value);
            return withMarkerAndContext(marker, newContext);
        } else {
            return this;
        }
    }

    @Override
    public Logger withContext(String key, String... values) {
        if (values == null) {
            return withContext(key, (String) null);
        }
        if (key != null) {
            Map<String, String> newContext = new HashMap<>(context);
            newContext.put(key, String.join(",", values));
            return withMarkerAndContext(marker, newContext);
        } else {
            return this;
        }
    }

    @Override
    public boolean isEnabled(Level level) {
        return logEventConsumer.isEnabled(getName(), level);
    }

    /**
     * Creates a new instance as a copy of this logger with the given marker and context.
     *
     * @param marker  the marker
     * @param context the context
     * @return the new logger
     */
    protected Logger withMarkerAndContext(final Marker marker, final Map<String, String> context) {
        return new LoggerImpl(getName(), marker, context, logEventFactory, logEventConsumer);
    }

    /**
     * Logs the given message and throwable.
     *
     * @param level     the level
     * @param message   the message
     * @param throwable the throwable
     */
    public void logImpl(Level level, LogMessage message, final Throwable throwable) {
        if (isEnabled(level)) {
            Marker marker = this.marker;
            LogEvent logEvent = logEventFactory.createLogEvent(level, getName(), message, throwable, marker, context);
            logEventConsumer.accept(logEvent);
        }
    }
}
