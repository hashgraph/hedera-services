/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.utils;

import org.junit.jupiter.params.converter.ArgumentConversionException;

/** Contains various common checks and methods used by the Converter classes */
public final class ConverterUtils {
    /**
     * Returns the input as a {@link String} if it is string type else throws an {@link
     * ArgumentConversionException}
     *
     * @param input the input to the converter
     * @throws ArgumentConversionException thrown when the input is not of type {@link String}
     * @return input casted as a string
     */
    static String toStringInstance(final Object input) throws ArgumentConversionException {
        if (!(input instanceof String)) {
            throw new ArgumentConversionException(input + " is not a string");
        }
        return (String) input;
    }

    /**
     * Returns an array of string from the input after performing split operation else throws an
     * {@link ArgumentConversionException}
     *
     * @param inputString the input to the converter
     * @param exactNumberOfParts exact number of parts from the string to expect, non positive
     *     number means it will be applied as many times as possible
     * @param delimiter the regex to be applied for the split operation
     * @param type the string defining the type of input
     * @throws ArgumentConversionException thrown when the numberOfParts don't match after the split
     *     operation
     */
    static String[] getPartsIfValid(
            final String inputString,
            final int exactNumberOfParts,
            final String delimiter,
            final String type)
            throws ArgumentConversionException {
        final var parts = inputString.split(delimiter, exactNumberOfParts);
        if (exactNumberOfParts != parts.length && exactNumberOfParts > 0) {
            throw new ArgumentConversionException(
                    inputString + " is not a " + exactNumberOfParts + "-part " + type + " ID");
        }
        return parts;
    }
}
