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

package com.swirlds.logging.benchmark;

import com.swirlds.logging.benchmark.config.LoggingHandlingType;
import com.swirlds.logging.benchmark.config.LoggingImplementation;
import com.swirlds.logging.benchmark.util.LogFiles;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.spi.LoggerContext;

public class ConfigureLog4J {

    private static final String PATTERN = "%d{UNIX_MILLIS} %-5level [%t] %c - %msg - [%marker] %X %n%throwable";
    public static final String CONSOLE_APPENDER_NAME = "console";
    public static final String FILE_APPENDER_NAME = "file";

    public static @NonNull LoggerContext configureConsoleLogging() {
        System.clearProperty("log4j2.contextSelector");
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("consoleLoggingConfig");
        builder.add(createConsoleAppender(CONSOLE_APPENDER_NAME, builder));
        builder.add(builder.newRootLogger(Level.DEBUG).add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)));
        return create(builder);
    }

    public static @NonNull LoggerContext configureFileLogging() {
        final String logFile = LogFiles.provideLogFilePath(LoggingImplementation.LOG4J2, LoggingHandlingType.FILE);
        System.clearProperty("log4j2.contextSelector");
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.DEBUG);
        builder.setConfigurationName("fileLoggingConfig");
        builder.add(createFileAppender(FILE_APPENDER_NAME, builder, logFile));
        builder.add(builder.newRootLogger(Level.DEBUG).add(builder.newAppenderRef(FILE_APPENDER_NAME)));
        return create(builder);
    }

    public static @NonNull LoggerContext configureFileAndConsoleLogging() {
        final String logFile =
                LogFiles.provideLogFilePath(LoggingImplementation.LOG4J2, LoggingHandlingType.CONSOLE_AND_FILE);
        System.clearProperty("log4j2.contextSelector");
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("fileAndConsoleLoggingConfig");
        builder.add(createFileAppender(FILE_APPENDER_NAME, builder, logFile));
        builder.add(createConsoleAppender(CONSOLE_APPENDER_NAME, builder));
        builder.add(builder.newRootLogger(Level.DEBUG)
                .add(builder.newAppenderRef(FILE_APPENDER_NAME))
                .add(builder.newAppenderRef(CONSOLE_APPENDER_NAME)));
        return create(builder);
    }

    private static @NonNull LoggerContext create(ConfigurationBuilder<BuiltConfiguration> builder) {
        Configuration configuration = builder.build();
        org.apache.logging.log4j.core.LoggerContext context = Configurator.initialize(configuration);
        LogManager.getFactory().removeContext(context);
        // context.reconfigure(configuration);
        return Configurator.initialize(configuration);
    }

    private static AppenderComponentBuilder createConsoleAppender(
            final String name, final ConfigurationBuilder<BuiltConfiguration> builder) {
        final LayoutComponentBuilder layoutComponentBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(name, "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(layoutComponentBuilder);
    }

    private static AppenderComponentBuilder createFileAppender(
            final String name, final ConfigurationBuilder<BuiltConfiguration> builder, final String path) {
        LayoutComponentBuilder layoutBuilder =
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN);
        return builder.newAppender(name, "File")
                .addAttribute("fileName", path)
                .addAttribute("append", true)
                .add(layoutBuilder);
    }
}
