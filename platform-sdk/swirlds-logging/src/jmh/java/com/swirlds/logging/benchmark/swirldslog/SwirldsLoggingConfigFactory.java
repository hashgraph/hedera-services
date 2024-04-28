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
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.benchmark.config.AppenderType;
import com.swirlds.logging.benchmark.config.Constants;
import com.swirlds.logging.benchmark.config.LoggingBenchmarkConfig;
import com.swirlds.logging.benchmark.util.ConfigManagement;
import com.swirlds.logging.benchmark.util.LogFiles;
import edu.umd.cs.findbugs.annotations.NonNull;

public class SwirldsLoggingConfigFactory {

    public LoggingSystem createLoggingSystem(@NonNull final LoggingBenchmarkConfig config) {
        final Configuration configuration = createConfiguration(config);
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.installHandlers();
        loggingSystem.installProviders();
        return loggingSystem;
    }

    private static Configuration createConfiguration(@NonNull final LoggingBenchmarkConfig config) {
        com.swirlds.config.api.ConfigurationBuilder configurationBuilder =
                com.swirlds.config.api.ConfigurationBuilder.create();
        configurationBuilder = configurationBuilder.withValue("logging.level", "trace")
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter());

        if (config.forwardToSwirldsLogging()) {
            configurationBuilder = configurationBuilder.withValue("logging.provider.log4j.enabled", "true");
        }

        configurationBuilder = configurationBuilder
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.console.level", "trace");

        final String singleFileName = LogFiles.provideLogFilePath(Constants.SWIRLDS, "file", "no-rolling");
        configurationBuilder = configurationBuilder
                .withValue("logging.handler.singleFile.type", "file")
                .withValue("logging.handler.singleFile.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.singleFile.level", "trace")
                .withValue("logging.handler.singleFile.file", singleFileName);

        final String rollingFileName = LogFiles.provideLogFilePath(Constants.SWIRLDS, "file", "rolling");
        configurationBuilder = configurationBuilder
                .withValue("logging.handler.rollingFile.type", "file")
                .withValue("logging.handler.rollingFile.formatTimestamp", ConfigManagement.formatTimestamp() + "")
                .withValue("logging.handler.rollingFile.level", "trace")
                .withValue("logging.handler.rollingFile.file", rollingFileName)
                .withValue("logging.handler.rollingFile.file-rolling.maxFileSize", "500MB")
                .withValue("logging.handler.rollingFile.file-rolling.maxRollover", "1");

        if (config.appenderType() == AppenderType.CONSOLE_ONLY
                || config.appenderType() == AppenderType.CONSOLE_AND_ROLLING_FILE) {
            configurationBuilder = configurationBuilder.withValue("logging.handler.console.enabled", "true");
        }
        if (config.appenderType() == AppenderType.SINGLE_FILE_ONLY
                || config.appenderType() == AppenderType.CONSOLE_AND_SINGLE_FILE) {
            configurationBuilder = configurationBuilder.withValue("logging.handler.singleFile.enabled", "true");
        }
        if (config.appenderType() == AppenderType.ROLLING_FILE_ONLY
                || config.appenderType() == AppenderType.CONSOLE_AND_ROLLING_FILE) {
            configurationBuilder = configurationBuilder.withValue("logging.handler.rollingFile.enabled", "true");
        }

        return configurationBuilder.build();
    }
}
