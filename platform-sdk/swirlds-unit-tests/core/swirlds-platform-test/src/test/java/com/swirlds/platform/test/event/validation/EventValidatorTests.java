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

package com.swirlds.platform.test.event.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.validation.EventDeduplication;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.event.validation.GossipEventValidator;
import com.swirlds.platform.event.validation.GossipEventValidators;
import com.swirlds.platform.event.validation.StaticValidators;
import com.swirlds.platform.event.validation.TransactionSizeValidator;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.test.event.EventBuilder;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

class EventValidatorTests {
    private static final GossipEventValidator VALID = (e) -> true;
    private static final GossipEventValidator INVALID = (e) -> false;

    private static BaseEvent eventWithParents(
            final long selfParentGen,
            final long otherParentGen,
            final Hash selfParentHash,
            final Hash otherParentHash) {
        final BaseEvent event = mock(BaseEvent.class);
        final long creatorId = 0;
        final Instant timeCreated = Instant.now();
        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[0];
        Mockito.when(event.getHashedData())
                .thenReturn(new BaseEventHashedData(
                        creatorId,
                        selfParentGen,
                        otherParentGen,
                        selfParentHash,
                        otherParentHash,
                        timeCreated,
                        transactions));
        return event;
    }

    @Test
    void baseEventValidators() {
        final GossipEvent event = EventBuilder.builder().buildGossipEvent();
        assertTrue(new GossipEventValidators(List.of()).isEventValid(event));
        assertTrue(new GossipEventValidators(List.of(VALID)).isEventValid(event));
        assertTrue(new GossipEventValidators(List.of(VALID, VALID, VALID)).isEventValid(event));
        assertFalse(new GossipEventValidators(List.of(VALID, INVALID, VALID)).isEventValid(event));
    }

    @Test
    void eventValidator() {
        final Set<GossipEvent> intakeEvents = new HashSet<>();
        final AtomicBoolean isValid = new AtomicBoolean(true);
        final EventValidator eventValidator = new EventValidator((e) -> isValid.get(), intakeEvents::add);

        final GossipEvent validEvent = EventBuilder.builder().buildGossipEvent();
        eventValidator.validateEvent(validEvent);
        assertTrue(intakeEvents.contains(validEvent), "event should have been passed to intake");

        isValid.set(false);
        final GossipEvent invalidEvent = EventBuilder.builder().buildGossipEvent();
        eventValidator.validateEvent(invalidEvent);
        assertFalse(intakeEvents.contains(invalidEvent), "event should not have been passed to intake");
    }

    @Test
    void eventDeduplication() {
        final AtomicBoolean isDuplicate = new AtomicBoolean(true);
        final EventIntakeMetrics metrics = mock(EventIntakeMetrics.class);
        final EventDeduplication deduplication =
                new EventDeduplication(e -> isDuplicate.get(), metrics);
        final GossipEvent event = EventBuilder.builder().buildGossipEvent();

        assertFalse(deduplication.isEventValid(event), "it should be a duplicate, so not valid");
        verify(metrics, description("metrics should have recorded the duplicate event"))
                .duplicateEvent();
        verify(metrics, never().description("there was no non-duplicate event so far"))
                .nonDuplicateEvent();

        isDuplicate.set(false);

        assertTrue(deduplication.isEventValid(event), "it should not be a duplicate, so valid");
        verify(metrics, description("metrics should have recorded the duplicate event"))
                .duplicateEvent();
        verify(metrics, description("metrics should have recorded the non-duplicate event"))
                .nonDuplicateEvent();
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1999, 2000, 2001, 10_000})
    void accumulatedTransactionSize(final int transAmount) {
        final int maxTransactionBytesPerEvent = 2000;
        final GossipEventValidator validator =
                new TransactionSizeValidator(maxTransactionBytesPerEvent);
        final GossipEvent event =
                EventBuilder.builder().setNumberOfTransactions(transAmount).buildGossipEvent();

        int eventTransSize = 0;
        for (final Transaction t : event.getHashedData().getTransactions()) {
            eventTransSize += t.getSerializedLength();
        }
        if (eventTransSize <= maxTransactionBytesPerEvent) {
            assertTrue(validator.isEventValid(event), "transaction limit should not have been exceeded");
        } else {
            assertFalse(validator.isEventValid(event), "transaction limit should have been exceeded");
        }
    }

    @Test
    void parentValidity() {
        final long undefinedGeneration = EventConstants.GENERATION_UNDEFINED;
        final long validGeneration = 10;
        final Hash hash1 = RandomUtils.randomHash();
        final Hash hash2 = RandomUtils.randomHash();
        final Hash nullHash = null;

        // has generation but no hash
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(validGeneration, undefinedGeneration, nullHash, nullHash)));
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, validGeneration, nullHash, nullHash)));

        // has hash but no generation
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, undefinedGeneration, hash1, nullHash)));
        assertFalse(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, hash2)));

        // both parents same hash
        assertFalse(
                StaticValidators.isParentDataValid(eventWithParents(validGeneration, validGeneration, hash1, hash1)));

        // no parents
        assertTrue(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, undefinedGeneration, nullHash, nullHash)));

        // valid parents
        assertTrue(StaticValidators.isParentDataValid(
                eventWithParents(validGeneration, undefinedGeneration, hash1, nullHash)));
        assertTrue(StaticValidators.isParentDataValid(
                eventWithParents(undefinedGeneration, validGeneration, nullHash, hash2)));
        assertTrue(
                StaticValidators.isParentDataValid(eventWithParents(validGeneration, validGeneration, hash1, hash2)));
    }

    @Test
    void invalidCreationTime() {
        final Instant time = Instant.now();
        final GossipEvent gossipEvent =
                EventBuilder.builder().setTimestamp(time).buildGossipEvent();
        final EventImpl event = new EventImpl(gossipEvent, null, null);

        event.setSelfParent(
                new EventImpl(
                        EventBuilder.builder().setTimestamp(time.plusNanos(100)).buildGossipEvent(),
                        null,
                        null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");

        event.setSelfParent(
                new EventImpl(
                        EventBuilder.builder().setTimestamp(time.plusNanos(1)).buildGossipEvent(),
                        null,
                        null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");

        event.setSelfParent(
                new EventImpl(
                        EventBuilder.builder().setTimestamp(time).buildGossipEvent(), null, null));
        assertFalse(StaticValidators.isValidTimeCreated(event), "should be invalid");
    }
}
