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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.time.format.DateTimeParseException;

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

    /**
     * Parse a JrsTestResult from a CSV line. The inverse of {@link #toCsvLine()}.
     *
     * @param line the CSV line
     * @return the parsed JrsTestResult, or null if the line could not be parsed
     */
    @Nullable
    public static JrsTestResult parseFromCsvLine(@NonNull final String line) {
        final String[] parts = line.split(",");

        if (parts.length != 5) {
            return null;
        }

        try {
            return new JrsTestResult(
                    new JrsTestIdentifier(parts[0], parts[1]),
                    TestStatus.valueOf(parts[2]),
                    Instant.parse(parts[3]),
                    parts[4]);
        } catch (final DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Write this JrsTestResult to a CSV line. The inverse of {@link #parseFromCsvLine(String)}.
     *
     * @return the CSV line
     */
    @NonNull
    public String toCsvLine() {
        return String.join(",", id.panel(), id.name(), status.name(), timestamp.toString(), testDirectory);
    }

    /**
     * Compare this JrsTestResult to another JrsTestResult. The comparison is based on the timestamp
     *
     * @param that the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to,
     */
    @Override
    public int compareTo(@NonNull final JrsTestResult that) {
        return that.timestamp.compareTo(this.timestamp);
    }
}
