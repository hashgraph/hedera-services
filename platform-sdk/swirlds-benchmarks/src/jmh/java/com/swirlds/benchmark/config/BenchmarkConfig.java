/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Settimgs for the benchmarks
 *
 * @param benchmarkData
 * 		parameter had no description in the old settings
 * @param saveDataDirectory
 * 		parameter had no description in the old settings
 * @param verifyResult
 * 		parameter had no description in the old settings
 * @param printHistogram
 * 		parameter had no description in the old settings
 * @param csvOutputFolder
 * 		The absolute or relative folder path where all the statistics CSV files will be written. If this value is null
 * 		or an empty string, the current folder selection behavior will be used (ie: the SDK base
 * 		path).
 * @param csvFileName
 * 		The prefix of the name of the CSV file that the platform will write statistics to. If this value is null or an
 * 		empty string, the platform will not write any statistics.
 * @param csvWriteFrequency
 * 		The frequency, in milliseconds, at which values are written to the statistics CSV file.
 * @param csvAppend
 * 		Indicates whether statistics should be appended to the CSV file.
 */
@ConfigData("benchmark")
public record BenchmarkConfig(
        @ConfigProperty(defaultValue = "") String benchmarkData,
        @ConfigProperty(defaultValue = "false") boolean saveDataDirectory,
        @ConfigProperty(defaultValue = "true") boolean verifyResult,
        @ConfigProperty(defaultValue = "false") boolean printHistogram,
        @ConfigProperty(defaultValue = "") String csvOutputFolder,
        @ConfigProperty(defaultValue = "BenchmarkMetrics.csv") String csvFileName,
        @ConfigProperty(defaultValue = "0") int csvWriteFrequency,
        @ConfigProperty(defaultValue = "false") boolean csvAppend,
        @ConfigProperty(defaultValue = "sda") String deviceName) {
    public String toString() {
        return String.format(
                "[benchmarkData=\"%s\", saveDataDirectory=%b, verifyResult=%b, printHistogram=%b,"
                        + " csvOutputFolder=\"%s\", csvFileName=\"%s\", csvWriteFrequency=%d, csvAppend=%b, deviceName=\"%s\"]",
                benchmarkData,
                saveDataDirectory,
                verifyResult,
                printHistogram,
                csvOutputFolder,
                csvFileName,
                csvWriteFrequency,
                csvAppend,
                deviceName);
    }
}
