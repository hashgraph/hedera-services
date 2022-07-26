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
package com.hedera.services.fees.calculation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final record CongestionMultipliers(int[] usagePercentTriggers, long[] multipliers) {
    public static CongestionMultipliers from(final String csv) {
        final List<Integer> triggers = new ArrayList<>();
        final List<Long> multipliers = new ArrayList<>();

        var sb = new StringBuilder();
        boolean nextTokenIsTrigger = true;
        for (int i = 0, n = csv.length(); i < n; i++) {
            final var here = csv.charAt(i);
            if (here == ',') {
                append(triggers, multipliers, sb.toString(), nextTokenIsTrigger);
                nextTokenIsTrigger = !nextTokenIsTrigger;
                sb = new StringBuilder();
            } else {
                sb.append(here);
            }
        }
        append(triggers, multipliers, sb.toString(), nextTokenIsTrigger);

        if (triggers.size() != multipliers.size()) {
            throw new IllegalArgumentException(
                    "Cannot use input of "
                            + triggers.size()
                            + "triggers and "
                            + multipliers.size()
                            + " multipliers!");
        }
        assertIncreasing(triggers, "triggers");
        assertIncreasing(multipliers, "multipliers");

        return new CongestionMultipliers(
                triggers.stream().mapToInt(v -> v).toArray(),
                multipliers.stream().mapToLong(l -> l).toArray());
    }

    private static void append(
            final List<Integer> triggers,
            final List<Long> multipliers,
            final String token,
            final boolean nextTokenIsTrigger) {
        if (nextTokenIsTrigger) {
            triggers.add(triggerFrom(token));
        } else {
            multipliers.add(multiplierFrom(token));
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

    private static Long multiplierFrom(final String s) {
        final var multiplier =
                s.endsWith("x")
                        ? Long.valueOf(sansDecimals(s.substring(0, s.length() - 1)))
                        : Long.valueOf(sansDecimals(s));
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Cannot use multiplier value " + multiplier + "!");
        }
        return multiplier;
    }

    private static Integer triggerFrom(final String s) {
        final var trigger = Integer.valueOf(sansDecimals(s));
        if (trigger <= 0 || trigger > 100) {
            throw new IllegalArgumentException("Cannot use trigger value " + trigger + "!");
        }
        return trigger;
    }

    private static String sansDecimals(final String s) {
        final var i = s.indexOf(".");
        if (-1 == i) {
            return s;
        }
        return s.substring(0, i);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(usagePercentTriggers), Arrays.hashCode(multipliers));
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || !o.getClass().equals(CongestionMultipliers.class)) {
            return false;
        }
        final var that = (CongestionMultipliers) o;
        return Arrays.equals(this.multipliers, that.multipliers)
                && Arrays.equals(this.usagePercentTriggers, that.usagePercentTriggers);
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder("CongestionMultipliers{");
        for (int i = 0; i < multipliers.length; i++) {
            sb.append(multipliers[i])
                    .append("x @ >=")
                    .append(usagePercentTriggers[i])
                    .append("%")
                    .append((i == multipliers.length - 1) ? "" : ", ");
        }
        return sb.append("}").toString();
    }
}
