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

package com.swirlds.platform.health.clock;

import static com.swirlds.common.formatting.StringFormattingUtils.addLine;
import static com.swirlds.platform.health.OSHealthCheckUtils.reportHeader;

import com.swirlds.common.config.OSHealthCheckConfig;

/**
 * Utility class that performs OS clock source speed checks and writes the report to a {@link StringBuilder}.
 */
public final class OSClockSpeedSourceChecker {

    private OSClockSpeedSourceChecker() {}

    public static boolean performClockSourceSpeedCheck(
            final StringBuilder sb, final OSHealthCheckConfig osHealthConfig) {
        final OSClockSourceSpeedCheck.Report clockSourceSpeed = OSClockSourceSpeedCheck.execute();
        return appendReport(sb, clockSourceSpeed, osHealthConfig.minClockCallsPerSec());
    }

    /**
     * Append the results of the clock speed check report to the string builder.
     *
     * @param sb
     * 		the string builder to append to
     * @param clockReport
     * 		the clock source speed check report
     * @param minClockCallsPerSec
     * 		the minimum number of clock calls per second required for the check to pass
     * @return {@code true} if the check passed, {@code false} otherwise
     */
    private static boolean appendReport(
            final StringBuilder sb, OSClockSourceSpeedCheck.Report clockReport, final long minClockCallsPerSec) {
        if (clockReport.callsPerSec() < minClockCallsPerSec) {
            reportHeader(sb, OSClockSourceSpeedCheck.Report.name(), false);
            addLine(
                    sb,
                    String.format(
                            "OS clock source is too slow. Calls/sec Minimum: %d, Achieved: %d",
                            minClockCallsPerSec, clockReport.callsPerSec()));
            return false;
        }
        reportHeader(sb, OSClockSourceSpeedCheck.Report.name(), true);
        addLine(sb, clockReport.toString());
        return true;
    }
}
