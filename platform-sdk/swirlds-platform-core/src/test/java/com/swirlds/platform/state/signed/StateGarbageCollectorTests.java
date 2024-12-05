/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import com.swirlds.platform.wiring.components.StateAndRound;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StateGarbageCollectorTests {

    @BeforeEach
    void setUp() {
        MerkleDb.resetDefaultInstancePath();
    }

    @AfterEach
    void tearDown() {
        RandomSignedStateGenerator.releaseAllBuiltSignedStates();
    }

    @Test
    void standardBehaviorTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final StateGarbageCollector garbageCollector = new DefaultStateGarbageCollector(platformContext);

        final List<ReservedSignedState> unreleasedStates = new LinkedList<>();
        final List<SignedState> releasedStates = new LinkedList<>();

        for (int i = 0; i < 100; i++) {

            // Generate a few states.
            final int statesToCreate = random.nextInt(3);
            for (int j = 0; j < statesToCreate; j++) {
                MerkleDb.resetDefaultInstancePath();
                final SignedState signedState = new RandomSignedStateGenerator(random)
                        .setDeleteOnBackgroundThread(true)
                        .build();
                unreleasedStates.add(signedState.reserve("hold local copy of state"));
                garbageCollector.registerState(new StateAndRound(
                        signedState.reserve("send state to garbage collector"), mock(ConsensusRound.class)));
            }

            // Randomly release some of the states.
            final Iterator<ReservedSignedState> unreleasedStatesIterator = unreleasedStates.iterator();
            while (unreleasedStatesIterator.hasNext()) {
                if (random.nextDouble() < 0.1) {
                    final ReservedSignedState reservedState = unreleasedStatesIterator.next();
                    releasedStates.add(reservedState.get());
                    reservedState.close();
                    unreleasedStatesIterator.remove();
                }
            }

            // Send a time pulse to the garbage collector. This should cause it to delete released states.
            garbageCollector.heartbeat();

            // Make sure all of the deleted states are actually destroyed.
            for (final SignedState releasedState : releasedStates) {
                assertTrue(releasedState.getState().isDestroyed());
            }
            releasedStates.clear();

            // Make sure that none of the unreleased states are destroyed.
            for (final ReservedSignedState unreleasedState : unreleasedStates) {
                assertFalse(unreleasedState.get().getState().isDestroyed());
            }
        }
    }
}
