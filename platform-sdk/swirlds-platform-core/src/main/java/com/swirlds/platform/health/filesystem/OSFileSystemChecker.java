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

package com.swirlds.platform.health.filesystem;

import static com.swirlds.common.formatting.StringFormattingUtils.addLine;
import static com.swirlds.platform.health.OSHealthCheckUtils.reportHeader;

import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.utility.Units;
import com.swirlds.platform.Settings;
import java.util.concurrent.TimeUnit;

/**
 * Utility class that performs file system checks and writes the report to a {@link StringBuilder}.
 */
public final class OSFileSystemChecker {

    private OSFileSystemChecker() {}

    public static boolean performFileSystemCheck(final StringBuilder sb, final OSHealthCheckConfig osHealthConfig) {
        try {
            final OSFileSystemCheck.Report fileSystemReport = OSFileSystemCheck.execute(
                    Settings.getInstance().getConfigPath(), osHealthConfig.fileReadTimeoutMillis());
            return appendReport(sb, fileSystemReport, osHealthConfig.maxFileReadMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while performing OS file system check", e);
        }
    }

    /**
     * Append the results of the file system check report to the string builder.
     *
     * @param sb
     * 		the string builder to append to
     * @param fileSystemReport
     * 		the file system check report
     * @param maxFileReadMillis
     * 		the maximum number of millis the file read may take before it is considered failed
     * @return {@code true} if the check passed, {@code false} otherwise
     */
    private static boolean appendReport(
            final StringBuilder sb, final OSFileSystemCheck.Report fileSystemReport, final long maxFileReadMillis) {
        if (fileSystemReport.code() == OSFileSystemCheck.TestResultCode.SUCCESS) {
            final double readMillis = fileSystemReport.readNanos() * Units.NANOSECONDS_TO_MILLISECONDS;
            if (TimeUnit.NANOSECONDS.toMillis(fileSystemReport.readNanos()) > maxFileReadMillis) {
                reportHeader(sb, OSFileSystemCheck.Report.name(), false);
                addLine(
                        sb,
                        String.format(
                                "OS file read time was too slow. Maximum millis allowed: %d, Took: %s",
                                maxFileReadMillis, readMillis));
                return false;
            } else {
                reportHeader(sb, OSFileSystemCheck.Report.name(), true);
                addLine(sb, fileSystemReport.toString());
                return true;
            }
        } else {
            reportHeader(sb, OSFileSystemCheck.Report.name(), false);
            if (fileSystemReport.exception() == null) {
                addLine(sb, String.format("OS file check failed. Code: %s", fileSystemReport.code()));
            } else {
                addLine(
                        sb,
                        String.format(
                                "OS file check failed with exception. Code: %s%n%s",
                                fileSystemReport.code(), fileSystemReport.exception()));
            }
            return false;
        }
    }
}
