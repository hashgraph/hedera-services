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

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.console.ConsoleHandlerFactory;
import com.swirlds.logging.file.FileHandlerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigureLog {

    public static final String LOG_FILE = "logging-out/log-like-hell-benchmark.log";

    public static void deleteOldLogFiles() {
        try {
            Files.deleteIfExists(Path.of(LOG_FILE));
        } catch (IOException e) {
            throw new RuntimeException("Can not delete old log file", e);
        }
    }

    public static LoggingSystem configureFileLogging() {
        deleteOldLogFiles();
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.active", "true")
                .withValue("logging.handler.file.formatTimestamp", "false")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", LOG_FILE)
                .build();
        final LogHandler fileHandler = new FileHandlerFactory().create("file", configuration);
        LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(fileHandler);
        return loggingSystem;
    }

    public static LoggingSystem configureConsoleLogging() {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.active", "true")
                .withValue("logging.handler.console.formatTimestamp", "false")
                .withValue("logging.handler.console.level", "trace")
                .build();
        final LogHandler consoleHandler = new ConsoleHandlerFactory().create("console", configuration);
        LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(consoleHandler);
        return loggingSystem;
    }

    public static LoggingSystem configureFileAndConsoleLogging() {
        deleteOldLogFiles();
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.active", "true")
                .withValue("logging.handler.file.formatTimestamp", "false")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", LOG_FILE)
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.active", "true")
                .withValue("logging.handler.console.formatTimestamp", "false")
                .withValue("logging.handler.console.level", "trace")
                .build();
        final LogHandler fileHandler = new FileHandlerFactory().create("file", configuration);
        final LogHandler consoleHandler = new ConsoleHandlerFactory().create("console", configuration);
        LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.addHandler(fileHandler);
        loggingSystem.addHandler(consoleHandler);
        return loggingSystem;
    }
}
