/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.service;

import static com.swirlds.platform.test.PlatformStateUtils.randomPlatformState;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.test.fixtures.state.TestPlatformStateFacade;
import java.time.Instant;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlatformStateFacadeTest {

    public static final Function<SemanticVersion, SoftwareVersion> VERSION_FACTORY =
            v -> new BasicSoftwareVersion(v.major());
    private static TestPlatformStateFacade platformStateFacade;
    private static PlatformMerkleStateRoot state;
    private static PlatformMerkleStateRoot emptyState;
    private static PlatformStateModifier platformStateModifier;

    @BeforeAll
    static void beforeAll() {
        state = new PlatformMerkleStateRoot(VERSION_FACTORY);
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(state);
        emptyState = new PlatformMerkleStateRoot(VERSION_FACTORY);
        platformStateFacade = new TestPlatformStateFacade(VERSION_FACTORY);
        platformStateModifier = randomPlatformState(state, platformStateFacade);
    }

    @Test
    void isInFreezePeriodTest() {

        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t2.plusSeconds(1);
        final Instant t4 = t3.plusSeconds(1);

        // No freeze time set
        assertFalse(PlatformStateFacade.isInFreezePeriod(t1, null, null));

        // No freeze time set, previous freeze time set
        assertFalse(PlatformStateFacade.isInFreezePeriod(t2, null, t1));

        // Freeze time is in the future, never frozen before
        assertFalse(PlatformStateFacade.isInFreezePeriod(t2, t3, null));

        // Freeze time is in the future, frozen before
        assertFalse(PlatformStateFacade.isInFreezePeriod(t2, t3, t1));

        // Freeze time is in the past, never frozen before
        assertTrue(PlatformStateFacade.isInFreezePeriod(t2, t1, null));

        // Freeze time is in the past, frozen before at an earlier time
        assertTrue(PlatformStateFacade.isInFreezePeriod(t3, t2, t1));

        // Freeze time in the past, already froze at that exact time
        assertFalse(PlatformStateFacade.isInFreezePeriod(t3, t2, t2));
    }

    @Test
    public void testCreationSoftwareVersionOf() {
        assertEquals(
                platformStateModifier.getCreationSoftwareVersion().getPbjSemanticVersion(),
                platformStateFacade.creationSoftwareVersionOf(state).getPbjSemanticVersion());
    }

    @Test
    public void testCreationSoftwareVersionOf_null() {
        assertNull(platformStateFacade.creationSoftwareVersionOf(emptyState));
    }

    @Test
    public void testRoundOf() {
        assertEquals(platformStateModifier.getRound(), platformStateFacade.roundOf(state));
    }

    @Test
    public void platformStateOf() {
        final PlatformMerkleStateRoot noPlatformState = new PlatformMerkleStateRoot(VERSION_FACTORY);
        noPlatformState.getReadableStates(PlatformStateService.NAME);
        platformStateFacade.platformStateOf(noPlatformState);
    }
}
