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

package com.swirlds.base.utility;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * String utility methods
 */
public class StringUtils {

    /**
     * An empty String {@code ""}.
     */
    public static final String EMPTY = "";

    private StringUtils() {}

    /**
     * Checks if a CharSequence is empty ("") or null.
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is empty or null
     */
    public static boolean isBlank(@Nullable final CharSequence cs) {
        final int strLen = cs == null ? 0 : cs.length();

        if (strLen == 0) {
            return true;
        }

        return cs.chars().allMatch(Character::isWhitespace);
    }

    /**
     * Checks if a CharSequence is not empty ("") and not null.
     *
     * @param cs the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is not empty and not null
     */
    public static boolean isNotBlank(@Nullable final CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * Aligns the given string to the left by padding it with the given character.
     *
     * @param str string to pad
     * @param size size of the resulting string
     * @param padChar character to pad with
     *
     * @return the padded string
     */
    public static String leftPad(final String str, final int size, final char padChar) {
        if (str == null || size <= str.length()) {
            return str;
        }

        return String.valueOf(padChar).repeat(size - str.length()) + str;
    }
}
