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
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_ENCRYPTION_KEY_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_STATUS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.RosterEntry;
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
import java.util.List;

/**
 * Extends the {@link ReadableTssStoreImpl} with write access to the TSS base store.
 */
public class WritableTssStore extends ReadableTssStoreImpl {
    /**
     * The underlying data storage class that holds the TssMessageTransaction data.
     */
    private final WritableKVState<TssMessageMapKey, TssMessageTransactionBody> tssMessageState;
    /**
     * The underlying data storage class that holds the TssVoteTransaction data.
     */
    private final WritableKVState<TssVoteMapKey, TssVoteTransactionBody> tssVoteState;
    /**
     * The underlying data storage class that holds the Node ID to TssEncryptionKeyTransaction data.
     */
    private final WritableKVState<EntityNumber, TssEncryptionKeyTransactionBody> tssEncryptionKeyState;

    /**
     * The singleton data storage that holds the current Tss state as a single status.
     */
    private final WritableSingletonState<TssStatus> tssStatusState;

    /**
     * Constructs a new {@link WritableTssStore} instance.
     *
     * @param states the writable states
     */
    public WritableTssStore(@NonNull final WritableStates states) {
        super(states);
        this.tssMessageState = states.get(TSS_MESSAGE_MAP_KEY);
        this.tssVoteState = states.get(TSS_VOTE_MAP_KEY);
        this.tssEncryptionKeyState = states.get(TSS_ENCRYPTION_KEY_MAP_KEY);
        this.tssStatusState = states.getSingleton(TSS_STATUS_KEY);
    }

    /**
     * Persists a new {@link TssMessageMapKey} with given {@link TssMessageTransactionBody} into the state.
     *
     * @param tssMessageMapKey  the {@link TssMessageMapKey} containing a target roster to be persisted
     * @param txBody            the body of {@link TssMessageTransactionBody} for the given roster to be persisted
     */
    public void put(@NonNull final TssMessageMapKey tssMessageMapKey, @NonNull final TssMessageTransactionBody txBody) {
        requireNonNull(tssMessageMapKey);
        requireNonNull(txBody);
        tssMessageState.put(tssMessageMapKey, txBody);
    }

    /**
     * Persists a new {@link TssVoteMapKey} with given {@link TssVoteTransactionBody} into the state.
     *
     * @param tssVoteMapKey     the {@link TssVoteMapKey} containing a target roster to be persisted
     * @param txBody            the {@link TssVoteTransactionBody} for the given roster to be persisted
     */
    public void put(@NonNull final TssVoteMapKey tssVoteMapKey, @NonNull final TssVoteTransactionBody txBody) {
        requireNonNull(tssVoteMapKey);
        requireNonNull(txBody);
        tssVoteState.put(tssVoteMapKey, txBody);
    }

    /**
     * Persists a new {@link EntityNumber} with given {@link TssEncryptionKeyTransactionBody} into the state.
     *
     * @param entityNumber      the corresponding Node ID to the {@link TssEncryptionKeyTransactionBody} to be persisted
     * @param txBody            the {@link TssEncryptionKeyTransactionBody} for the given node to be persisted
     */
    public void put(@NonNull final EntityNumber entityNumber, @NonNull final TssEncryptionKeyTransactionBody txBody) {
        requireNonNull(entityNumber);
        requireNonNull(txBody);
        tssEncryptionKeyState.put(entityNumber, txBody);
    }

    /**
     * Persists a new {@link TssStatus} for the current Tss state.
     *
     * @param tssStatus the {@link TssStatus} to be persisted
     */
    public void put(@NonNull final TssStatus tssStatus) {
        requireNonNull(tssStatus);
        tssStatusState.put(tssStatus);
    }

    /**
     * Removes a {@link TssMessageTransactionBody} from the state.
     *
     * @param tssMessageMapKey for which the {@link TssMessageTransactionBody} to be removed.
     */
    public void remove(@NonNull final TssMessageMapKey tssMessageMapKey) {
        requireNonNull(tssMessageMapKey);
        tssMessageState.remove(tssMessageMapKey);
    }

    /**
     * Removes a {@link TssVoteTransactionBody} from the state.
     *
     * @param tssVoteMapKey for which the {@link TssVoteTransactionBody} to be removed.
     */
    public void remove(@NonNull final TssVoteMapKey tssVoteMapKey) {
        requireNonNull(tssVoteMapKey);
        tssVoteState.remove(tssVoteMapKey);
    }

    /**
     * Removes a {@link TssEncryptionKeyTransactionBody} from the state.
     *
     * @param entityNumber the Node for which the {@link TssEncryptionKeyTransactionBody} to be removed.
     */
    public void remove(@NonNull final EntityNumber entityNumber) {
        requireNonNull(entityNumber);
        tssEncryptionKeyState.remove(entityNumber);
    }

    /**
     * Removes EntityNumber (Node ID) from the {@link TssEncryptionKeyTransactionBody} map, but only if
     * the Node ID is present in neither the active roster's and the candidate roster's entries. {@link RosterEntry}
     *
     * @param rostersEntriesNodeIds contains the non-duplicate Node IDs of current active and candidate rosters entries
     */
    public void removeIfNotPresent(@NonNull final List<EntityNumber> rostersEntriesNodeIds) {
        requireNonNull(rostersEntriesNodeIds);
        tssEncryptionKeyState.keys().forEachRemaining(entityNumber -> {
            if (!rostersEntriesNodeIds.contains(entityNumber)) {
                remove(entityNumber);
            }
        });
    }

    /**
     * Remove all TSS transaction bodies from the state.
     */
    public void clear() {
        tssVoteState.keys().forEachRemaining(tssVoteState::remove);
        tssMessageState.keys().forEachRemaining(tssMessageState::remove);
        tssEncryptionKeyState.keys().forEachRemaining(tssEncryptionKeyState::remove);
        tssStatusState.put(TssStatus.DEFAULT);
    }
}
