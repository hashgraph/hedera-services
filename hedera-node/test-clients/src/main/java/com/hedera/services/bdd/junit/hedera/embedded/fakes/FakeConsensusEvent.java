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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;

public class FakeConsensusEvent extends FakeEvent implements ConsensusEvent {
    private final long consensusOrder;
    private final Instant consensusTimestamp;

    public FakeConsensusEvent(
            @NonNull final FakeEvent event,
            final long consensusOrder,
            @NonNull final Instant consensusTimestamp,
            @NonNull final SemanticVersion version) {
        super(event.getCreatorId(), event.getTimeCreated(), version, event.transaction);
        this.consensusOrder = consensusOrder;
        this.consensusTimestamp = requireNonNull(consensusTimestamp);
        event.transaction.setConsensusTimestamp(consensusTimestamp);
    }

    @Override
    public @NonNull Iterator<ConsensusTransaction> consensusTransactionIterator() {
        return Collections.singleton((ConsensusTransaction) transaction).iterator();
    }

    @Override
    public @NonNull Iterator<Bytes> transactionBytesIterator() {
        return Collections.singleton(transaction.getTransactionsBytes()).iterator();
    }

    @Override
    public long getConsensusOrder() {
        return consensusOrder;
    }

    @Override
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }
}
