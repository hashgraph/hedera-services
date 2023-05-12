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

package com.swirlds.platform.health;

import com.swirlds.common.utility.Units;
import com.swirlds.platform.health.clock.OSClockSourceSpeedCheck;
import com.swirlds.platform.health.entropy.OSEntropyCheck;
import com.swirlds.platform.health.filesystem.OSFileSystemCheck;
import java.nio.file.Path;

/**
 * Command line tool for running the OS health checks and printing the results.
 */
public final class OSHealthCheckMain {

    private OSHealthCheckMain() {}

    /**
     * Prints the results of the OS health checks using system defaults
     */
    public static void main(final String[] args) {
        printOSClockSpeedReport();
        printOSEntropyReport();
        if (args.length > 0) {
            printOSFileSystemReport(Path.of(args[0]));
        }
    }

    private static void printOSFileSystemReport(final Path fileToRead) {
        try {
            final OSFileSystemCheck.Report report = OSFileSystemCheck.execute(fileToRead);
            if (report.code() == OSFileSystemCheck.TestResultCode.SUCCESS) {
                final double elapsedMillis = report.readNanos() * Units.NANOSECONDS_TO_MILLISECONDS;
                System.out.printf(
                        "File system check passed, took %d nanos (%s millis) "
                                + "to open the file and read 1 byte (data=%s)%n",
                        report.readNanos(), elapsedMillis, report.data());
            } else {
                if (report.exception() == null) {
                    System.out.printf("File system check failed. Reason: %s%n", report.code());
                } else {
                    System.out.printf(
                            "File system check failed with exception. Reason: %s%n%s%n",
                            report.code(), report.exception());
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Thread interrupted while checking the file system");
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void printOSClockSpeedReport() {
        final OSClockSourceSpeedCheck.Report clockSpeedReport = OSClockSourceSpeedCheck.execute();
        System.out.printf("Average clock source speed: %d calls/sec%n", clockSpeedReport.callsPerSec());
    }

    private static void printOSEntropyReport() {
        try {
            final OSEntropyCheck.Report randomSpeed = OSEntropyCheck.execute();
            if (randomSpeed.success()) {
                final double elapsedMillis = randomSpeed.elapsedNanos() * Units.NANOSECONDS_TO_MILLISECONDS;
                System.out.printf(
                        "First random number generation time: %d nanos (%s millis), generated long=%d%n",
                        randomSpeed.elapsedNanos(), elapsedMillis, randomSpeed.randomLong());
            } else {
                System.out.println("Random number generation check failed due to timeout");
            }
        } catch (InterruptedException e) {
            System.out.println("Thread interrupted while measuring the random number generation speed");
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
