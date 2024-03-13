/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.log4j.appender;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * SwirldsLogAppender is a custom log appender for the Log4j2 logging framework that integrates
 * with the swirlds-logging API as a provider.
 * It allows Log4j2 log events to be consumed by the swirlds-logging API.
 * This appender is specifically designed convert Log4j2 logging events to swirlds-logging events.
 * <p>
 * It supports dynamic installation of {@link LogEventFactory} and {@link LogEventConsumer} to
 * facilitate custom log event creation and consumption.
 * </p>
 * <p>
 *     To forward all logging from Log4j2 to the swirlds-logging API, the following configuration should be used:
 *     <pre>
 *         {@code
 *         <Configuration status="WARN">
 *             <Appenders>
 *               <SwirldsLoggingAppender name="SwirldsAppender">
 *               </SwirldsLoggingAppender>
 *             </Appenders>
 *             <Loggers>
 *               <Root level="all">
 *                 <AppenderRef ref="SwirldsAppender"/>
 *               </Root>
 *             </Loggers>
 *         </Configuration>
 *        }
 *     </pre>
 *
 * @see com.swirlds.logging.api.extensions.provider.LogProvider
 */
@Plugin(name = SwirldsLogAppender.APPENDER_NAME, category = "Core", elementType = "appender", printObject = true)
public class SwirldsLogAppender extends AbstractAppender {
    /**
     * The name of the appender for Log4j2 configuration.
     */
    public static final String APPENDER_NAME = "SwirldsLoggingAppender";

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

    /**
     * Constructs a new SwirldsLogAppender instance.
     * This constructor is used by the Log4j2 framework.
     *
     * @param name   The name of the appender.
     * @param filter The filter to apply.
     * @param layout The layout of log messages.
     */
    private SwirldsLogAppender(
            @NonNull final String name,
            @Nullable final Filter filter,
            @Nullable final Layout<? extends Serializable> layout) {
        super(name, filter, layout, false, Property.EMPTY_ARRAY);
    }

    /**
     * Factory method to create a SwirldsLogAppender instance.
     * This method is used by the Log4j2 framework.
     *
     * @param name   The name of the appender.
     * @param layout The layout of log messages.
     * @param filter The filter to apply.
     *
     * @return A new instance of SwirldsLogAppender.
     */
    @PluginFactory
    public static SwirldsLogAppender createAppender(
            @PluginAttribute("name") @NonNull final String name,
            @PluginElement("Layout") @Nullable final Layout<? extends Serializable> layout,
            @PluginElement("Filters") @Nullable final Filter filter) {
        return new SwirldsLogAppender(name, filter, layout);
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
        SwirldsLogAppender.logEventConsumer = logEventConsumer;
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
        SwirldsLogAppender.logEventFactory = logEventFactory;
    }

    /**
     * If the log provider was installed from the swirlds-logging API,
     * {@code event} will be forwarded to the swirlds-logging API.
     *
     * @param event The log event to append.
     */
    @Override
    public void append(@NonNull final LogEvent event) {
        if (event == null) {
            EMERGENCY_LOGGER.logNPE("event");
            return;
        }
        if (logEventFactory != null && logEventConsumer != null) {
            logEventConsumer.accept(logEventFactory.createLogEvent(
                    translateLevel(event.getLevel()),
                    event.getLoggerName(),
                    event.getThreadName(),
                    event.getTimeMillis(),
                    new Log4JMessage(event.getMessage()),
                    event.getThrown(),
                    translateMarker(event.getMarker()),
                    event.getContextData().toMap()));
        } else {
            if (!initialisationErrorPrinted.getAndSet(true)) {
                EMERGENCY_LOGGER.log(
                        Level.ERROR,
                        "LogEventFactory and LogEventConsumer are not installed. "
                                + "Log events will not be forwarded to the swirlds-logging API.");
            }
        }
    }

    /**
     * Translates Log4j2 markers to swirlds-logging API markers.
     *
     * @param marker The Log4j2 marker to translate.
     *
     * @return The corresponding swirlds-logging marker.
     */
    @Nullable
    private Marker translateMarker(@Nullable final org.apache.logging.log4j.Marker marker) {
        if (marker == null) {
            return null;
        }

        final var parents = marker.getParents();
        if (parents == null || parents.length == 0) {
            return new Marker(marker.getName());
        }

        final Marker parent = translateMarker(parents[parents.length - 1]);
        return new Marker(marker.getName(), parent);
    }

    /**
     * Translates Log4j2 log levels to swrirlds-logging API levels.
     *
     * @param level The Log4j2 level to translate.
     *
     * @return The corresponding swirlds-logging level.
     */
    @NonNull
    private static Level translateLevel(@NonNull final org.apache.logging.log4j.Level level) {
        if (level == null) {
            EMERGENCY_LOGGER.logNPE("level");
            return Level.INFO;
        }

        return switch (level.getStandardLevel()) {
            case FATAL, ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case DEBUG -> Level.DEBUG;
            case TRACE -> Level.TRACE;
            default -> Level.INFO;
        };
    }
}
