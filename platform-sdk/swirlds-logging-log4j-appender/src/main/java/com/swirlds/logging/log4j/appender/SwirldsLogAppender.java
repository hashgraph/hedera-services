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
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import com.swirlds.logging.api.extensions.provider.LogProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Serializable;
import java.util.Objects;
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
 * with the swirlds-logging API. It allows Log4j2 log events to be consumed by the swirlds-logging API.
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
 */
@Plugin(name = SwirldsLogAppender.APPENDER_NAME, category = "Core", elementType = "appender", printObject = true)
public class SwirldsLogAppender extends AbstractAppender implements LogProvider {
    public static final String APPENDER_NAME = "SwirldsLoggingAppender";

    /**
     * The log event factory to create log events. This is set by the swirlds-logging API.
     */
    private static LogEventFactory logEventFactory = null;
    /**
     * The log event consumer to consume log events. This is set by the swirlds-logging API.
     */
    private static LogEventConsumer logEventConsumer = null;

    /**
     * Constructs a new SwirldsLogAppender instance with default values. This constructor is used by the swirlds-logging API.
     */
    public SwirldsLogAppender() {
        super(APPENDER_NAME, null, null, false, Property.EMPTY_ARRAY);
    }

    /**
     * Constructs a new SwirldsLogAppender instance. This constructor is used by the Log4j2 framework.
     *
     * @param name   The name of the appender.
     * @param filter The filter to apply.
     * @param layout The layout of log messages.
     */
    protected SwirldsLogAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout) {
        super(name, filter, layout, false, Property.EMPTY_ARRAY);
    }

    /**
     * Factory method to create a SwirldsLogAppender instance. This method is used by the Log4j2 framework.
     *
     * @param name   The name of the appender.
     * @param layout The layout of log messages.
     * @param filter The filter to apply.
     *
     * @return A new instance of SwirldsLogAppender.
     */
    @PluginFactory
    public static SwirldsLogAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filters") Filter filter) {
        return new SwirldsLogAppender(name, filter, layout);
    }

    /**
     * Forwards {@code event} to the swirlds-logging API. If the log provider was installed from the swirlds-logging API,
     *
     * @param event The log event to append.
     * @see SwirldsLogAppender#install(LogEventFactory, LogEventConsumer)
     */
    @Override
    public void append(final LogEvent event) {
        if (logEventFactory != null && logEventConsumer != null) {
            logEventConsumer.accept(logEventFactory.createLogEvent(
                            translateLevel(event.getLevel()),
                            event.getLoggerName(),
                            event.getThreadName(),
                            event.getTimeMillis(),
                            event.getMessage().getFormattedMessage(),
                            event.getThrown(),
                            translateMarker(event.getMarker()),
                            event.getContextData().toMap()));
        }
    }

    private Marker translateMarker(@Nullable final org.apache.logging.log4j.Marker marker) {
        if (marker == null) {
            return null;
        }
        if (marker.hasParents()) {
            if(marker.getParents().length == 1) {
                return new Marker(marker.getName(), translateMarker(marker.getParents()[0]));
            }
            final Marker parent = translateMarker(marker.getParents()[marker.getParents().length - 1]);
            return new Marker(marker.getName(), parent);
        }
        return new Marker(marker.getName());
    }

    /**
     * Translates Log4j2 log levels to Hedera Hashgraph logging API levels.
     *
     * @param level The Log4j2 level to translate.
     *
     * @return The corresponding swirlds-logging level.
     */
    private static Level translateLevel(@NonNull final org.apache.logging.log4j.Level level) {
        Objects.requireNonNull(level, "level is required");
        return switch (level.getStandardLevel()) {
            case FATAL, ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case DEBUG -> Level.DEBUG;
            case TRACE -> Level.TRACE;
            default -> Level.INFO;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActive() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void install(
            @NonNull final LogEventFactory logEventFactory, @NonNull final LogEventConsumer logEventConsumer) {
        SwirldsLogAppender.logEventFactory = logEventFactory;
        SwirldsLogAppender.logEventConsumer = logEventConsumer;
    }
}
