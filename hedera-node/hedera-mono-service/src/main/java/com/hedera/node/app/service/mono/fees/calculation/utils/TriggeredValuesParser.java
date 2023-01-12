/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees.calculation.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TriggeredValuesParser {
    private TriggeredValuesParser() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public record TriggeredValues<T>(List<Integer> triggers, List<T> values) {}

    public static <T extends Comparable<T>> TriggeredValues<T> parseFrom(
            final String csv, final Function<String, T> valueParser) {
        final List<Integer> triggers = new ArrayList<>();
        final List<T> values = new ArrayList<>();

        var sb = new StringBuilder();
        boolean nextTokenIsTrigger = true;
        for (int i = 0, n = csv.length(); i < n; i++) {
            final var here = csv.charAt(i);
            if (here == ',') {
                append(triggers, values, valueParser, sb.toString(), nextTokenIsTrigger);
                nextTokenIsTrigger = !nextTokenIsTrigger;
                sb = new StringBuilder();
            } else {
                sb.append(here);
            }
        }
        append(triggers, values, valueParser, sb.toString(), nextTokenIsTrigger);

        if (triggers.size() != values.size()) {
            throw new IllegalArgumentException(
                    "Cannot use input of "
                            + triggers.size()
                            + "triggers and "
                            + values.size()
                            + " values!");
        }
        assertIncreasing(triggers, "triggers");
        assertIncreasing(values, "values");
        return new TriggeredValues<>(triggers, values);
    }

    private static <T> void append(
            final List<Integer> triggers,
            final List<T> values,
            final Function<String, T> valueParser,
            final String token,
            final boolean nextTokenIsTrigger) {
        if (nextTokenIsTrigger) {
            triggers.add(triggerFrom(token));
        } else {
            values.add(valueParser.apply(token));
        }
    }

    private static <T extends Comparable<T>> void assertIncreasing(
            final List<T> vals, final String desc) {
        for (int i = 0, n = vals.size() - 1; i < n; i++) {
            final var a = vals.get(i);
            final var b = vals.get(i + 1);
            if (a.compareTo(b) >= 0) {
                throw new IllegalArgumentException(
                        "Given " + desc + " are not strictly increasing!");
            }
        }
    }

    private static Integer triggerFrom(final String s) {
        final var trigger = Integer.valueOf(sansDecimals(s));
        if (trigger < 0 || trigger > 100) {
            throw new IllegalArgumentException("Cannot use trigger value " + trigger + "!");
        }
        return trigger;
    }

    public static String sansDecimals(final String s) {
        final var i = s.indexOf(".");
        if (-1 == i) {
            return s;
        }
        return s.substring(0, i);
    }
}
