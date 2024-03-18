/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.base.context.Context;
import com.swirlds.base.test.fixtures.context.WithContext;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.logging.LoggerApiSpecTest;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.emergency.EmergencyLoggerImpl;
import com.swirlds.logging.api.internal.event.DefaultLogEvent;
import com.swirlds.logging.api.internal.level.ConfigLevel;
import com.swirlds.logging.util.InMemoryHandler;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@WithContext
public class LoggingSystemTest {

    private final List<Path> tempFiles = List.of(Path.of("crypto.log"), Path.of("transaction.log"));
    private LoggingSystem loggingSystem = null;

    @BeforeEach
    void cleanupBefore() {
        // reset Emergency logger to remove messages from previous tests
        EmergencyLoggerImpl.getInstance().publishLoggedEvents();
    }

    @AfterEach
    void cleanupAfter() {
        if (loggingSystem != null) {
            loggingSystem.stopAndFinalize();
            tempFiles.forEach(path -> {
                if (path.toFile().exists()) {
                    path.toFile().delete();
                }
            });
        }
        loggingSystem = null;
    }

    @Test
    @Disabled
    @DisplayName("Test that a logger name is always created correctly")
    void testLoggerName() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        final LoggerImpl testNameLogger = loggingSystem.getLogger("test-name");
        final LoggerImpl nullNameLogger = loggingSystem.getLogger(null);
        final LoggerImpl blankNameLogger = loggingSystem.getLogger("");
        final LoggerImpl ToTrimNameLogger = loggingSystem.getLogger("  test-name  ");
        final LoggerImpl ToTrimBlankNameLogger = loggingSystem.getLogger("    ");

