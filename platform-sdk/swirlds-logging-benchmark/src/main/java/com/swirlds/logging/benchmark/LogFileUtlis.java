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

package com.swirlds.logging.benchmark;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LogFileUtlis {

    @NonNull
    public static String provideLogFilePath(LoggingImplementation implementation, LoggingHandlingType type) {
        final String path = getPath(implementation, type);
        deleteFile(path);
        return path;
    }

    @NonNull
    public static String getPath(final LoggingImplementation implementation, final LoggingHandlingType type) {
        final long pid = ProcessHandle.current().pid();
        return "logging-out/benchmark-" + implementation + "-" + pid + "-" + type + ".log";
    }

    private static void deleteFile(final String logFile) {
        try {
            Files.deleteIfExists(Path.of(logFile));
        } catch (IOException e) {
            throw new RuntimeException("Can not delete old log file", e);
        }
    }

    public static List<String> getLogStatementsFromLogFile(
            final LoggingImplementation implementation, final LoggingHandlingType type) throws IOException {
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(getPath(implementation, type)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    public static List<String> linesToStatements(String logName, List<String> logLines) {
        List<String> result = new ArrayList<>();
        StringBuilder previousLine = new StringBuilder();

        for (String line : logLines) {
            if (line.isEmpty()) {
                continue;
            } else if (line.contains(logName)) {
                if (!previousLine.isEmpty()) {
                    result.add(previousLine.toString());
                    previousLine.setLength(0);
                }
                previousLine.append(line);
            } else {
                previousLine.append("\n").append(line);
            }
        }
        if (!previousLine.isEmpty()) {
            result.add(previousLine.toString());
        }

        return result;
    }

    // Method to extract the number at the start of the line
    public static long extractNumber(String line) {
        // Split the line by space and get the first token
        String[] parts = line.split("\\s+", 2);
        return Long.parseLong(parts[0]);
    }

    public static boolean isSorted(List<Long> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i) > list.get(i + 1)) {
                return false; // If any element is greater than the next one, the list is not sorted
            }
        }
        return true; // If the loop completes without returning false, the list is sorted
    }
}
