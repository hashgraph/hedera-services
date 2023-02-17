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

package com.hedera.node.app.service.mono.stats;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.mono.utils.LongSampleAccumulator;
import org.junit.jupiter.api.Test;

class LongSampleAccumulatorTest {

    @Test
    void addSamplesComplainsOfDifferentDenominatorsTest() {

        final var src = new LongSampleAccumulator("Foo", 100);
        final var sut = new LongSampleAccumulator("Bar", 250);

        assertThatIllegalArgumentException().isThrownBy(() -> {
            sut.addSamples(src);
        });
    }

    @Test
    void coalesceSamplesTest() {

        final var sut = new LongSampleAccumulator("Zed", 1);
        sut.addSample(1);
        sut.addSample(10);
        sut.addSample(100);

        final String expectedBefore = """
                Zed[#3, ∑111, 𝑚𝑎𝑥 100, ÷1]\
                """;
        assertEquals(expectedBefore, sut.toString());

        sut.coalesceSamples();

        final String expectedAfter = """
                Zed[#1, ∑111, 𝑚𝑎𝑥 111, ÷1]\
                """;
        assertEquals(expectedAfter, sut.toString());
    }
}
