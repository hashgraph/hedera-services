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

package com.swirlds.logging.benchmark.swirldslog.rolling;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.benchmark.config.LoggingBenchmarkConfig;
import com.swirlds.logging.benchmark.util.ConfigManagement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Convenience methods for configuring swirlds-logging logger
 */
public class RollingSwirldsLogConfig implements LoggingBenchmarkConfig<LoggingSystem> {

    /**
     * {@inheritDoc}
     * @param logFile
     */
    public @NonNull LoggingSystem configureFileLogging(final String logFile) {
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.enabled", "true")
                .withValue("logging.handler.file.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", logFile)
                .withValue("logging.handler.file.file-rolling.maxFileSize", "500MB")
                .withValue("logging.handler.file.file-rolling.maxRollover", "1")
                .withValue("logging.provider.log4j.enabled", "true")
                .build();

        return configure(configuration);
    }

    /**
     * {@inheritDoc}
     */
    public @NonNull LoggingSystem configureConsoleLogging() {
        final Configuration configuration = ConfigurationBuilder.create()
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
     * @param logFile
     */
    public @NonNull LoggingSystem configureFileAndConsoleLogging(final String logFile) {
        final Configuration configuration = ConfigurationBuilder.create()
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
                .withValue("logging.handler.file.file-rolling.maxFileSize", "30MB")
                .withValue("logging.handler.file.file-rolling.maxRollover", "1")
                .withValue("logging.handler.console.level", "trace")
                .build();

        return configure(configuration);
    }

    @NonNull
    private LoggingSystem configure(@NonNull final Configuration configuration) {
        LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.installHandlers();
        loggingSystem.installProviders();
        return loggingSystem;
    }
}
