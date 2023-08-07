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

package com.swirlds.platform.testreader;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * The result of a JRS test run.
 *
 * @param id            the test ID
 * @param status        true if the test passed, false if the test failed
 * @param timestamp     the timestamp of the test run
 * @param testDirectory the directory where the test was run
 */
public record JrsTestResult(
        @NonNull JrsTestIdentifier id,
        @NonNull TestStatus status,
        @NonNull Instant timestamp,
        @NonNull String testDirectory)
        implements Comparable<JrsTestResult> {
    @Override
    public int compareTo(@NonNull final JrsTestResult that) {
        return that.timestamp.compareTo(this.timestamp);
    }
}
