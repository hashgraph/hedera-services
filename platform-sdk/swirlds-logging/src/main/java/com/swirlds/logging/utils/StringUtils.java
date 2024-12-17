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

package com.swirlds.logging.utils;

/**
 * String utilities class
 */
public class StringUtils {
    private StringUtils() {}

    /**
     * Creates a String of digits of the number and pads to the left with 0. Examples:
     * <ul>
     * <li>{@code toPaddedDigitsString(1, 1)} --> 1</li>
     * <li>{@code toPaddedDigitsString(1, 2)} --> 01</li>
     * <li>{@code toPaddedDigitsString(12, 1)} --> 2</li>
     * <li>{@code toPaddedDigitsString(12, 2)} --> 12</li>
     * <li>{@code toPaddedDigitsString(12, 3)} --> 012</li>
     * <li>{@code toPaddedDigitsString(123, 3)} --> 123</li>
     * <li>{@code toPaddedDigitsString(758, 4)} --> 0758</li>
     * </ul>
     *
     * @param number        The number to append in reverse order.
     * @param desiredLength The maximum length of the number to append.
     */
    public static String toPaddedDigitsString(final int number, final int desiredLength) {
        StringBuilder buffer = new StringBuilder();
        int actualLength = 0;
        int num = number;
        while ((num > 0) && actualLength < desiredLength) {
            int digit = num % 10;
            buffer.append(digit);
            num /= 10;
            actualLength++;
        }
        while (desiredLength > actualLength) {
            buffer.append(0);
            actualLength++;
        }
        return buffer.reverse().toString();
    }
}
