/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.testreader;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;

/**
 * Contains the data for a JRS test report.
 *
 * @param directory   the root of the GCP directory that was read to gather this data
 * @param reportTime  the time when the data was gathered
 * @param reportSpan  the number of days covered by the report
 * @param testResults individual test results
 */
public record JrsReportData(
        @NonNull String directory,
        @NonNull Instant reportTime,
        int reportSpan,
        @NonNull List<JrsTestResult> testResults) {}
