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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StringUtilsTest {

    @Test
    void leftPad() {
        final String start = "123";

        final String padded = StringUtils.leftPad(start, 5, '_');

        assertEquals("__123", padded);
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    void isBlank(final CharSequence input, final boolean expected) {
        assertEquals(expected, StringUtils.isBlank(input));
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    void isNotBlank(final CharSequence input, final boolean expected) {
        assertNotEquals(expected, StringUtils.isNotBlank(input));
    }

    // Provider method for the parameterized test
    private static Stream<Arguments> provideStringsForIsBlank() {
        return Stream.of(
                // The format is: input, expectedOutput
                Arguments.of(null, true),
                Arguments.of("", true),
                Arguments.of(" ", true),
                Arguments.of("\t", true),
                Arguments.of("\n", true),
                Arguments.of(" \t\n\r", true),
                Arguments.of("a", false),
                Arguments.of("word", false),
                Arguments.of("a ", false),
                Arguments.of(" a", false),
                Arguments.of(" word ", false),
                Arguments.of("\ta\t", false));
    }
}
