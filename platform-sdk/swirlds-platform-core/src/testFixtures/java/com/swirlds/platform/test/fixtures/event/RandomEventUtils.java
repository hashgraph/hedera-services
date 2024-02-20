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

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Random;

public class RandomEventUtils {
    public static final Instant DEFAULT_FIRST_EVENT_TIME_CREATED = Instant.ofEpochMilli(1588771316678L);

    /**
     * Similar to randomEvent, but the timestamp used for the event's creation timestamp
     * is provided by an argument.
     */
    public static IndexedEvent randomEventWithTimestamp(
            final Random random,
            final NodeId creatorId,
            final Instant timestamp,
            final long birthRound,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {

        final BaseEventHashedData hashedData = randomEventHashedDataWithTimestamp(
                random, creatorId, timestamp, birthRound, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParent != null ? otherParent.getCreatorId() : NodeId.UNDEFINED_NODE_ID, sig);

        return new IndexedEvent(hashedData, unhashedData, selfParent, otherParent);
    }

    /**
     * Similar to randomEventHashedData but where the timestamp provided to this
     * method is the timestamp used as the creation timestamp for the event.
     */
    public static BaseEventHashedData randomEventHashedDataWithTimestamp(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant timestamp,
            final long birthRound,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
            final boolean fakeHash) {

        final EventDescriptor selfDescriptor = (selfParent == null || selfParent.getBaseHash() == null)
                ? null
                : new EventDescriptor(
                        selfParent.getBaseHash(),
                        selfParent.getCreatorId(),
                        selfParent.getGeneration(),
                        selfParent.getBaseEvent().getHashedData().getBirthRound());
        final EventDescriptor otherDescriptor = (otherParent == null || otherParent.getBaseHash() == null)
                ? null
                : new EventDescriptor(
                        otherParent.getBaseHash(),
                        otherParent.getCreatorId(),
                        otherParent.getGeneration(),
                        otherParent.getBaseEvent().getHashedData().getBirthRound());

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                creatorId,
                selfDescriptor,
                otherDescriptor == null ? Collections.emptyList() : Collections.singletonList(otherDescriptor),
                birthRound,
                timestamp,
                transactions);

        if (fakeHash) {
            hashedData.setHash(RandomUtils.randomHash(random));
        } else {
            CryptographyHolder.get().digestSync(hashedData);
        }
        return hashedData;
    }
}
