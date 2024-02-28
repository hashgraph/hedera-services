/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.logging.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class ConfigureLog4J {

    private final static String PATTERN = "%d %c [%t] %-5level: %msg [%marker] %X %n%throwable";
    public static final String LOG_FILE = "logging-out/log-like-hell-benchmark-log4j2.log";

    public static void deleteOldLogFiles() {
        try {
            Files.deleteIfExists(Path.of(LOG_FILE));
        } catch (IOException e) {
            throw new RuntimeException("Can not delete old log file", e);
        }
    }

    public static void configureConsoleLogging() {
        System.clearProperty("log4j2.contextSelector");

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("loggingConfig");

        builder.add(createConsoleAppender("console", builder));

        builder.add(builder.newRootLogger(Level.DEBUG)
                .add(builder.newAppenderRef("console")));
        Configurator.initialize(builder.build());
    }

    public static void configureFileLogging() {
        deleteOldLogFiles();
        System.clearProperty("log4j2.contextSelector");

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("loggingConfig");

        builder.add(createFileAppender("file", builder));

        builder.add(builder.newRootLogger(Level.DEBUG)
                .add(builder.newAppenderRef("file")));
        Configurator.initialize(builder.build());
    }

    public static void configureFileAndConsoleLogging() {
        deleteOldLogFiles();
        System.clearProperty("log4j2.contextSelector");

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.ERROR);
        builder.setConfigurationName("loggingConfig");

        builder.add(createFileAppender("file", builder));
        builder.add(createConsoleAppender("console", builder));

        builder.add(builder.newRootLogger(Level.DEBUG)
                .add(builder.newAppenderRef("file"))
                .add(builder.newAppenderRef("console")));
        Configurator.initialize(builder.build());
    }

    public static AppenderComponentBuilder createConsoleAppender(final String name,
            final ConfigurationBuilder<BuiltConfiguration> builder) {
        final LayoutComponentBuilder layoutComponentBuilder = builder.newLayout("PatternLayout")
                .addAttribute("pattern", PATTERN);
        return builder.newAppender(name, "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(layoutComponentBuilder);
    }

    public static AppenderComponentBuilder createFileAppender(final String name,
            final ConfigurationBuilder<BuiltConfiguration> builder) {
        LayoutComponentBuilder layoutBuilder = builder.newLayout("PatternLayout")
                .addAttribute("pattern", PATTERN);
        return builder.newAppender(name, "File")
                .addAttribute("fileName", LOG_FILE)
                .addAttribute("append", false)
                .add(layoutBuilder);
    }

}
