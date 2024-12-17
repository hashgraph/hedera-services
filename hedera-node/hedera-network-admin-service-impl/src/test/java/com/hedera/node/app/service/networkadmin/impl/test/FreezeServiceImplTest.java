/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.test;

import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.FREEZE_TIME_KEY;
import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.UPGRADE_FILE_HASH_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.fixtures.state.FakeSchemaRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreezeServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private StartupNetworks startupNetworks;

    @Test
    void testSpi() {
        // when
        final FreezeService service = new FreezeServiceImpl();

        // then
        assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(
                FreezeServiceImpl.class,
                service.getClass(),
                "We must always receive an instance of type " + FreezeServiceImpl.class.getName());
    }

    @Test
    void registersExpectedSchema() {
        final var subject = new FreezeServiceImpl();
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        subject.registerSchemas(registry);
        verify(registry).register(schemaCaptor.capture());
        final var schema = schemaCaptor.getValue();

        final var statesToCreate = schema.statesToCreate();
        assertEquals(2, statesToCreate.size());
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(FREEZE_TIME_KEY, iter.next());
        assertEquals(UPGRADE_FILE_HASH_KEY, iter.next());
    }

    @Test
    void migratesAsExpected() {
        final var subject = new FreezeServiceImpl();
        final var registry = new FakeSchemaRegistry();
        final var state = new FakeState();

        subject.registerSchemas(registry);
        registry.migrate(FreezeService.NAME, state, networkInfo, startupNetworks);
        final var upgradeFileHashKeyState =
                state.getReadableStates(FreezeService.NAME).getSingleton(UPGRADE_FILE_HASH_KEY);
        assertNull(upgradeFileHashKeyState.get());
    }
}
