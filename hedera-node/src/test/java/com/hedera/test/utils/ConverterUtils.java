package com.hedera.test.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.junit.jupiter.params.converter.ArgumentConversionException;

/**
 * Contians various common checks and methods used by the Converter classes
 * @author abhishekpandey
 * */
public final class ConverterUtils {
    /**
     * Checks if the input to the converter is of type {@link String} else throws an {@link ArgumentConversionException}
     * @param input
     *              the input to the converter
     * @throws ArgumentConversionException
     * */
    public static void checkIfInputString(Object input)
            throws ArgumentConversionException {
        if (!(input instanceof String)) {
            throw new ArgumentConversionException(input + " is not a string");
        }
    }

    /**
     * Returns an array of string from the input after performing split operation else throws an
     * {@link ArgumentConversionException}
     * @param inputString
     *              the input to the converter
     * @param limit
     *              the limit to be applied for the split operation
     * @param type
     *             the string defining the type of input
     * @throws ArgumentConversionException
     * */
    public static String[] getPartsIfValid(String inputString, int limit, String type)
            throws ArgumentConversionException {
        var parts = inputString.split("\\.", limit);
        if (limit != parts.length) {
            throw new ArgumentConversionException(inputString + " is not a " + limit + "-part " + type +" ID");
        }
        return parts;
    }
}
