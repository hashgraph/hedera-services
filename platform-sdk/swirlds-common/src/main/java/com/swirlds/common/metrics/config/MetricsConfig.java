/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Min;
import java.time.Duration;

/**
 * Configuration for Metrics
 *
 * @param metricsUpdatePeriodMillis
 *         the metrics update period in milliseconds
 * @param disableMetricsOutput
 *         Disable all metrics-outputs. If {@code true}, this overrides all other specific settings
 *         concerning metrics-output.
 * @param csvOutputFolder
 *         The absolute or relative folder path where all the statistics CSV files will be written.
 *         If this value is null or an empty string, the current folder selection behavior will be
 *         used (ie: the SDK base path).
 * @param csvFileName
 *         The prefix of the name of the CSV file that the platform will write statistics to.
 *         If this value is null or an empty string, the platform will not write any statistics.
 * @param csvAppend
 *         Indicates whether statistics should be appended to the CSV file.
 * @param csvWriteFrequency
 *         The frequency, in milliseconds, at which values are written to the statistics CSV file.
 * @param metricsDocFileName
 *         the file name to be used for Metrics document generation
 */
@ConfigData
public record MetricsConfig(
        @Min(0) @ConfigProperty(defaultValue = "1000") long metricsUpdatePeriodMillis,
        @ConfigProperty(defaultValue = "false") boolean disableMetricsOutput,
        @ConfigProperty(defaultValue = "") String csvOutputFolder,
        @ConfigProperty(defaultValue = "") String csvFileName,
        @ConfigProperty(defaultValue = "false") boolean csvAppend,
        @Min(0) @ConfigProperty(defaultValue = "3000") int csvWriteFrequency,
        @ConfigProperty(defaultValue = "metricsDoc.tsv") String metricsDocFileName) {

    /**
     * Returns the metrics update interval time as a {@link Duration}.
     *
     * @return the metrics update duration
     */
    public Duration getMetricsUpdateDuration() {
        return Duration.ofMillis(metricsUpdatePeriodMillis);
    }

    /**
     * Returns the metrics snapshot interval time as a {@link Duration}.
     *
     * @return the metrics snapshot duration
     */
    public Duration getMetricsSnapshotDuration() {
        return Duration.ofMillis(csvWriteFrequency);
    }
}
