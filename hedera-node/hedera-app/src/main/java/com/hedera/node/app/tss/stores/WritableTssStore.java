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
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_STATUS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Extends the {@link ReadableTssStoreImpl} with write access to the TSS base store.
 */
public class WritableTssStore extends ReadableTssStoreImpl {
    /**
     * The underlying data storage class that holds the Pending Airdrops data.
     */
    private final WritableKVState<TssMessageMapKey, TssMessageTransactionBody> tssMessageState;

    private final WritableKVState<TssVoteMapKey, TssVoteTransactionBody> tssVoteState;

    private final WritableKVState<EntityNumber, TssEncryptionKeyTransactionBody> tssEncryptionKeyState;

    private final WritableSingletonState<TssStatus> tssStatusState;

    public WritableTssStore(@NonNull final WritableStates states) {
        super(states);
        this.tssMessageState = states.get(TSS_MESSAGE_MAP_KEY);
        this.tssVoteState = states.get(TSS_VOTE_MAP_KEY);
        this.tssEncryptionKeyState = states.get(TSS_ENCRYPTION_KEYS_KEY);
        this.tssStatusState = states.getSingleton(TSS_STATUS_KEY);
    }

    public void put(@NonNull final TssMessageMapKey tssMessageMapKey, @NonNull final TssMessageTransactionBody txBody) {
        requireNonNull(tssMessageMapKey);
        requireNonNull(txBody);
        tssMessageState.put(tssMessageMapKey, txBody);
    }

    public void put(@NonNull final TssVoteMapKey tssVoteMapKey, @NonNull final TssVoteTransactionBody txBody) {
        requireNonNull(tssVoteMapKey);
        requireNonNull(txBody);
        tssVoteState.put(tssVoteMapKey, txBody);
    }

    public void put(@NonNull final EntityNumber entityNumber, @NonNull final TssEncryptionKeyTransactionBody txBody) {
        requireNonNull(entityNumber);
        requireNonNull(txBody);
        tssEncryptionKeyState.put(entityNumber, txBody);
    }

    public void put(@NonNull final TssStatus tssStatus) {
        requireNonNull(tssStatus);
        tssStatusState.put(tssStatus);
    }

    public void remove(@NonNull final TssMessageMapKey tssMessageMapKey) {
        requireNonNull(tssMessageMapKey);
        tssMessageState.remove(tssMessageMapKey);
    }

    public void remove(@NonNull final TssVoteMapKey tssVoteMapKey) {
        requireNonNull(tssVoteMapKey);
        tssVoteState.remove(tssVoteMapKey);
    }

    public void remove(@NonNull final EntityNumber entityNumber) {
        requireNonNull(entityNumber);
        tssEncryptionKeyState.remove(entityNumber);
    }

    public void clear() {
        tssVoteState.keys().forEachRemaining(tssVoteState::remove);
        tssMessageState.keys().forEachRemaining(tssMessageState::remove);
        tssEncryptionKeyState.keys().forEachRemaining(tssEncryptionKeyState::remove);
        tssStatusState.put(TssStatus.DEFAULT);
    }
}
