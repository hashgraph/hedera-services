/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.ids.EntityIdService.ENTITY_ID_STATE_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.store.WritableStoreFactory;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdServiceApiImplTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    @Mock
    private WritableStoreFactory storeFactory;

    private final AtomicReference<EntityNumber> nextEntityNumber = new AtomicReference<>();
    private final WritableSingletonState<EntityNumber> entityIdState =
            new WritableSingletonStateBase<>(ENTITY_ID_STATE_KEY, nextEntityNumber::get, nextEntityNumber::set);
    private final WritableStates writableStates = new MapWritableStates(Map.of(ENTITY_ID_STATE_KEY, entityIdState));
    private final WritableEntityIdStore idsStore = new WritableEntityIdStore(writableStates);

    private EntityIdServiceApiImpl subject;

    @BeforeEach
    void setUp() {
        subject = new EntityIdServiceApiImpl(DEFAULT_CONFIG, storeFactory);
    }

    @Test
    void peeksAndUsesAsExpected() {
        given(storeFactory.getStore(WritableEntityIdStore.class)).willReturn(idsStore);

        nextEntityNumber.set(new EntityNumber(123L));

        assertEquals(124L, subject.peekNextNumber());
        assertEquals(124L, subject.useNextNumber());
        assertEquals(125L, subject.peekNextNumber());
    }
}
