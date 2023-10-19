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

package com.swirlds.platform.event.validation;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;
import static com.swirlds.platform.event.validation.EventValidationChecks.areParentsValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isGenerationValid;
import static com.swirlds.platform.event.validation.EventValidationChecks.isValidTimeCreated;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link EventValidationChecks} class
 */
class EventValidationChecksTests {
    private RateLimitedLogger logger;
    private LongAccumulator metricAccumulator;
    private Random random;

    @BeforeEach
    void setup() {
        logger = mock(RateLimitedLogger.class);
        metricAccumulator = mock(LongAccumulator.class);
        random = getRandomPrintSeed();
    }

    /**
     * Generate a mock hashed data object with the given parameters.
     *
     * @param selfParentHash  the self parent hash
     * @param otherParentHash the other parent hash
     * @param selfParentGen   the self parent generation
     * @param otherParentGen  the other parent generation
     * @return a mock hashed data object with the given parameters
     */
    private BaseEventHashedData generateMockHashedData(
            @Nullable final Hash selfParentHash,
            @Nullable final Hash otherParentHash,
            final long selfParentGen,
            final long otherParentGen) {
        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSelfParentHash()).thenReturn(selfParentHash);
        when(hashedData.getOtherParentHash()).thenReturn(otherParentHash);
        when(hashedData.getSelfParentGen()).thenReturn(selfParentGen);
        when(hashedData.getOtherParentGen()).thenReturn(otherParentGen);

        return hashedData;
    }

    @Test
    @DisplayName("Test isGenerationValid")
    void testGenerationValid() {
        final long minGenNonAncient = 11;

        final GossipEvent ancientEvent = mock(GossipEvent.class);
        when(ancientEvent.getGeneration()).thenReturn(5L);
        assertFalse(isGenerationValid(ancientEvent, minGenNonAncient, logger));

        final GossipEvent almostAncientEvent = mock(GossipEvent.class);
        when(almostAncientEvent.getGeneration()).thenReturn(11L);
        assertTrue(isGenerationValid(almostAncientEvent, minGenNonAncient, logger));

        final GossipEvent nonAncientEvent = mock(GossipEvent.class);
        when(nonAncientEvent.getGeneration()).thenReturn(15L);
        assertTrue(isGenerationValid(nonAncientEvent, minGenNonAncient, logger));
    }

    @Test
    @DisplayName("Test isValidTimeCreated")
    void testIsValidTimeCreated() {
        final Instant now = new FakeTime().now();

        final EventImpl selfParent = mock(EventImpl.class);
        when(selfParent.getTimeCreated()).thenReturn(now);

        final EventImpl childAfterParent = mock(EventImpl.class);
        when(childAfterParent.getTimeCreated()).thenReturn(now.plusMillis(1111));
        when(childAfterParent.getSelfParent()).thenReturn(selfParent);
        assertTrue(isValidTimeCreated(childAfterParent, logger, metricAccumulator));

        final EventImpl childSameTimeAsParent = mock(EventImpl.class);
        when(childSameTimeAsParent.getTimeCreated()).thenReturn(now);
        when(childSameTimeAsParent.getSelfParent()).thenReturn(selfParent);
        assertFalse(isValidTimeCreated(childSameTimeAsParent, logger, metricAccumulator));

        final EventImpl childBeforeParent = mock(EventImpl.class);
        when(childBeforeParent.getTimeCreated()).thenReturn(now.minusMillis(1111));
        when(childBeforeParent.getSelfParent()).thenReturn(selfParent);
        assertFalse(isValidTimeCreated(childBeforeParent, logger, metricAccumulator));

        final EventImpl childWithoutSelfParent = mock(EventImpl.class);
        when(childWithoutSelfParent.getTimeCreated()).thenReturn(now);
        when(childWithoutSelfParent.getSelfParent()).thenReturn(null);
        assertTrue(isValidTimeCreated(childWithoutSelfParent, logger, metricAccumulator));
    }

    @Test
    @DisplayName("Test areParentsValid with invalid self parent")
    void invalidParents() {
        final EventImpl child = mock(EventImpl.class);

        // this event is normal, except its self parent hash is null
        when(child.getHashedData()).thenReturn(generateMockHashedData(null, randomHash(random), 10L, 11L));
        assertFalse(areParentsValid(child, false, logger, metricAccumulator));

        // this event is normal, except its self parent generation is invalid
        when(child.getHashedData())
                .thenReturn(generateMockHashedData(randomHash(random), randomHash(random), -1L, 11L));
        assertFalse(areParentsValid(child, false, logger, metricAccumulator));

        // this event is normal, except its other parent hash is null
        when(child.getHashedData()).thenReturn(generateMockHashedData(randomHash(random), null, 10L, 11L));
        assertFalse(areParentsValid(child, false, logger, metricAccumulator));

        // this event is normal, except its other parent generation is invalid
        when(child.getHashedData())
                .thenReturn(generateMockHashedData(randomHash(random), randomHash(random), 11L, -1L));
        assertFalse(areParentsValid(child, false, logger, metricAccumulator));
    }

    @Test
    @DisplayName("Test areParentsValid with no parents")
    void genesisEvent() {
        final EventImpl child = mock(EventImpl.class);

        // this simulates a genesis event
        when(child.getHashedData()).thenReturn(generateMockHashedData(null, null, -1L, -1L));
        assertTrue(areParentsValid(child, false, logger, metricAccumulator));
    }

    @Test
    @DisplayName("Test areParentsValid with parents from the first valid generation")
    void genesisParents() {
        final EventImpl child = mock(EventImpl.class);

        // this simulates an event whose parents are genesis events
        when(child.getHashedData())
                .thenReturn(generateMockHashedData(
                        randomHash(random), randomHash(random), FIRST_GENERATION, FIRST_GENERATION));
        assertTrue(areParentsValid(child, false, logger, metricAccumulator));
    }

    @Test
    @DisplayName("Test areParentsValid with an event where self and other parent are the same")
    void singleParent() {
        final EventImpl child = mock(EventImpl.class);

        final Hash hash = randomHash(random);
        final long generation = 10L;
        when(child.getHashedData()).thenReturn(generateMockHashedData(hash, hash, generation, generation));

        assertTrue(
                areParentsValid(child, true, logger, metricAccumulator),
                "A shared parent should be permitted in a single node network");
        assertFalse(
                areParentsValid(child, false, logger, metricAccumulator),
                "A shared parent should not be permitted in a multi-node network");
    }

    @Test
    @DisplayName("Test areParentsValid with valid parents")
    void validParents() {
        final EventImpl child = mock(EventImpl.class);
        when(child.getHashedData())
                .thenReturn(generateMockHashedData(randomHash(random), randomHash(random), 10L, 11L));

        assertTrue(areParentsValid(child, false, logger, metricAccumulator));
    }
}
