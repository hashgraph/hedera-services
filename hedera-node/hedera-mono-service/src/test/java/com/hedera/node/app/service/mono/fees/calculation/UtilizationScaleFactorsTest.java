/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UtilizationScaleFactorsTest {
    @CsvSource({"50|3:2|69|7:3|96|8:1,50|69|96,3:2|7:3|8:1"})
    @ParameterizedTest
    void readsExpectedValues(
            final String pipedInput, final String pipedTriggers, final String pipedScales) {
        final var csv = pipedInput.replace("|", ",");
        final var triggers =
                Arrays.stream(pipedTriggers.split("[|]")).mapToInt(Integer::parseInt).toArray();
        final var scaleFactors =
                Arrays.stream(pipedScales.split("[|]"))
                        .map(ScaleFactor::from)
                        .toArray(ScaleFactor[]::new);

        final var parsed = UtilizationScaleFactors.from(csv);
        assertArrayEquals(triggers, parsed.usagePercentTriggers());
        assertArrayEquals(scaleFactors, parsed.scaleFactors());
    }

    @Test
    void objectContractsMet() {
        final var propA = "90,10:1,95,25:1,99,100:1";
        final var propB = "90,10:1,95,25:1,99,1000:1";
        final var propC = "90,10:1,94,25:1,99,100:1";

        final var a = UtilizationScaleFactors.from(propA);

        assertEquals(a, UtilizationScaleFactors.from(propA));
        assertNotEquals(null, a);
        assertNotEquals(new Object(), a);
        assertNotEquals(a, UtilizationScaleFactors.from(propB));
        assertNotEquals(a, UtilizationScaleFactors.from(propC));

        assertEquals(a.hashCode(), a.hashCode());
        assertEquals(a.hashCode(), UtilizationScaleFactors.from(propA).hashCode());
        assertNotEquals(new Object().hashCode(), a.hashCode());
        assertNotEquals(a.hashCode(), UtilizationScaleFactors.from(propB).hashCode());
        assertNotEquals(a.hashCode(), UtilizationScaleFactors.from(propC).hashCode());
    }

    @Test
    void toStringWorks() {
        final var propA = "90,10:1,95,25:1,99,100:1";
        final var desired =
                "UtilizationScaleFactors{usagePercentTriggers=[90, 95, 99],"
                        + " scaleFactors=[ScaleFactor{scale=10:1}, ScaleFactor{scale=25:1},"
                        + " ScaleFactor{scale=100:1}]}";

        assertEquals(desired, UtilizationScaleFactors.from(propA).toString());
    }
}
