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

package com.hedera.services.cli.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Formatters {
    public static <T> Function<List<T>, String> getListFormatter(
            @NonNull final Function<T, String> formatter, @NonNull final String subfieldSeparator) {
        return lt -> {
            if (!lt.isEmpty()) {
                final var sb = new StringBuilder();
                for (@NonNull final var e : lt) {
                    final var v = formatter.apply(e);
                    sb.append(v);
                    sb.append(subfieldSeparator);
                }
                // Remove last subfield separator
                if (sb.length() >= subfieldSeparator.length()) sb.setLength(sb.length() - subfieldSeparator.length());
                return sb.toString();
            } else return "";
        };
    }

    public static <T> Function<Optional<T>, String> getOptionalFormatter(@NonNull final Function<T, String> formatter) {
        return ot -> ot.isPresent() ? formatter.apply(ot.get()) : "";
    }

    public static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }
}
