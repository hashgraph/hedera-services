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

package com.hedera.node.app.tss;

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides read-only access to the TSS base store.
 * <p>
 * <b>TODO:</b> (TSS-FUTURE) Implement read-only access to the TSS base state.
 */
public class ReadableTssBaseStore {
    private final ReadableKVState<TssMessageMapKey, TssMessageTransactionBody> tssMessages;
    private final ReadableKVState<TssVoteMapKey, TssVoteMapKey> tssVotes;

    public ReadableTssBaseStore(@NonNull final ReadableStates readableStates) {
        this.tssMessages = readableStates.get(TSS_MESSAGE_MAP_KEY);
        this.tssVotes = readableStates.get(TSS_VOTE_MAP_KEY);
    }
}
