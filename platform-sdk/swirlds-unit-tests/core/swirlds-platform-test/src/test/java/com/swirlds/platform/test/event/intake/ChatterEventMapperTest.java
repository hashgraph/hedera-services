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

package com.swirlds.platform.test.event.intake;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.intake.ChatterEventMapper;
import com.swirlds.platform.test.event.GossipEventBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatterEventMapperTest {
    @Test
    @DisplayName("basic functionality")
    void test() {
        final long creator1 = 1;
        final long creator2 = 2;

        final ChatterEventMapper mapper = new ChatterEventMapper();
        Assertions.assertNull(mapper.getMostRecentEvent(creator1), "initially it should be empty");
        Assertions.assertNull(mapper.getMostRecentEvent(creator2), "initially it should be empty");

        final GossipEvent cr1gen1 = GossipEventBuilder.builder()
                .setCreatorId(creator1)
                .setGeneration(1)
                .buildEvent();
        mapper.mapEvent(cr1gen1);
        Assertions.assertEquals(
                cr1gen1, mapper.getMostRecentEvent(creator1), "the event just added should be the most recent");
        Assertions.assertNull(mapper.getMostRecentEvent(creator2), "creator1 should not affect creator2");

        final GossipEvent cr1gen10 = GossipEventBuilder.builder()
                .setCreatorId(creator1)
                .setGeneration(10)
                .buildEvent();
        mapper.mapEvent(cr1gen10);
        Assertions.assertEquals(
                cr1gen10, mapper.getMostRecentEvent(creator1), "the event just added should be the most recent");
        Assertions.assertNull(mapper.getMostRecentEvent(creator2), "creator1 should not affect creator2");

        final GossipEvent cr1gen5 = GossipEventBuilder.builder()
                .setCreatorId(creator1)
                .setGeneration(5)
                .buildEvent();
        mapper.mapEvent(cr1gen5);
        Assertions.assertEquals(
                cr1gen10, mapper.getMostRecentEvent(creator1), "the event just added should NOT be the most recent");
        Assertions.assertNull(mapper.getMostRecentEvent(creator2), "creator1 should not affect creator2");

        mapper.clear();
        Assertions.assertNull(mapper.getMostRecentEvent(creator1), "after a clear, there should be no events");
        Assertions.assertNull(mapper.getMostRecentEvent(creator2), "after a clear, there should be no events");
    }
}