        // then
        Assertions.assertEquals("test-name", testNameLogger.getName());
        Assertions.assertEquals("", nullNameLogger.getName());
        Assertions.assertEquals("", blankNameLogger.getName());
        Assertions.assertEquals("test-name", ToTrimNameLogger.getName());
        Assertions.assertEquals("", ToTrimBlankNameLogger.getName());
    }

    @Test
    @Disabled
    @DisplayName("Test that creating loggers with same name ends in same logger instance")
    void testSameLoggerByName() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        final LoggerImpl testNameLogger = loggingSystem.getLogger("test-name");
        final LoggerImpl nullNameLogger = loggingSystem.getLogger(null);
        final LoggerImpl blankNameLogger = loggingSystem.getLogger("");
        final LoggerImpl ToTrimNameLogger = loggingSystem.getLogger("  test-name  ");
        final LoggerImpl ToTrimBlankNameLogger = loggingSystem.getLogger("    ");
        final LoggerImpl testNameLogger2 = loggingSystem.getLogger("test-name");

        // then
        Assertions.assertSame(testNameLogger, ToTrimNameLogger);
        Assertions.assertSame(nullNameLogger, blankNameLogger);
        Assertions.assertSame(nullNameLogger, ToTrimBlankNameLogger);
        Assertions.assertSame(testNameLogger, testNameLogger2);
    }

    @Test
    @Disabled
    @DisplayName("Test that INFO is default level for a non configured logging system")
    void testDefaultLevel() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        final boolean rootTraceEnabled = loggingSystem.isEnabled("", Level.TRACE, null);
        final boolean rootDebugEnabled = loggingSystem.isEnabled("", Level.DEBUG, null);
        final boolean rootInfoEnabled = loggingSystem.isEnabled("", Level.INFO, null);
        final boolean rootWarnEnabled = loggingSystem.isEnabled("", Level.WARN, null);
        final boolean rootErrorEnabled = loggingSystem.isEnabled("", Level.ERROR, null);

        final boolean rootTrimTraceEnabled = loggingSystem.isEnabled("  ", Level.TRACE, null);
        final boolean rootTrimDebugEnabled = loggingSystem.isEnabled("  ", Level.DEBUG, null);
        final boolean rootTrimInfoEnabled = loggingSystem.isEnabled("  ", Level.INFO, null);
        final boolean rootTrimWarnEnabled = loggingSystem.isEnabled("  ", Level.WARN, null);
        final boolean rootTrimErrorEnabled = loggingSystem.isEnabled("  ", Level.ERROR, null);

        final boolean rootNullTraceEnabled = loggingSystem.isEnabled(null, Level.TRACE, null);
        final boolean rootNullDebugEnabled = loggingSystem.isEnabled(null, Level.DEBUG, null);
        final boolean rootNullInfoEnabled = loggingSystem.isEnabled(null, Level.INFO, null);
        final boolean rootNullWarnEnabled = loggingSystem.isEnabled(null, Level.WARN, null);
        final boolean rootNullErrorEnabled = loggingSystem.isEnabled(null, Level.ERROR, null);

        final boolean testTraceEnabled = loggingSystem.isEnabled("test.Class", Level.TRACE, null);
        final boolean testDebugEnabled = loggingSystem.isEnabled("test.Class", Level.DEBUG, null);
        final boolean testInfoEnabled = loggingSystem.isEnabled("test.Class", Level.INFO, null);
        final boolean testWarnEnabled = loggingSystem.isEnabled("test.Class", Level.WARN, null);
        final boolean testErrorEnabled = loggingSystem.isEnabled("test.Class", Level.ERROR, null);

        final boolean testBlankTraceEnabled = loggingSystem.isEnabled("  test.Class  ", Level.TRACE, null);
        final boolean testBlankDebugEnabled = loggingSystem.isEnabled("  test.Class  ", Level.DEBUG, null);
        final boolean testBlankInfoEnabled = loggingSystem.isEnabled("  test.Class  ", Level.INFO, null);
        final boolean testBlankWarnEnabled = loggingSystem.isEnabled("  test.Class  ", Level.WARN, null);
        final boolean testBlankErrorEnabled = loggingSystem.isEnabled("  test.Class  ", Level.ERROR, null);

        // then
        Assertions.assertFalse(rootTraceEnabled, "INFO should be default level");
        Assertions.assertFalse(rootDebugEnabled, "INFO should be default level");
        Assertions.assertTrue(rootInfoEnabled, "INFO should be default level");
        Assertions.assertTrue(rootWarnEnabled, "INFO should be default level");
        Assertions.assertTrue(rootErrorEnabled, "INFO should be default level");

        Assertions.assertFalse(rootNullTraceEnabled, "INFO should be default level");
        Assertions.assertFalse(rootNullDebugEnabled, "INFO should be default level");
        Assertions.assertTrue(rootNullInfoEnabled, "INFO should be default level");
        Assertions.assertTrue(rootNullWarnEnabled, "INFO should be default level");
        Assertions.assertTrue(rootNullErrorEnabled, "INFO should be default level");

        Assertions.assertFalse(rootTrimTraceEnabled, "INFO should be default level");
        Assertions.assertFalse(rootTrimDebugEnabled, "INFO should be default level");
        Assertions.assertTrue(rootTrimInfoEnabled, "INFO should be default level");
        Assertions.assertTrue(rootTrimWarnEnabled, "INFO should be default level");
        Assertions.assertTrue(rootTrimErrorEnabled, "INFO should be default level");

        Assertions.assertFalse(testTraceEnabled, "INFO should be default level");
        Assertions.assertFalse(testDebugEnabled, "INFO should be default level");
        Assertions.assertTrue(testInfoEnabled, "INFO should be default level");
        Assertions.assertTrue(testWarnEnabled, "INFO should be default level");
        Assertions.assertTrue(testErrorEnabled, "INFO should be default level");

        Assertions.assertFalse(testBlankTraceEnabled, "INFO should be default level");
        Assertions.assertFalse(testBlankDebugEnabled, "INFO should be default level");
        Assertions.assertTrue(testBlankInfoEnabled, "INFO should be default level");
        Assertions.assertTrue(testBlankWarnEnabled, "INFO should be default level");
        Assertions.assertTrue(testBlankErrorEnabled, "INFO should be default level");
    }

    @Test
    @Disabled
    @DisplayName("Test that logging system can handle null params for isEnabled")
    void testNullLevel() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        final boolean rootEnabled = loggingSystem.isEnabled("", null, null);
        final boolean rootTrimEnabled = loggingSystem.isEnabled("  ", null, null);
        final boolean rootNullEnabled = loggingSystem.isEnabled(null, null, null);
        final boolean testEnabled = loggingSystem.isEnabled("test.Class", null, null);
        final boolean testBlankEnabled = loggingSystem.isEnabled("  test.Class  ", null, null);

        // then
        Assertions.assertTrue(rootEnabled, "For a NULL level all must be enabled");
        Assertions.assertTrue(rootTrimEnabled, "For a NULL level all must be enabled");
        Assertions.assertTrue(rootNullEnabled, "For a NULL level all must be enabled");
        Assertions.assertTrue(testEnabled, "For a NULL level all must be enabled");
        Assertions.assertTrue(testBlankEnabled, "For a NULL level all must be enabled");
    }

    @Test
    @Disabled
    @DisplayName("Test that isEnabled logs errors to emergency logger")
    void testErrorsForEnabled() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.isEnabled("test.Class", Level.TRACE, null); // no logged error
        loggingSystem.isEnabled("test.Class", null, null); // 1 logged error
        loggingSystem.isEnabled(null, Level.TRACE, null); // 1 logged error
        loggingSystem.isEnabled(null, null, null); // 2 logged errors

        final List<LogEvent> loggedErrorEvents = getLoggedEvents();

        Assertions.assertEquals(4, loggedErrorEvents.size(), "There should be 6 ERROR events");
    }

    @Test
    @Disabled
    @DisplayName("Test that accept logs errors to emergency logger")
    void testErrorsForAccept() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.accept(null); // 1 logged error

        final List<LogEvent> loggedErrorEvents = getLoggedEvents();

        Assertions.assertEquals(1, loggedErrorEvents.size(), "There should be 1 ERROR event");
    }

    private List<LogEvent> getLoggedEvents() {
        return EmergencyLoggerImpl.getInstance().publishLoggedEvents().stream()
                .filter(event -> event.level() == Level.ERROR)
                .collect(Collectors.toList());
    }

    @Test
    @Disabled
    @DisplayName("Test that log level can be configured")
    void testCustomLevel() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.test.Class", "TRACE")
                .withConverter(ConfigLevel.class, new ConfigLevelConverter())
                .getOrCreateConfig();

        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        final boolean rootTraceEnabled = loggingSystem.isEnabled("", Level.TRACE, null);
        final boolean rootDebugEnabled = loggingSystem.isEnabled("", Level.DEBUG, null);
        final boolean rootInfoEnabled = loggingSystem.isEnabled("", Level.INFO, null);
        final boolean rootWarnEnabled = loggingSystem.isEnabled("", Level.WARN, null);
        final boolean rootErrorEnabled = loggingSystem.isEnabled("", Level.ERROR, null);

        final boolean rootNullTraceEnabled = loggingSystem.isEnabled(null, Level.TRACE, null);
        final boolean rootNullDebugEnabled = loggingSystem.isEnabled(null, Level.DEBUG, null);
        final boolean rootNullInfoEnabled = loggingSystem.isEnabled(null, Level.INFO, null);
        final boolean rootNullWarnEnabled = loggingSystem.isEnabled(null, Level.WARN, null);
        final boolean rootNullErrorEnabled = loggingSystem.isEnabled(null, Level.ERROR, null);

        final boolean rootTrimTraceEnabled = loggingSystem.isEnabled("  ", Level.TRACE, null);
        final boolean rootTrimDebugEnabled = loggingSystem.isEnabled("  ", Level.DEBUG, null);
        final boolean rootTrimInfoEnabled = loggingSystem.isEnabled("  ", Level.INFO, null);
        final boolean rootTrimWarnEnabled = loggingSystem.isEnabled("  ", Level.WARN, null);
        final boolean rootTrimErrorEnabled = loggingSystem.isEnabled("  ", Level.ERROR, null);

        final boolean testTraceEnabled = loggingSystem.isEnabled("test.Class", Level.TRACE, null);
        final boolean testDebugEnabled = loggingSystem.isEnabled("test.Class", Level.DEBUG, null);
        final boolean testInfoEnabled = loggingSystem.isEnabled("test.Class", Level.INFO, null);
        final boolean testWarnEnabled = loggingSystem.isEnabled("test.Class", Level.WARN, null);
        final boolean testErrorEnabled = loggingSystem.isEnabled("test.Class", Level.ERROR, null);

        final boolean testBlankTraceEnabled = loggingSystem.isEnabled("  test.Class  ", Level.TRACE, null);
        final boolean testBlankDebugEnabled = loggingSystem.isEnabled("  test.Class  ", Level.DEBUG, null);
        final boolean testBlankInfoEnabled = loggingSystem.isEnabled("  test.Class  ", Level.INFO, null);
        final boolean testBlankWarnEnabled = loggingSystem.isEnabled("  test.Class  ", Level.WARN, null);
        final boolean testBlankErrorEnabled = loggingSystem.isEnabled("  test.Class  ", Level.ERROR, null);

        // then
        Assertions.assertFalse(rootTraceEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootDebugEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootInfoEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootWarnEnabled, "ERROR is configured for root");
        Assertions.assertTrue(rootErrorEnabled, "ERROR is configured for root");

        Assertions.assertFalse(rootNullTraceEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootNullDebugEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootNullInfoEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootNullWarnEnabled, "ERROR is configured for root");
        Assertions.assertTrue(rootNullErrorEnabled, "ERROR is configured for root");

        Assertions.assertFalse(rootTrimTraceEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootTrimDebugEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootTrimInfoEnabled, "ERROR is configured for root");
        Assertions.assertFalse(rootTrimWarnEnabled, "ERROR is configured for root");
        Assertions.assertTrue(rootTrimErrorEnabled, "ERROR is configured for root");

        Assertions.assertTrue(testTraceEnabled, "TRACE is configured");
        Assertions.assertTrue(testDebugEnabled, "TRACE is configured");
        Assertions.assertTrue(testInfoEnabled, "TRACE is configured");
        Assertions.assertTrue(testWarnEnabled, "TRACE is configured");
        Assertions.assertTrue(testErrorEnabled, "TRACE is configured");

        Assertions.assertTrue(testBlankTraceEnabled, "TRACE is configured");
        Assertions.assertTrue(testBlankDebugEnabled, "TRACE is configured");
        Assertions.assertTrue(testBlankInfoEnabled, "TRACE is configured");
        Assertions.assertTrue(testBlankWarnEnabled, "TRACE is configured");
        Assertions.assertTrue(testBlankErrorEnabled, "TRACE is configured");
    }

    @Test
    @Disabled
    @DisplayName("Test that addHandler logs errors to emergency logger")
    void testNullHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.addHandler(null);

        // then
        final List<LogEvent> loggedErrorEvents = getLoggedEvents();
        Assertions.assertEquals(1, loggedErrorEvents.size());
    }

    @Test
    @Disabled
    @DisplayName("Test that getLogger logs errors to emergency logger")
    void testNullLogger() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        final LoggerImpl logger = loggingSystem.getLogger(null);

        // then
        Assertions.assertNotNull(logger);
        Assertions.assertEquals("", logger.getName());
        Assertions.assertFalse(logger.isEnabled(Level.TRACE), "logger should be configured as root logger");
        Assertions.assertFalse(logger.isEnabled(Level.DEBUG), "logger should be configured as root logger");
        Assertions.assertTrue(logger.isEnabled(Level.INFO), "logger should be configured as root logger");
        Assertions.assertTrue(logger.isEnabled(Level.WARN), "logger should be configured as root logger");
        Assertions.assertTrue(logger.isEnabled(Level.ERROR), "logger should be configured as root logger");
        final List<LogEvent> loggedErrorEvents = getLoggedEvents();
        Assertions.assertEquals(1, loggedErrorEvents.size());
    }

    @Test
    @Disabled
    @DisplayName("Test that all logging is forwarded to emergency logger if no handler is defined")
    void testEmergencyLoggerIsUsedIfNoAppender() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final LoggerImpl logger = loggingSystem.getLogger("");
        EmergencyLoggerImpl.getInstance().publishLoggedEvents(); // reset Emergency logger to remove the init logging

        // when
        logger.trace("trace-message"); // should not be logged since root logger is defined as INFO level
        logger.debug("debug-message"); // should not be logged since root logger is defined as INFO level
        logger.info("info-message");
        logger.warn("warn-message");
        logger.error("error-message");

        // then
        final List<LogEvent> loggedEvents = EmergencyLoggerImpl.getInstance().publishLoggedEvents();
        Assertions.assertEquals(3, loggedEvents.size());
        Assertions.assertEquals("info-message", loggedEvents.get(0).message().getMessage());
        Assertions.assertEquals(Level.INFO, loggedEvents.get(0).level());
        Assertions.assertEquals("warn-message", loggedEvents.get(1).message().getMessage());
        Assertions.assertEquals(Level.WARN, loggedEvents.get(1).level());
        Assertions.assertEquals("error-message", loggedEvents.get(2).message().getMessage());
        Assertions.assertEquals(Level.ERROR, loggedEvents.get(2).level());
    }

    @Test
    @Disabled
    @DisplayName("Test that all logging for info+ is forwarded to emergency logger if no handler is defined")
    void testEmergencyLoggerIsUsedForConfiguredLevelIfNoAppender() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final LoggerImpl logger = loggingSystem.getLogger("test.Class");
        EmergencyLoggerImpl.getInstance().publishLoggedEvents(); // reset Emergency logger to remove the init logging

        // when
        logger.trace("trace-message"); // should not be logged
        logger.debug("debug-message"); // should not be logged
        logger.info("info-message");
        logger.warn("warn-message");
        logger.error("error-message");

        // then
        final List<LogEvent> loggedEvents = EmergencyLoggerImpl.getInstance().publishLoggedEvents();
        Assertions.assertEquals(3, loggedEvents.size());
        Assertions.assertEquals("info-message", loggedEvents.get(0).message().getMessage());
        Assertions.assertEquals(Level.INFO, loggedEvents.get(0).level());
        Assertions.assertEquals("warn-message", loggedEvents.get(1).message().getMessage());
        Assertions.assertEquals(Level.WARN, loggedEvents.get(1).level());
        Assertions.assertEquals("error-message", loggedEvents.get(2).message().getMessage());
        Assertions.assertEquals(Level.ERROR, loggedEvents.get(2).level());
    }

    @Test
    @DisplayName(
            "Test that checks if simple log calls are forwarded correctly will all information to the configured handler")
    void testSimpleLoggingHandling() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);
        final LoggerImpl logger = loggingSystem.getLogger("test-logger");
        final long startTime = System.currentTimeMillis() - 1;

        // when
        logger.trace("trace-message"); // Should not be forwarded since INFO is configured as root level
        logger.debug("debug-message"); // Should not be forwarded since INFO is configured as root level
        logger.info("info-message");
        logger.warn("warn-message");
        logger.error("error-message");

        // then
        final List<LogEvent> loggedEvents = handler.getEvents();
        Assertions.assertEquals(3, loggedEvents.size());

        final LogEvent event1 = loggedEvents.get(0);
        Assertions.assertEquals("info-message", event1.message().getMessage());
        Assertions.assertEquals(Level.INFO, event1.level());
        Assertions.assertEquals(Map.of(), event1.context());
        Assertions.assertEquals("test-logger", event1.loggerName());
        Assertions.assertNull(event1.marker());
        Assertions.assertEquals(Thread.currentThread().getName(), event1.threadName());
        Assertions.assertNull(event1.throwable());
        Assertions.assertTrue(event1.timestamp() > startTime);
        Assertions.assertTrue(event1.timestamp() <= System.currentTimeMillis());

        final LogEvent event2 = loggedEvents.get(1);
        Assertions.assertEquals("warn-message", event2.message().getMessage());
        Assertions.assertEquals(Level.WARN, event2.level());
        Assertions.assertEquals(Map.of(), event2.context());
        Assertions.assertEquals("test-logger", event2.loggerName());
        Assertions.assertNull(event2.marker());
        Assertions.assertEquals(Thread.currentThread().getName(), event2.threadName());
        Assertions.assertNull(event2.throwable());
        Assertions.assertTrue(event2.timestamp() > startTime);
        Assertions.assertTrue(event2.timestamp() <= System.currentTimeMillis());

        final LogEvent event3 = loggedEvents.get(2);
        Assertions.assertEquals("error-message", event3.message().getMessage());
        Assertions.assertEquals(Level.ERROR, event3.level());
        Assertions.assertEquals(Map.of(), event3.context());
        Assertions.assertEquals("test-logger", event3.loggerName());
        Assertions.assertNull(event3.marker());
        Assertions.assertEquals(Thread.currentThread().getName(), event3.threadName());
        Assertions.assertNull(event3.throwable());
        Assertions.assertTrue(event3.timestamp() > startTime);
        Assertions.assertTrue(event3.timestamp() <= System.currentTimeMillis());

        Assertions.assertTrue(event1.timestamp() <= event2.timestamp());
        Assertions.assertTrue(event2.timestamp() <= event3.timestamp());
    }

    @Test
    @Disabled
    @DisplayName("Test that accept passes events to the configured handler")
    void testAcceptHandling() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);
        final LocalDateTime startTime = LocalDateTime.now();
        LogEvent event1 = loggingSystem.getLogEventFactory().createLogEvent(Level.INFO, "test-logger", "info-message");
        LogEvent event2 = loggingSystem
                .getLogEventFactory()
                .createLogEvent(
                        Level.TRACE,
                        "test-logger",
                        "trace-message"); // should not be forwarded since INFO is configured as root level
        LogEvent event3 =
                loggingSystem.getLogEventFactory().createLogEvent(Level.ERROR, "test-logger", "error-message");
        LogEvent event4 = loggingSystem.getLogEventFactory().createLogEvent(Level.INFO, "test-logger", "info-message");

        // when
        loggingSystem.accept(event1);
        loggingSystem.accept(event2);
        loggingSystem.accept(event3);
        loggingSystem.accept(event4);

        // then
        final List<LogEvent> loggedEvents = handler.getEvents();
        Assertions.assertEquals(3, loggedEvents.size());
        Assertions.assertEquals(event1, loggedEvents.get(0));
        Assertions.assertEquals(event3, loggedEvents.get(1));
        Assertions.assertEquals(event4, loggedEvents.get(2));
    }

    @Test
    @DisplayName(
            "Test that checks if complex log calls are forwarded correctly with all information to the configured handler")
    void testComplexLoggingHandling() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);
        final LoggerImpl logger = loggingSystem.getLogger("test-logger");
        final long startTime = System.currentTimeMillis() - 1;

        // when
        Context.getGlobalContext().add("global", "global-value");
        logger.withMarker("TRACE_MARKER")
                .withContext("level", "trace")
                .withContext("context", "unit-test.Class")
                .trace(
                        "trace-message {}",
                        new RuntimeException("trace-error"),
                        "ARG"); // Should not be forwarded since INFO is configured as root level

        try (final AutoCloseable closeable =
                Context.getThreadLocalContext().add("thread-local", "thread-local-value")) {
            logger.withMarker("INFO_MARKER")
                    .withContext("level", "info")
                    .withContext("context", "unit-test.Class")
                    .info("info-message {}", new RuntimeException("info-error"), "ARG");
        } catch (final Exception e) {
            Assertions.fail();
        }

        Context.getGlobalContext().remove("global");

        logger.withMarker("INFO_MARKER")
                .withContext("level", "info")
                .withContext("context", "unit-test.Class")
                .info("info-message2 {}", new RuntimeException("info-error2"), "ARG2");

        // then
        final List<LogEvent> loggedEvents = handler.getEvents();
        Assertions.assertEquals(2, loggedEvents.size());

        final LogEvent event1 = loggedEvents.get(0);
        Assertions.assertEquals("info-message ARG", event1.message().getMessage());
        Assertions.assertEquals(Level.INFO, event1.level());
        Assertions.assertEquals(
                Map.of(
                        "context",
                        "unit-test.Class",
                        "global",
                        "global-value",
                        "thread-local",
                        "thread-local-value",
                        "level",
                        "info"),
                event1.context());
        Assertions.assertEquals("test-logger", event1.loggerName());
        Assertions.assertEquals(new Marker("INFO_MARKER"), event1.marker());
        Assertions.assertEquals(Thread.currentThread().getName(), event1.threadName());
        Assertions.assertNotNull(event1.throwable());
        Assertions.assertEquals("info-error", event1.throwable().getMessage());
        Assertions.assertEquals(RuntimeException.class, event1.throwable().getClass());
        Assertions.assertTrue(event1.timestamp() > startTime);
        Assertions.assertTrue(event1.timestamp() <= System.currentTimeMillis());

        final LogEvent event2 = loggedEvents.get(1);
        Assertions.assertEquals("info-message2 ARG2", event2.message().getMessage());
        Assertions.assertEquals(Level.INFO, event2.level());
        Assertions.assertEquals(Map.of("context", "unit-test.Class", "level", "info"), event2.context());
        Assertions.assertEquals("test-logger", event2.loggerName());
        Assertions.assertEquals(new Marker("INFO_MARKER"), event2.marker());
        Assertions.assertEquals(Thread.currentThread().getName(), event2.threadName());
        Assertions.assertNotNull(event2.throwable());
        Assertions.assertEquals("info-error2", event2.throwable().getMessage());
        Assertions.assertEquals(RuntimeException.class, event2.throwable().getClass());
        Assertions.assertTrue(event1.timestamp() > startTime);
        Assertions.assertTrue(event1.timestamp() <= System.currentTimeMillis());

        Assertions.assertTrue(event1.timestamp() <= event2.timestamp());
    }

    @Test
    @DisplayName("Test that accept passes complex log calls correctly with all informations to the configured handler")
    void testAcceptComplexHandling() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);

        LogEvent event1 = loggingSystem
                .getLogEventFactory()
                .createLogEvent(
                        Level.INFO,
                        "test-logger",
                        Thread.currentThread().getName(),
                        System.currentTimeMillis(),
                        "message",
                        new RuntimeException("error"),
                        new Marker("INFO_MARKER"),
                        Map.of("context", "unit-test.Class", "level", "info"));
        Context.getGlobalContext().add("new-global", "new-global-value");
        LogEvent event2 = loggingSystem
                .getLogEventFactory()
                .createLogEvent(
                        Level.TRACE,
                        "test-logger",
                        "trace-message"); // should not be forwarded since INFO is configured as root level
        LogEvent event3 =
                loggingSystem.getLogEventFactory().createLogEvent(Level.ERROR, "test-logger", "error-message");
        LogEvent event4 = loggingSystem
                .getLogEventFactory()
                .createLogEvent(
                        Level.INFO,
                        "test-logger",
                        Thread.currentThread().getName(),
                        System.currentTimeMillis(),
                        "message",
                        new RuntimeException("error"),
                        new Marker("INFO_MARKER"),
                        Map.of("context", "unit-test.Class"));

        // when
        loggingSystem.accept(event1);
        loggingSystem.accept(event2);
        loggingSystem.accept(event3);
        loggingSystem.accept(event4);

        // then
        final List<LogEvent> loggedEvents = handler.getEvents();
        Assertions.assertEquals(3, loggedEvents.size());
        Assertions.assertEquals(event1, loggedEvents.get(0));
        Assertions.assertEquals(
                new DefaultLogEvent(
                        event3.level(),
                        event3.loggerName(),
                        event3.threadName(),
                        event3.timestamp(),
                        event3.message(),
                        event3.throwable(),
                        event3.marker(),
                        Map.of("new-global", "new-global-value")),
                loggedEvents.get(1));
        Assertions.assertEquals(
                loggingSystem
                        .getLogEventFactory()
                        .createLogEvent(
                                event4.level(),
                                event4.loggerName(),
                                event4.threadName(),
                                event4.timestamp(),
                                event4.message(),
                                event4.throwable(),
                                event4.marker(),
                                Map.of("context", "unit-test.Class", "new-global", "new-global-value")),
                loggedEvents.get(2));
    }

    @Test
    @Disabled
    @DisplayName("Test that any exception in a handler will not be thrown but logged instead")
    void testExceptionInHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(new LogHandler() {
            @Override
            public String getName() {
                return "ExceptionThrowingHandler";
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void accept(LogEvent event) {
                throw new RuntimeException("Exception in handler");
            }
        });

        // when
        loggingSystem.accept(loggingSystem.getLogEventFactory().createLogEvent(Level.INFO, "logger", "message"));

        final List<LogEvent> loggedErrorEvents = getLoggedEvents();

        Assertions.assertEquals(1, loggedErrorEvents.size(), "There should be 1 ERROR event");
    }

    @Test
    @DisplayName("Test that empty configuration throws no exception")
    void testHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.installHandlers();

        // then
        assertThat(loggingSystem.getHandlers()).hasSize(0);
    }

    @Test
    @Disabled
    @DisplayName("Test that unknown handler type throws no exception")
    void testUnknownTypeHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.handler.CRYPTO_FILE.type", "space")
                .getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.installHandlers();

        // then
        assertThat(loggingSystem.getHandlers()).hasSize(0);
    }

    @Test
    @DisplayName("Test that installing handler with known type but not enabled throws no exception")
    void testAddHandlerWithoutEnabled() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.handler.CRYPTO_FILE.enabled", "false")
                .withValue("logging.handler.CRYPTO_FILE.type", "console")
                .getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.installHandlers();

        // then
        assertThat(loggingSystem.getHandlers()).hasSize(0);
    }

    @Test
    @DisplayName("Test that installing known handler type and enabled throws no exception")
    void testAddHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.handler.CRYPTO_FILE.enabled", "true")
                .withValue("logging.handler.CRYPTO_FILE.type", "console")
                .getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.installHandlers();

        // then
        assertThat(loggingSystem.getHandlers()).hasSize(1);
    }

    @Test
    @DisplayName("Test that installing multiple known handler type and enabled throws no exception")
    void testAddMultipleHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.handler.CRYPTO_FILE.enabled", "true")
                .withValue("logging.handler.CRYPTO_FILE.type", "console")
                .withValue("logging.handler.TRANSACTION_FILE.enabled", "true")
                .withValue("logging.handler.TRANSACTION_FILE.type", "console")
                .getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.installHandlers();

        // then
        assertThat(loggingSystem.getHandlers()).hasSize(2);
    }

    @Test
    @Disabled
    void testSpecWithLoggingSystemWithoutHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        // then
        testSpec(loggingSystem);
    }

    @Test
    void testSpecWithLoggingSystemWithHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        LogHandler handler = new LogHandler() {
            @Override
            public void accept(LogEvent logEvent) {
                // We do not handle any events
            }
        };
        loggingSystem.addHandler(handler);

        // then
        testSpec(loggingSystem);
    }

    @Test
    @DisplayName("Test that installing multiple known handler type and enabled throws no exception")
    void testAddMultipleFileHandler() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.handler.CRYPTO_FILE.enabled", "true")
                .withValue("logging.handler.CRYPTO_FILE.type", "file")
                .withValue("logging.handler.CRYPTO_FILE.file", "crypto.log")
                .withValue("logging.handler.TRANSACTION_FILE.enabled", "true")
                .withValue("logging.handler.TRANSACTION_FILE.type", "file")
                .withValue("logging.handler.TRANSACTION_FILE.file", "transaction.log")
                .getOrCreateConfig();

        loggingSystem = new LoggingSystem(configuration);

        // when
        loggingSystem.installHandlers();

        // then
        assertThat(loggingSystem.getHandlers()).hasSize(2);
        tempFiles.forEach(path -> {
            assertThat(path.toFile()).exists();
        });
    }

    static void testSpec(LoggingSystem system) {
        LoggerApiSpecTest.testSpec(system.getLogger("test-name"));
        LoggerApiSpecTest.testSpec(system.getLogger(null));
    }
}
