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

package com.swirlds.logging.api.internal.format;

import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class StackTracePrinter {

    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    private static void print(@NonNull final Appendable writer, @NonNull Throwable throwable, boolean rootCause)
            throws IOException {
        if (writer == null) {
            EMERGENCY_LOGGER.logNPE("printWriter");
            return;
        }
        if (throwable == null) {
            EMERGENCY_LOGGER.logNPE("throwable");
            return;
        }

        if (!rootCause) {
            writer.append("Caused by: ");
            writer.append(System.lineSeparator());
        }

        writer.append(throwable.getClass().getName());
        writer.append(": ");
        writer.append(throwable.getMessage());
        writer.append(System.lineSeparator());
        for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
            final String className = stackTraceElement.getClassName();
            final String methodName = stackTraceElement.getMethodName();
            final int line = stackTraceElement.getLineNumber();
            writer.append("\tat ");
            writer.append(className);
            writer.append(".");
            writer.append(methodName);
            writer.append("(");
            writer.append(className);
            writer.append(".java:");
            writer.append(Integer.toString(line));
            writer.append(")");
            writer.append(System.lineSeparator());
        }
        final Throwable cause = throwable.getCause();
        if (cause != null) {
            print(writer, cause, false);
        }
    }

    public static void print(@NonNull final Appendable writer, @NonNull Throwable throwable) throws IOException {
        print(writer, throwable, true);
    }
}
