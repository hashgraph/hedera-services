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

package com.swirlds.platform.test.sync;

import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.gossip.shadowgraph.SyncUtils;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SyncTestUtilsTest {

    @Test
    void testUnknownNonAncient() {
        // the following graph is used for this test
        // GEN
        //   3  e7
        //      | \
        //   2  e5 e6
        //		|  |
        //   1  e3 e4
        //     	 \ | \
        //   0     e1 e2

        final Random random = RandomUtils.getRandomPrintSeed();

        final ShadowEvent e1 = EventFactory.makeShadow(random);
        final ShadowEvent e2 = EventFactory.makeShadow(random);
        final ShadowEvent e3 = EventFactory.makeShadow(random, null, e1);
        final ShadowEvent e4 = EventFactory.makeShadow(random, e1, e2);
        final ShadowEvent e5 = EventFactory.makeShadow(random, e3);
        final ShadowEvent e6 = EventFactory.makeShadow(random, e4);
        final ShadowEvent e7 = EventFactory.makeShadow(random, e5, e6);

        final Set<ShadowEvent> knownSet = new HashSet<>();
        knownSet.add(e5);
        knownSet.add(e3);
        knownSet.add(e1);

        final NonAncientEventWindow eventWindow = new NonAncientEventWindow(0, 1, 0, GENERATION_THRESHOLD);

        final Predicate<ShadowEvent> unknownNonAncient =
                SyncUtils.unknownNonAncient(knownSet, eventWindow, eventWindow, GENERATION_THRESHOLD);

        assertFalse(unknownNonAncient.test(e1), "e1 is both ancient and known, should be false");
        assertFalse(unknownNonAncient.test(e2), "e2 is ancient, should be false");
        assertFalse(unknownNonAncient.test(e3), "e3 is known, should be false");
        assertTrue(unknownNonAncient.test(e4), "e4 is unknown and non-ancient, should be true");
        assertFalse(unknownNonAncient.test(e5), "e5 is known, should be false");
        assertTrue(unknownNonAncient.test(e6), "e6 is unknown and non-ancient, should be true");
        assertTrue(unknownNonAncient.test(e7), "e7 is unknown and non-ancient, should be true");
    }
}
