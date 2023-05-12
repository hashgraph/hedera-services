/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging;

/**
 * A collection of utilities to assist with logging.
 */
public final class LoggingUtils {

    private LoggingUtils() {}

    /**
     * Choose between a singular and plural word depending on a count.
     *
     * @param count
     * 		the count of objects
     * @param singular
     * 		the singular version of the word, e.g. "goose"
     * @param plural
     * 		the plural version of the word, e.g. "geese"
     * @return the correct form of the word given the count
     */
    public static String plural(final long count, final String singular, final String plural) {
        if (count == 1) {
            return singular;
        }
        return plural;
    }

    /**
     * Choose between a singular and plural word depending on the count.
     * For words that can be made plural by adding an "s" to the end.
     *
     * @param count
     * 		the count of objects
     * @param singular
     * 		the singular version of the word, e.g. "dog"
     * @return the correct form of the word given the count
     */
    public static String plural(final long count, final String singular) {
        if (count == 1) {
            return singular;
        }
        return singular + "s";
    }
}
