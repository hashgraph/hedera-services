/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees.calculation;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.service.mono.fees.calculation.utils.TriggeredValuesParser;
import java.util.Arrays;

public record UtilizationScaleFactors(int[] usagePercentTriggers, ScaleFactor[] scaleFactors) {
    public static UtilizationScaleFactors from(final String csv) {
        final var triggeredFactors = TriggeredValuesParser.parseFrom(csv, ScaleFactor::from);
        return new UtilizationScaleFactors(
                triggeredFactors.triggers().stream().mapToInt(v -> v).toArray(),
                triggeredFactors.values().stream().toArray(ScaleFactor[]::new));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UtilizationScaleFactors that = (UtilizationScaleFactors) o;
        return Arrays.equals(usagePercentTriggers, that.usagePercentTriggers)
                && Arrays.equals(scaleFactors, that.scaleFactors);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(usagePercentTriggers);
        result = 31 * result + Arrays.hashCode(scaleFactors);
        return result;
    }

    @Override
    public String toString() {
        return "UtilizationScaleFactors{"
                + "usagePercentTriggers="
                + Arrays.toString(usagePercentTriggers)
                + ", scaleFactors="
                + Arrays.toString(scaleFactors)
                + '}';
    }
}
