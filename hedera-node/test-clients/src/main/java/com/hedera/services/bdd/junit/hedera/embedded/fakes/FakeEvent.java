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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.support.translators.inputs.TransactionParts;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;

public class FakeEvent implements Event {
    private static final Bytes FAKE_SHA_384_SIGNATURE = Bytes.wrap(new byte[] {
        (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
        (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
        (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
        (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
        (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
        (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
    });

    private final NodeId creatorId;
    private final Instant timeCreated;
    private final SemanticVersion version;
    private final EventCore eventCore;
    public final TransactionWrapper transaction;

    public FakeEvent(
            @NonNull final NodeId creatorId,
            @NonNull final Instant timeCreated,
            @NonNull final SemanticVersion version,
            @NonNull final TransactionWrapper transaction) {
        this.version = requireNonNull(version);
        this.creatorId = requireNonNull(creatorId);
        this.timeCreated = requireNonNull(timeCreated);
        this.transaction = requireNonNull(transaction);
        this.eventCore = EventCore.newBuilder()
                .creatorNodeId(creatorId.id())
                .timeCreated(HapiUtils.asTimestamp(timeCreated))
                .version(version)
                .build();
    }

    @Override
    public Iterator<Transaction> transactionIterator() {
        return Collections.singleton((Transaction) transaction).iterator();
    }

    @Override
    public Instant getTimeCreated() {
        return timeCreated;
    }

    @NonNull
    @Override
    public NodeId getCreatorId() {
        return creatorId;
    }

    @NonNull
    @Override
    public SemanticVersion getSoftwareVersion() {
        return version;
    }

    @NonNull
    @Override
    public EventCore getEventCore() {
        return eventCore;
    }

    @NonNull
    @Override
    public Bytes getSignature() {
        return FAKE_SHA_384_SIGNATURE;
    }

    @NonNull
    public HederaFunctionality function(){
        return TransactionParts.from(transaction.getApplicationTransaction()).function();
    }
}
