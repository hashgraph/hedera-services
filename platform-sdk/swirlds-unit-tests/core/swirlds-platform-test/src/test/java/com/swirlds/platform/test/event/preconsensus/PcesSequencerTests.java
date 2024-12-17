/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.preconsensus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.preconsensus.DefaultPcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PcesSequencer Tests")
class PcesSequencerTests {

    @Test
    @DisplayName("Standard Behavior Test")
    void standardBehaviorTest() {
        final PcesSequencer sequencer = new DefaultPcesSequencer();

        long prev = -1;
        for (int i = 0; i < 1000; i++) {
            final ValueReference<Long> seq = new ValueReference<>(PlatformEvent.NO_STREAM_SEQUENCE_NUMBER);
            final PlatformEvent event = mock(PlatformEvent.class);
            doAnswer(invocation -> {
                        seq.setValue(invocation.getArgument(0));
                        return null;
                    })
                    .when(event)
                    .setStreamSequenceNumber(anyLong());

            sequencer.assignStreamSequenceNumber(event);

            assertTrue(seq.getValue() > prev);
            prev = seq.getValue();
        }
    }

    @Test
    @DisplayName("Set Value Twice Test")
    void setValueTwiceTest() {
        final PcesSequencer sequencer = new DefaultPcesSequencer();

        final PlatformEvent event = new TestingEventBuilder(RandomUtils.getRandom()).build();

        sequencer.assignStreamSequenceNumber(event);
        assertThrows(IllegalStateException.class, () -> sequencer.assignStreamSequenceNumber(event));
    }
}
