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

package com.swirlds.platform.health.entropy;

import static com.swirlds.common.formatting.StringFormattingUtils.addLine;
import static com.swirlds.platform.health.OSHealthCheckUtils.reportHeader;

import com.swirlds.common.config.OSHealthCheckConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class that performs OS entropy checks and writes the report to a {@link StringBuilder}.
 */
public final class OSEntropyChecker {

    private OSEntropyChecker() {}

    /**
     * Perform entropy checks using the various sources of {@link java.security.SecureRandom} used in the platform.
     *
     * @param sb
     * 		the string builder to append the report to
     * @param osHealthCheckConfig
     * 		config values for OS health checks
     * @return {@code true} if all the checks passed, {@code false} otherwise
     */
    public static boolean performEntropyChecks(final StringBuilder sb, final OSHealthCheckConfig osHealthCheckConfig) {
        return performEntropyChecks(
                sb,
                osHealthCheckConfig,
                List.of(
                        // Test this type because it is used by the platform, check that it does not hang
                        EntropySource.strong(),
                        // Checks the availability of OS entropy
                        EntropySource.of("NativePRNGBlocking", "SUN")));
    }

    private static boolean performEntropyChecks(
            final StringBuilder sb,
            final OSHealthCheckConfig osHealthCheckConfig,
            final Iterable<EntropySource> entropySources) {
        boolean allPassed = true;
        for (final EntropySource entropySource : entropySources) {
            allPassed &= performEntropyCheck(sb, osHealthCheckConfig, entropySource);
        }
        return allPassed;
    }

    private static boolean performEntropyCheck(
            final StringBuilder sb, final OSHealthCheckConfig osHealthConfig, final EntropySource entropySource) {
        final long maxGenNanos = TimeUnit.MILLISECONDS.toNanos(osHealthConfig.maxRandomNumberGenerationMillis());
        try {
            final OSEntropyCheck.Report entropyReport =
                    OSEntropyCheck.execute(osHealthConfig.entropyTimeoutMillis(), entropySource);
            return appendReport(sb, entropyReport, maxGenNanos);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while performing OS entropy check", e);
        }
    }

    /**
     * Append the results of the entropy check report to the string builder.
     *
     * @param sb
     * 		the string builder to append to
     * @param entropyReport
     * 		the entropy check report
     * @param maxGenNanos
     * 		the maximum number of nanos the check may take before it is considered failed
     * @return {@code true} if the check passed, {@code false} otherwise
     */
    private static boolean appendReport(
            final StringBuilder sb, final OSEntropyCheck.Report entropyReport, final long maxGenNanos) {
        if (!entropyReport.success()) {
            reportHeader(sb, OSEntropyCheck.Report.name(), false);
            addLine(sb, "OS entropy check timed out.");
            return false;
        } else if (entropyReport.elapsedNanos() > maxGenNanos) {
            reportHeader(sb, OSEntropyCheck.Report.name(), false);
            addLine(
                    sb,
                    String.format(
                            "OS random number generation (first call, not an average) is too slow. "
                                    + "Maximum nanos allowed: %d, Took: %d",
                            maxGenNanos, entropyReport.elapsedNanos()));
            return false;
        } else {
            reportHeader(sb, OSEntropyCheck.Report.name(), true);
            addLine(sb, entropyReport.toString());
            return true;
        }
    }
}
