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
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.entity.EntityCounts;
import com.hedera.node.app.spi.validation.EntityType;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WritableEntityIdStoreTest {
    private final AtomicReference<EntityNumber> nextEntityNumber = new AtomicReference<>();
    private final AtomicReference<EntityCounts> entityCounts = new AtomicReference<>();
    private final WritableSingletonState<EntityNumber> entityIdState =
            new WritableSingletonStateBase<>(ENTITY_ID_STATE_KEY, nextEntityNumber::get, nextEntityNumber::set);
    private final WritableSingletonState<EntityCounts> entityCountsState =
            new WritableSingletonStateBase<>(ENTITY_COUNTS_KEY, entityCounts::get, entityCounts::set);
    private WritableEntityIdStore subject;

    @BeforeEach
    void setup() {
        nextEntityNumber.set(EntityNumber.DEFAULT);
        entityCounts.set(EntityCounts.newBuilder().numAccounts(10L).build());
        final var writableStates =
                new MapWritableStates(Map.of(ENTITY_ID_STATE_KEY, entityIdState, ENTITY_COUNTS_KEY, entityCountsState));
        subject = new WritableEntityIdStore(writableStates);
    }

    @Test
    void peeksAndIncrementsAsExpected() {
        assertEquals(1, subject.peekAtNextNumber());
        assertEquals(1, subject.incrementAndGet(EntityType.ACCOUNT));
        assertEquals(2, subject.peekAtNextNumber());
        assertEquals(2, subject.incrementAndGet(EntityType.ACCOUNT));
        assertEquals(12, entityCountsState.get().numAccounts());
    }

    @Test
    void incrementsAsExpected() {
        assertEquals(1, subject.incrementAndGet(EntityType.TOKEN));
        assertEquals(1, entityCountsState.get().numTokens());
        assertEquals(2, subject.incrementAndGet(EntityType.TOKEN_ASSOCIATION));
        assertEquals(1, entityCountsState.get().numTokenRelations());
        assertEquals(3, subject.incrementAndGet(EntityType.NFT));
        assertEquals(1, entityCountsState.get().numNfts());
        assertEquals(4, subject.incrementAndGet(EntityType.ALIAS));
        assertEquals(1, entityCountsState.get().numAliases());
        assertEquals(5, subject.incrementAndGet(EntityType.NODE));
        assertEquals(1, entityCountsState.get().numNodes());
        assertEquals(6, subject.incrementAndGet(EntityType.SCHEDULE));
        assertEquals(1, entityCountsState.get().numSchedules());
        assertEquals(7, subject.incrementAndGet(EntityType.CONTRACT_BYTECODE));
        assertEquals(1, entityCountsState.get().numContractBytecodes());
        assertEquals(8, subject.incrementAndGet(EntityType.CONTRACT_STORAGE));
        assertEquals(1, entityCountsState.get().numContractStorageSlots());
        assertEquals(9, subject.incrementAndGet(EntityType.TOPIC));
        assertEquals(1, entityCountsState.get().numTopics());
        assertEquals(10, subject.incrementAndGet(EntityType.FILE));
        assertEquals(1, entityCountsState.get().numFiles());
        assertEquals(11, subject.incrementAndGet(EntityType.AIRDROP));
        assertEquals(1, entityCountsState.get().numAirdrops());
        assertEquals(12, subject.incrementAndGet(EntityType.STAKING_INFO));
        assertEquals(1, entityCountsState.get().numStakingInfos());
    }
}
