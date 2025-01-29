/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.ids;

import static com.hedera.node.app.ids.schemas.V0490EntityIdSchema.ENTITY_ID_STATE_KEY;
import static com.hedera.node.app.ids.schemas.V0590EntityIdSchema.ENTITY_COUNTS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.entity.EntityCounts;
import com.hedera.node.app.hapi.utils.EntityType;
import com.hedera.node.app.spi.ids.WritableEntityCounters;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A writeable store for entity ids.
 */
public class WritableEntityIdStore extends ReadableEntityIdStoreImpl implements WritableEntityCounters {
    /**
     * The underlying data storage class that holds the entity id data.
     */
    private final WritableSingletonState<EntityNumber> entityIdState;

    private final WritableSingletonState<EntityCounts> entityCountsState;

    /**
     * Create a new {@link WritableEntityIdStore} instance.
     *
     * @param states The state to use.
     */
    public WritableEntityIdStore(@NonNull final WritableStates states) {
        super(states);
        requireNonNull(states);
        this.entityIdState = states.getSingleton(ENTITY_ID_STATE_KEY);
        this.entityCountsState = states.getSingleton(ENTITY_COUNTS_KEY);
    }

    /**
     * Returns the next entity number that will be used.
     *
     * @return the next entity number that will be used
     */
    public long peekAtNextNumber() {
        final var oldEntityNum = entityIdState.get();
        return oldEntityNum == null ? 1 : oldEntityNum.number() + 1;
    }

    /**
     * Increments the current entity number in state and returns the new value.
     *
     * @return the next new entity number
     */
    public long incrementAndGet() {
        final var newEntityNum = peekAtNextNumber();
        entityIdState.put(new EntityNumber(newEntityNum));
        return newEntityNum;
    }

    @Override
    public void incrementEntityTypeCount(final EntityType entityType) {
        adjustEntityCount(entityType, 1);
    }

    @Override
    public void adjustEntityCount(final EntityType entityType, final long delta) {
        final var entityCounts = requireNonNull(entityCountsState.get());
        final var newEntityCounts = entityCounts.copyBuilder();
        switch (entityType) {
            case ACCOUNT -> newEntityCounts.numAccounts(entityCounts.numAccounts() + delta);
            case ALIAS -> newEntityCounts.numAliases(entityCounts.numAliases() + delta);
            case TOKEN -> newEntityCounts.numTokens(entityCounts.numTokens() + delta);
            case TOKEN_ASSOCIATION -> newEntityCounts.numTokenRelations(entityCounts.numTokenRelations() + delta);
            case TOPIC -> newEntityCounts.numTopics(entityCounts.numTopics() + delta);
            case FILE -> newEntityCounts.numFiles(entityCounts.numFiles() + delta);
            case CONTRACT_BYTECODE -> newEntityCounts.numContractBytecodes(entityCounts.numContractBytecodes() + delta);
            case CONTRACT_STORAGE -> newEntityCounts.numContractStorageSlots(
                    entityCounts.numContractStorageSlots() + delta);
            case NFT -> newEntityCounts.numNfts(entityCounts.numNfts() + delta);
            case SCHEDULE -> newEntityCounts.numSchedules(entityCounts.numSchedules() + delta);
            case AIRDROP -> newEntityCounts.numAirdrops(entityCounts.numAirdrops() + delta);
            case NODE -> newEntityCounts.numNodes(entityCounts.numNodes() + delta);
            case STAKING_INFO -> newEntityCounts.numStakingInfos(entityCounts.numStakingInfos() + delta);
        }
        entityCountsState.put(newEntityCounts.build());
    }

    @Override
    public void decrementEntityTypeCounter(final EntityType entityType) {
        adjustEntityCount(entityType, -1);
    }
}
