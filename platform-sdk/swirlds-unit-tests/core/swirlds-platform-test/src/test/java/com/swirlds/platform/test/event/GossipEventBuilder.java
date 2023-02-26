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

package com.swirlds.platform.test.event;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.event.GossipEvent;
import java.time.Instant;
import java.util.Random;

public class GossipEventBuilder {
    private Random random;
    private long creatorId;
    private Instant timestamp;
    private int numberOfTransactions;
    private int transactionSize;
    private GossipEvent selfParent;
    private GossipEvent otherParent;
    private boolean fakeHash;
    private long fakeGeneration;

    public static GossipEventBuilder builder() {
        return new GossipEventBuilder().setDefaults();
    }

    public GossipEventBuilder setDefaults() {
        random = new Random();
        creatorId = 0;
        timestamp = Instant.ofEpochMilli(1588771316678L);
        numberOfTransactions = 0;
        transactionSize = 4;
        selfParent = null;
        otherParent = null;
        fakeHash = true;
        fakeGeneration = Long.MIN_VALUE;
        return this;
    }

    public GossipEventBuilder setRandom(final Random random) {
        this.random = random;
        return this;
    }

    public GossipEventBuilder setCreatorId(final long creatorId) {
        this.creatorId = creatorId;
        return this;
    }

    public GossipEventBuilder setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public GossipEventBuilder setNumberOfTransactions(final int numberOfTransactions) {
        this.numberOfTransactions = numberOfTransactions;
        return this;
    }

    public GossipEventBuilder setTransactionSize(final int transactionSize) {
        this.transactionSize = transactionSize;
        return this;
    }

    public GossipEventBuilder setSelfParent(final GossipEvent selfParent) {
        this.selfParent = selfParent;
        return this;
    }

    public GossipEventBuilder setOtherParent(final GossipEvent otherParent) {
        this.otherParent = otherParent;
        return this;
    }

    public GossipEventBuilder setFakeHash(final boolean fakeHash) {
        this.fakeHash = fakeHash;
        return this;
    }

    public GossipEventBuilder setGeneration(final long generation) {
        fakeGeneration = generation;
        return this;
    }

    public GossipEvent buildEvent() {
        final ConsensusTransactionImpl[] tr = new ConsensusTransactionImpl[numberOfTransactions];
        for (int i = 0; i < tr.length; ++i) {
            final byte[] bytes = new byte[] {(byte) i, (byte) i, (byte) i, (byte) i};
            tr[i] = new SwirldTransaction(bytes);
        }
        final long selfParentGen = fakeGeneration >= GraphGenerations.FIRST_GENERATION
                ? fakeGeneration - 1
                : selfParent != null ? selfParent.getGeneration() : EventConstants.GENERATION_UNDEFINED;
        final long otherParentGen = fakeGeneration >= GraphGenerations.FIRST_GENERATION
                ? fakeGeneration - 1
                : otherParent != null ? otherParent.getGeneration() : -1;
        final BaseEventHashedData hashedData = new BaseEventHashedData(
                creatorId,
                selfParentGen,
                otherParentGen,
                selfParent != null ? selfParent.getHashedData().getHash() : null,
                otherParent != null ? otherParent.getHashedData().getHash() : null,
                timestamp,
                tr);

        if (fakeHash) {
            hashedData.setHash(RandomUtils.randomHash(random));
        } else {
            CryptographyHolder.get().digestSync(hashedData);
        }

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParent != null ? otherParent.getHashedData().getCreatorId() : -1, sig);
        final GossipEvent gossipEvent = new GossipEvent(hashedData, unhashedData);
        gossipEvent.buildDescriptor();
        return gossipEvent;
    }

    public GossipEventBuilder reset() {
        return setDefaults();
    }
}
