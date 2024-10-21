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

package com.hedera.node.app.tss.stores;

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides read-only access to the TSS base store.
 */
public class ReadableTssBaseStore implements ReadableTssStore {
    /**
     * The underlying data storage class that holds the airdrop data.
     */
    private final ReadableKVState<TssMessageMapKey, TssMessageTransactionBody> readableTssMessageState;

    private final ReadableKVState<TssVoteMapKey, TssVoteTransactionBody> readableTssVoteState;

    /**
     * Create a new {@link ReadableTssBaseStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableTssBaseStore(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.readableTssMessageState = states.get(TSS_MESSAGE_MAP_KEY);
        this.readableTssVoteState = states.get(TSS_VOTE_MAP_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TssMessageTransactionBody getMessage(@NonNull final TssMessageMapKey tssMessageKey) {
        return readableTssMessageState.get(tssMessageKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(@NonNull final TssMessageMapKey tssMessageKey) {
        return readableTssMessageState.contains(tssMessageKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TssVoteTransactionBody getVote(@NonNull final TssVoteMapKey tssVoteKey) {
        return readableTssVoteState.get(tssVoteKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(@NonNull final TssVoteMapKey tssVoteKey) {
        return readableTssVoteState.contains(tssVoteKey);
    }

    @Override
    public long messageStateSize() {
        return readableTssMessageState.size();
    }

    @Override
    public List<TssMessageTransactionBody> getTssMessages(final Bytes rosterHash) {
        final List<TssMessageTransactionBody> tssMessages = new ArrayList<>();
        readableTssMessageState.keys().forEachRemaining(key -> {
            if (key.rosterHash().equals(rosterHash)) {
                tssMessages.add(readableTssMessageState.get(key));
            }
        });
        return tssMessages;
    }
}
