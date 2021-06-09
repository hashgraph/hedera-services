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

public final class ConverterUtils {
    public static void checkIfInputString(Object input)
            throws ArgumentConversionException {
        if (!(input instanceof String)) {
            throw new ArgumentConversionException(input + " is not a string");
        }
    }

    public static String[] getPartsIfValid(String inputString, int limit, String type)
            throws ArgumentConversionException {
        var parts = inputString.split("\\.", limit);
        if (limit != parts.length) {
            throw new ArgumentConversionException(inputString + " is not a " + limit + "-part " + type +" ID");
        }
        return parts;
    }
}
