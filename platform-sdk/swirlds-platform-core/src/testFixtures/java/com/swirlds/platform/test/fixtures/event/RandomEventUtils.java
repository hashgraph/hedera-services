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

package com.swirlds.platform.test.fixtures.event;

import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.EventTransaction;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.hashing.PbjStreamHasher;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class RandomEventUtils {
    public static final Instant DEFAULT_FIRST_EVENT_TIME_CREATED = Instant.ofEpochMilli(1588771316678L);

    /**
     * Similar to randomEvent, but the timestamp used for the event's creation timestamp
     * is provided by an argument.
     */
    public static EventImpl randomEventWithTimestamp(
            final Random random,
            final NodeId creatorId,
            final Instant timestamp,
            final long birthRound,
            final TransactionWrapper[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {

        final UnsignedEvent unsignedEvent = randomUnsignedEventWithTimestamp(
                random, creatorId, timestamp, birthRound, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        return new EventImpl(new PlatformEvent(unsignedEvent, sig), selfParent, otherParent);
    }

    /**
     * Similar to randomEventHashedData but where the timestamp provided to this
     * method is the timestamp used as the creation timestamp for the event.
     */
    public static UnsignedEvent randomUnsignedEventWithTimestamp(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant timestamp,
            final long birthRound,
            @Nullable final TransactionWrapper[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
            final boolean fakeHash) {

        final EventDescriptorWrapper selfDescriptor = (selfParent == null || selfParent.getBaseHash() == null)
                ? null
                : new EventDescriptorWrapper(new EventDescriptor(
                        selfParent.getBaseHash().getBytes(),
                        selfParent.getCreatorId().id(),
                        selfParent.getBaseEvent().getBirthRound(),
                        selfParent.getGeneration()));
        final EventDescriptorWrapper otherDescriptor = (otherParent == null || otherParent.getBaseHash() == null)
                ? null
                : new EventDescriptorWrapper(new EventDescriptor(
                        otherParent.getBaseHash().getBytes(),
                        otherParent.getCreatorId().id(),
                        otherParent.getBaseEvent().getBirthRound(),
                        otherParent.getGeneration()));

        final List<EventTransaction> convertedTransactions = new ArrayList<>();
        if (transactions != null) {
            Stream.of(transactions).map(TransactionWrapper::getTransaction).forEach(convertedTransactions::add);
        }
        final UnsignedEvent unsignedEvent = new UnsignedEvent(
                new BasicSoftwareVersion(1),
                creatorId,
                selfDescriptor,
                otherDescriptor == null ? Collections.emptyList() : Collections.singletonList(otherDescriptor),
                birthRound,
                timestamp,
                convertedTransactions);

        if (fakeHash) {
            unsignedEvent.setHash(RandomUtils.randomHash(random));
        } else {
            new PbjStreamHasher().hashUnsignedEvent(unsignedEvent);
        }
        return unsignedEvent;
    }
}
