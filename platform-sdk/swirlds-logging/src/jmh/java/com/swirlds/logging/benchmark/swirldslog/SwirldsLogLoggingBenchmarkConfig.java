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

package com.swirlds.logging.benchmark.swirldslog;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.benchmark.config.Constants;
import com.swirlds.logging.benchmark.config.LoggingBenchmarkConfig;
import com.swirlds.logging.benchmark.util.ConfigManagement;
import com.swirlds.logging.benchmark.util.LogFiles;
import com.swirlds.logging.console.ConsoleHandlerFactory;
import com.swirlds.logging.file.FileHandlerFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Convenience methods for configuring swirlds-logging logger
 */
public class SwirldsLogLoggingBenchmarkConfig implements LoggingBenchmarkConfig<LoggingSystem> {

    private static final FileHandlerFactory FILE_HANDLER_FACTORY = new FileHandlerFactory();
    private static final ConsoleHandlerFactory CONSOLE_HANDLER_FACTORY = new ConsoleHandlerFactory();

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggingSystem configureFileLogging() {
        final String logFile = LogFiles.provideLogFilePath(Constants.SWIRLDS, Constants.FILE_TYPE);
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.enabled", "true")
                .withValue("logging.handler.file.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", logFile)
                .withValue("logging.provider.log4j.enabled", "true")
                .build();

        return configure(configuration);
    }

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggingSystem configureConsoleLogging() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.console.level", "trace")
                .withValue("logging.provider.log4j.enabled", "true")
                .build();

        return configure(configuration);
    }

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggingSystem configureFileAndConsoleLogging() {
        final String logFile = LogFiles.provideLogFilePath(Constants.SWIRLDS, Constants.CONSOLE_AND_FILE_TYPE);
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.enabled", "true")
                .withValue("logging.handler.file.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", logFile)
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.console.level", "trace")
                .withValue("logging.provider.log4j.enabled", "true")
                .build();

        return configure(configuration);
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

    @NonNull
    private LoggingSystem configure(@NonNull final Configuration configuration) {
        LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.installHandlers();
        loggingSystem.installProviders();
        return loggingSystem;
    }
}
