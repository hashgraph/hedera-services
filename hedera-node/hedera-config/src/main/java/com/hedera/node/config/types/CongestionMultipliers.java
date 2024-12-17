/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.types;

import java.util.Arrays;
import java.util.Objects;

public record CongestionMultipliers(int[] usagePercentTriggers, long[] multipliers) {
    public static CongestionMultipliers from(final String csv) {
        final var triggeredMultipliers = TriggeredValuesParser.parseFrom(csv, CongestionMultipliers::multiplierFrom);
        return new CongestionMultipliers(
                triggeredMultipliers.triggers().stream().mapToInt(v -> v).toArray(),
                triggeredMultipliers.values().stream().mapToLong(l -> l).toArray());
    }

    private static Long multiplierFrom(final String s) {
        final var multiplier = s.endsWith("x")
                ? Long.valueOf(TriggeredValuesParser.sansDecimals(s.substring(0, s.length() - 1)))
                : Long.valueOf(TriggeredValuesParser.sansDecimals(s));
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Cannot use multiplier value " + multiplier + "!");
        }
        return multiplier;
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
