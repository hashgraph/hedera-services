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

package com.swirlds.logging.benchmark.log4j2;

import static com.swirlds.logging.benchmark.config.Constants.FILE_TYPE;
import static com.swirlds.logging.log4j.appender.SwirldsLogAppender.APPENDER_NAME;

import com.swirlds.logging.benchmark.config.AppenderType;
import com.swirlds.logging.benchmark.config.Constants;
import com.swirlds.logging.benchmark.config.FileRollingMode;
import com.swirlds.logging.benchmark.config.LoggingBenchmarkConfig;
import com.swirlds.logging.benchmark.swirldslog.SwirldsLoggingConfigFactory;
import com.swirlds.logging.benchmark.util.ConfigManagement;
import com.swirlds.logging.benchmark.util.LogFiles;
import com.swirlds.logging.log4j.appender.SwirldsLogAppender;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.spi.LoggerContext;

public class Log4JLoggerFactory {

    protected static final String PATTERN =
            (ConfigManagement.formatTimestamp() ? "%d{yyyy-MM-dd HH:mm:ss.SSS}" : "%d{UNIX_MILLIS}")
                    + " %-5level [%t] %c - %msg - [%marker] %X %n%throwable";
    private static final String CONSOLE_APPENDER_NAME = "console";
    private static final String FILE_APPENDER_NAME = "file";

    private static final String FORWARD_APPENDER_NAME = "forward";

    private static final String LOGGER_NAME = Constants.LOG4J2 + "Benchmark";
    public static final String CONTEXT_SELECTOR_PROPERTY = "log4j2.contextSelector";

    public Log4JLoggerFactory() {
        PluginManager.addPackage(SwirldsLogAppender.class.getPackage().getName());
    }

    @NonNull
    public Logger createLogger(final @NonNull LoggingBenchmarkConfig config) {
        final LoggerContext context = createLoggerContext(config);
        return context.getLogger(LOGGER_NAME);
    }

    @NonNull
    private static LoggerContext createLoggerContext(final @NonNull LoggingBenchmarkConfig config) {
        System.clearProperty(CONTEXT_SELECTOR_PROPERTY);
        final String fileName = LogFiles.provideLogFilePath(
                Constants.LOG4J2,
                FILE_TYPE,
                config.appenderType().getFileRollingMode().toString());
        if (config.forwardToSwirldsLogging()) {
            SwirldsLoggingConfigFactory swirldsLoggingConfigFactory = new SwirldsLoggingConfigFactory();
            swirldsLoggingConfigFactory.createLoggingSystem(config);
            return createForwardLoggingContext();
        } else if (config.appenderType() == AppenderType.CONSOLE_AND_ROLLING_FILE
                || config.appenderType() == AppenderType.CONSOLE_AND_SINGLE_FILE) {
            return createFileAndConsoleLoggingContext(
                    fileName, config.appenderType().getFileRollingMode());
        } else if (config.appenderType() == AppenderType.CONSOLE_ONLY) {
            return createConsoleLoggingContext();
        } else if (config.appenderType() == AppenderType.SINGLE_FILE_ONLY
                || config.appenderType() == AppenderType.ROLLING_FILE_ONLY) {
            return createFileLoggingContext(fileName, config.appenderType().getFileRollingMode());
        }
        throw new IllegalArgumentException("Invalid appender type");
    }

    @NonNull
    private static LoggerContext createForwardLoggingContext() {
        final var builder = createConfigurationBuilder("forwardLoggingConfig");
        builder.add(createForwardToSwirldsLoggingAppender(builder));
        builder.add(createRootLoggerBuilder(builder).add(builder.newAppenderRef(FORWARD_APPENDER_NAME)));
        return create(builder);
    }

    @NonNull
    private static LoggerContext createConsoleLoggingContext() {
        final var builder = createConfigurationBuilder("consoleLoggingConfig");
        builder.add(createConsoleAppender(builder));
        builder.add(createRootLoggerBuilder(builder).add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)));
        return create(builder);
    }

    @NonNull
    private static LoggerContext createFileLoggingContext(final String logFile, final FileRollingMode mode) {
        final var builder = createConfigurationBuilder("fileLoggingConfig");
        if (Objects.equals(mode, FileRollingMode.NO_ROLLING)) {
            builder.add(createSingleFileAppender(builder, logFile));
        } else {
            builder.add(createRollingFileAppender(builder, logFile));
        }
        builder.add(createRootLoggerBuilder(builder).add(builder.newAppenderRef(FILE_APPENDER_NAME)));
        return create(builder);
    }

    @NonNull
    private static LoggerContext createFileAndConsoleLoggingContext(final String logFile, final FileRollingMode mode) {
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("fileAndConsoleLoggingConfig");
        if (Objects.equals(mode, FileRollingMode.NO_ROLLING)) {
            builder.add(createSingleFileAppender(builder, logFile));
        } else {
            builder.add(createRollingFileAppender(builder, logFile));
        }
        builder.add(createConsoleAppender(builder));
        builder.add(createRootLoggerBuilder(builder)
                .add(builder.newAppenderRef(FILE_APPENDER_NAME))
                .add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)));
        return create(builder);
    }

    @NonNull
    private static AppenderComponentBuilder createConsoleAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder) {
        final LayoutComponentBuilder layoutComponentBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(CONSOLE_APPENDER_NAME, "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(layoutComponentBuilder);
    }

    @NonNull
    private static AppenderComponentBuilder createSingleFileAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder, final @NonNull String path) {
        final LayoutComponentBuilder layoutBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(FILE_APPENDER_NAME, "File")
                .addAttribute("fileName", path)
                .addAttribute("append", true)
                .add(layoutBuilder);
    }

    @NonNull
    private static AppenderComponentBuilder createRollingFileAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder, final @NonNull String path) {
        final LayoutComponentBuilder layoutBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);

        return builder.newAppender(FILE_APPENDER_NAME, "RollingFile")
                .addAttribute("fileName", path)
                .addAttribute("filePattern", path.replace(".log", "") + ".%i.log")
                .addAttribute("append", true)
                .add(layoutBuilder)
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy"))
                .addComponent(builder.newComponent("DefaultRolloverStrategy").addAttribute("max", 1))
                .addAttribute("size", "30MB");
    }

    @NonNull
    private static AppenderComponentBuilder createForwardToSwirldsLoggingAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder) {
        return builder.newAppender(FORWARD_APPENDER_NAME, APPENDER_NAME);
    }

    private static ConfigurationBuilder<BuiltConfiguration> createConfigurationBuilder(
            final @NonNull String configName) {
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName(configName);
        return builder;
    }

    private static LoggerContext create(final @NonNull ConfigurationBuilder<BuiltConfiguration> builder) {
        final org.apache.logging.log4j.core.config.Configuration configuration = builder.build();
        final org.apache.logging.log4j.core.LoggerContext context = Configurator.initialize(configuration);
        LogManager.getFactory().removeContext(context);
        return Configurator.initialize(configuration);
    }

    private static RootLoggerComponentBuilder createRootLoggerBuilder(
            ConfigurationBuilder<BuiltConfiguration> builder) {
        return builder.newRootLogger(Level.TRACE);
    }
}
