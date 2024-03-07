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

import com.swirlds.logging.benchmark.config.Constants;
import com.swirlds.logging.benchmark.config.LoggingBenchmarkConfig;
import com.swirlds.logging.benchmark.util.ConfigManagement;
import com.swirlds.logging.benchmark.util.LogFiles;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.spi.LoggerContext;

/**
 * Convenience methods for configuring log4j logger
 */
public class Log4JLoggingBenchmarkConfig implements LoggingBenchmarkConfig<LoggerContext> {

    private static final String PATTERN =
            (ConfigManagement.formatTimestamp() ? "%d{yyyy-MM-dd HH:mm:ss.SSS}" : "%d{UNIX_MILLIS}")
                    + " %-5level [%t] %c - %msg - [%marker] %X %n%throwable";
    private static final String CONSOLE_APPENDER_NAME = "console";
    private static final String FILE_APPENDER_NAME = "file";

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggerContext configureConsoleLogging() {
        System.clearProperty("log4j2.contextSelector");
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("consoleLoggingConfig");
        builder.add(createConsoleAppender(builder));
        builder.add(builder.newRootLogger(Level.DEBUG).add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)));
        return create(builder);
    }

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggerContext configureFileLogging() {
        final String logFile = LogFiles.provideLogFilePath(Constants.LOG4J2, Constants.FILE_TYPE);
        System.clearProperty("log4j2.contextSelector");
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.DEBUG);
        builder.setConfigurationName("fileLoggingConfig");
        builder.add(createFileAppender(builder, logFile));
        builder.add(builder.newRootLogger(Level.DEBUG).add(builder.newAppenderRef(FILE_APPENDER_NAME)));
        return create(builder);
    }

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggerContext configureFileAndConsoleLogging() {
        final String logFile = LogFiles.provideLogFilePath(Constants.LOG4J2, Constants.CONSOLE_AND_FILE_TYPE);
        System.clearProperty("log4j2.contextSelector");
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("fileAndConsoleLoggingConfig");
        builder.add(createFileAppender(builder, logFile));
        builder.add(createConsoleAppender(builder));
        builder.add(builder.newRootLogger(Level.DEBUG)
                .add(builder.newAppenderRef(FILE_APPENDER_NAME))
                .add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)));
        return create(builder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tierDown() {
        if (ConfigManagement.deleteOutputFiles()) {
            LogFiles.deleteFile(LogFiles.provideLogFilePath(Constants.SWIRLDS, Constants.FILE_TYPE));
            LogFiles.deleteFile(LogFiles.provideLogFilePath(Constants.SWIRLDS, Constants.CONSOLE_AND_FILE_TYPE));
        }
        if (ConfigManagement.deleteOutputFolder()) {
            LogFiles.tryDeleteDirAndContent();
        }
    }

    private static @NonNull LoggerContext create(final @NonNull ConfigurationBuilder<BuiltConfiguration> builder) {
        final org.apache.logging.log4j.core.config.Configuration configuration = builder.build();
        final org.apache.logging.log4j.core.LoggerContext context = Configurator.initialize(configuration);
        LogManager.getFactory().removeContext(context);
        return Configurator.initialize(configuration);
    }

    private static AppenderComponentBuilder createConsoleAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder) {
        final LayoutComponentBuilder layoutComponentBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(Log4JLoggingBenchmarkConfig.CONSOLE_APPENDER_NAME, "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(layoutComponentBuilder);
    }

    private static AppenderComponentBuilder createFileAppender(
            final @NonNull ConfigurationBuilder<BuiltConfiguration> builder, final @NonNull String path) {
        final LayoutComponentBuilder layoutBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(Log4JLoggingBenchmarkConfig.FILE_APPENDER_NAME, "File")
                .addAttribute("fileName", path)
                .addAttribute("append", true)
                .add(layoutBuilder);
    }
}
