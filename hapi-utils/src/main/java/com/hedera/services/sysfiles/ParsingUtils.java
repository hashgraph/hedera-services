/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sysfiles;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ParsingUtils {
    public static <T, A, B> T fromTwoPartDelimited(
            final String literal,
            final String delimiter,
            final BiConsumer<A, B> validator,
            final Function<String, A> aParser,
            final Function<String, B> bParser,
            final BiFunction<A, B, T> finisher) {
        final var splitIndex = literal.indexOf(delimiter);
        if (splitIndex == -1) {
            throw new IllegalArgumentException(
                    "Missing '" + delimiter + "' in literal '" + literal + "'");
        }
        final var delimLen = delimiter.length();
        final var a = aParser.apply(literal.substring(0, splitIndex));
        final var b = bParser.apply(literal.substring(splitIndex + delimLen));
        validator.accept(a, b);
        return finisher.apply(a, b);
    }

    private ParsingUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
