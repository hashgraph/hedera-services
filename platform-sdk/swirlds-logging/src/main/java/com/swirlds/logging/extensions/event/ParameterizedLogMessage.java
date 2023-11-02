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

package com.swirlds.logging.extensions.event;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A log message that can be parameterized with arguments. The arguments are inserted into the message at the positions
 * of the {} placeholders.
 * <p>
 * The implementation is copied from slf4j for first tests. need to be replaced in future. SLF4J is using MIT license
 * (https://github.com/qos-ch/slf4j/blob/master/LICENSE.txt). Based on that we can use it in our project for now
 *
 * @param messagePattern the message pattern
 * @param args           the arguments
 * @see LogMessage
 */
public record ParameterizedLogMessage(@NonNull String messagePattern, @NonNull Object... args) implements LogMessage {

    static final char DELIM_START = '{';
    static final String DELIM_STR = "{}";
    private static final char ESCAPE_CHAR = '\\';

    @Override
    public String getMessage() {
        if (messagePattern == null) {
            return "";
        }

        if (args == null) {
            return messagePattern;
        }

        int i = 0;
        int j;
        // use string builder for better multicore performance
        final StringBuilder sbuf = new StringBuilder(messagePattern.length() + 50);

        int L;
        for (L = 0; L < args.length; L++) {

            j = messagePattern.indexOf(DELIM_STR, i);

            if (j == -1) {
                // no more variables
                if (i == 0) { // this is a simple string
                    return messagePattern;
                } else { // add the tail string which contains no variables and return
                    // the result.
                    sbuf.append(messagePattern, i, messagePattern.length());
                    return sbuf.toString();
                }
            } else {
                if (isEscapedDelimeter(messagePattern, j)) {
                    if (!isDoubleEscaped(messagePattern, j)) {
                        L--; // DELIM_START was escaped, thus should not be incremented
                        sbuf.append(messagePattern, i, j - 1);
                        sbuf.append(DELIM_START);
                        i = j + 1;
                    } else {
                        // The escape character preceding the delimiter start is
                        // itself escaped: "abc x:\\{}"
                        // we have to consume one backward slash
                        sbuf.append(messagePattern, i, j - 1);
                        deeplyAppendParameter(sbuf, args[L]);
                        i = j + 2;
                    }
                } else {
                    // normal case
                    sbuf.append(messagePattern, i, j);
                    deeplyAppendParameter(sbuf, args[L]);
                    i = j + 2;
                }
            }
        }
        // append the characters following the last {} pair.
        sbuf.append(messagePattern, i, messagePattern.length());
        return sbuf.toString();
    }

    private static boolean isEscapedDelimeter(@NonNull final String messagePattern, final int delimeterStartIndex) {

        if (delimeterStartIndex == 0) {
            return false;
        }
        final char potentialEscape = messagePattern.charAt(delimeterStartIndex - 1);
        if (potentialEscape == ESCAPE_CHAR) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean isDoubleEscaped(@NonNull final String messagePattern, final int delimeterStartIndex) {
        if (delimeterStartIndex >= 2 && messagePattern.charAt(delimeterStartIndex - 2) == ESCAPE_CHAR) {
            return true;
        } else {
            return false;
        }
    }

    private static void deeplyAppendParameter(@NonNull final StringBuilder sbuf, @Nullable final Object o) {
        if (o == null) {
            sbuf.append("null");
            return;
        }
        try {
            final String oAsString = o.toString();
            sbuf.append(oAsString);
        } catch (Throwable t) {
            sbuf.append("[FAILED toString()]");
        }
    }
}
