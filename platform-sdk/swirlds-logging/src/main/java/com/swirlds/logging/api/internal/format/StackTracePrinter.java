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

package com.swirlds.logging.api.internal.format;

import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is responsible for printing stack traces of exceptions to a specified Appendable object.
 * It includes functionality to avoid printing circular references and to handle cases where
 * certain parts of the trace have already been printed. It also includes emergency logging
 * for null references.
 */
public class StackTracePrinter {

    /**
     * Emergency logger for handling null references.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * Prints the stack trace of a throwable to a provided Appendable writer.
     * Avoids printing circular references and handles already printed traces.
     *
     * @param writer          The Appendable object to write the stack trace to.
     * @param throwable       The Throwable object whose stack trace is to be printed.
     * @param alreadyPrinted  A Set of Throwable objects that have already been printed.
     * @param enclosingTrace  An array of StackTraceElement representing the current call stack.
     * @throws IOException    If an I/O error occurs.
     */
    private static void print(
            @NonNull final Appendable writer,
            @NonNull Throwable throwable,
            @NonNull final Set<Throwable> alreadyPrinted,
            @NonNull final StackTraceElement[] enclosingTrace)
            throws IOException {
        // Method implementation
    }

    /**
     * Overloaded print method to print the stack trace of a throwable.
     * Initializes a new HashSet and empty StackTraceElement array to facilitate the print process.
     *
     * @param writer    The Appendable object to write the stack trace to.
     * @param throwable The Throwable object whose stack trace is to be printed.
     * @throws IOException If an I/O error occurs.
     */
    public static void print(@NonNull final Appendable writer, @NonNull Throwable throwable) throws IOException {
        print(writer, throwable, new HashSet<>(), new StackTraceElement[0]);
    }
}
