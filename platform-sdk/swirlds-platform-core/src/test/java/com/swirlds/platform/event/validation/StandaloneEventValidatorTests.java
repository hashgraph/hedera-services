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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StandaloneEventValidator}
 */
class StandaloneEventValidatorTests {
    private AtomicInteger consumedEventCount;
    private AtomicLong exitedIntakePipelineCount;
    private Random random;

    private Instant defaultSelfParentTime;
    private Instant defaultChildTime;

    private final long defaultOtherParentGen = 11L;
    private final long defaultSelfParentGen = 10L;

    private StandaloneEventValidator singleNodeNetworkValidator;
    private StandaloneEventValidator multiNodeNetworkValidator;

    @BeforeEach
    void setup() {
        final FakeTime time = new FakeTime();
        defaultSelfParentTime = time.now();
        defaultChildTime = defaultSelfParentTime.plusMillis(1000);

        exitedIntakePipelineCount = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        consumedEventCount = new AtomicInteger(0);
        final Consumer<GossipEvent> eventConsumer = event -> {
            consumedEventCount.incrementAndGet();
        };

        singleNodeNetworkValidator =
                new StandaloneEventValidator(platformContext, time, true, eventConsumer, intakeEventCounter);
        multiNodeNetworkValidator =
                new StandaloneEventValidator(platformContext, time, false, eventConsumer, intakeEventCounter);
    }

    /**
     * Generate a mock event with the given parameters
     *
     * @param selfParentHash    the self parent hash
     * @param otherParentHash   the other parent hash
     * @param selfParentGen     the self parent generation
     * @param otherParentGen    the other parent generation
     * @param timeChildCreated  the time the child was created
     * @param timeParentCreated the time the parent was created
     * @return the mock event
     */
    private EventImpl generateEvent(
            @Nullable final Hash selfParentHash,
            @Nullable final Hash otherParentHash,
            final long selfParentGen,
            final long otherParentGen,
            @NonNull final Instant timeChildCreated,
            @Nullable final Instant timeParentCreated) {

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSelfParentHash()).thenReturn(selfParentHash);
        when(hashedData.getOtherParentHash()).thenReturn(otherParentHash);
        when(hashedData.getSelfParentGen()).thenReturn(selfParentGen);
        when(hashedData.getOtherParentGen()).thenReturn(otherParentGen);

        final GossipEvent baseEvent = mock(GossipEvent.class);

        final EventImpl selfParent = mock(EventImpl.class);
        when(selfParent.getTimeCreated()).thenReturn(timeParentCreated);

        final EventImpl event = mock(EventImpl.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getTimeCreated()).thenReturn(timeChildCreated);
        when(event.getSelfParent()).thenReturn(selfParent);
        when(event.getBaseEvent()).thenReturn(baseEvent);
        when(event.getGeneration()).thenReturn(selfParentGen + 1L);

        return event;
    }

    /**
     * Assert either a passing or failing validation for a given event
     *
     * @param useSingleNodeNetworkValidator whether to use the single node network validator or the multi node network
     * @param event                         the event to validate
     * @param expectPass                    whether the event is expected to pass validation
     */
    private void assertValidationResult(
            final boolean useSingleNodeNetworkValidator, @NonNull final EventImpl event, final boolean expectPass) {
        int expectedConsumedEventCount = consumedEventCount.get();
        long expectedExitedIntakePipelineCount = exitedIntakePipelineCount.get();

        if (expectPass) {
            expectedConsumedEventCount++;
        } else {
            expectedExitedIntakePipelineCount++;
        }

        final StandaloneEventValidator validator =
                useSingleNodeNetworkValidator ? singleNodeNetworkValidator : multiNodeNetworkValidator;
        validator.handleEvent(event);

        assertEquals(expectedConsumedEventCount, consumedEventCount.get());
        assertEquals(expectedExitedIntakePipelineCount, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Normal event should pass validation")
    void normalOperation() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                defaultSelfParentGen,
                defaultOtherParentGen,
                defaultChildTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, true);
        assertValidationResult(true, child, true);
    }

    @Test
    @DisplayName("Child created at the same time as parent")
    void childCreatedAtSameTimeAsParent() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                defaultSelfParentGen,
                defaultOtherParentGen,
                defaultSelfParentTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, false);
    }

    @Test
    @DisplayName("Child created before parent")
    void childCreatedBeforeParent() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                defaultSelfParentGen,
                defaultOtherParentGen,
                defaultSelfParentTime.minusMillis(1000),
                defaultSelfParentTime);

        assertValidationResult(false, child, false);
    }

    @Test
    @DisplayName("Child has no self parent")
    void childHasNoSelfParent() {
        final EventImpl child =
                generateEvent(null, randomHash(random), -1, defaultOtherParentGen, defaultChildTime, null);
        when(child.getSelfParent()).thenReturn(null);

        assertValidationResult(false, child, true);
    }

    @Test
    @DisplayName("Child has no other parent")
    void childHasNoOtherParent() {
        final EventImpl child = generateEvent(
                randomHash(random), null, defaultSelfParentGen, -1L, defaultChildTime, defaultSelfParentTime);
        when(child.getOtherParent()).thenReturn(null);

        assertValidationResult(false, child, true);
    }

    @Test
    @DisplayName("Child has no parents at all")
    void childHasNoParents() {
        final EventImpl child = generateEvent(null, null, -1L, -1L, defaultChildTime, null);
        when(child.getOtherParent()).thenReturn(null);
        when(child.getSelfParent()).thenReturn(null);

        assertValidationResult(false, child, true);
    }

    @Test
    @DisplayName("Self parent hash is missing")
    void missingSelfParentHash() {
        final EventImpl child = generateEvent(
                null,
                randomHash(random),
                defaultSelfParentGen,
                defaultOtherParentGen,
                defaultChildTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, false);
    }

    @Test
    @DisplayName("Other parent hash is missing")
    void missingOtherParentHash() {
        final EventImpl child = generateEvent(
                randomHash(random),
                null,
                defaultSelfParentGen,
                defaultOtherParentGen,
                defaultChildTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, false);
    }

    @Test
    @DisplayName("Self parent generation is invalid")
    void selfParentGenInvalid() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                -1L,
                defaultOtherParentGen,
                defaultChildTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, false);
    }

    @Test
    @DisplayName("Other parent generation is invalid")
    void otherParentGenInvalid() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                defaultSelfParentGen,
                -1L,
                defaultChildTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, false);
    }

    @Test
    @DisplayName("Test with parents from the first valid generation")
    void genesisParents() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                FIRST_GENERATION,
                FIRST_GENERATION,
                defaultChildTime,
                defaultSelfParentTime);

        assertValidationResult(false, child, true);
    }

    @Test
    @DisplayName("Test validation with an event where self and other parent are the same")
    void singleParent() {
        final Hash hash = randomHash(random);

        final EventImpl child = generateEvent(
                hash, hash, defaultSelfParentGen, defaultSelfParentGen, defaultChildTime, defaultSelfParentTime);

        assertValidationResult(false, child, false);
        assertValidationResult(true, child, true);
    }

    @Test
    @DisplayName("Ancient events should be discarded")
    void ancientEvent() {
        final EventImpl child = generateEvent(
                randomHash(random),
                randomHash(random),
                defaultSelfParentGen,
                defaultOtherParentGen,
                defaultChildTime,
                defaultSelfParentTime);

        multiNodeNetworkValidator.setMinimumGenerationNonAncient(100L);

        assertValidationResult(false, child, false);
    }
}
