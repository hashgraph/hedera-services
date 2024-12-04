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

package com.hedera.node.app.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.roster.schemas.RosterTransplantSchema;
import com.hedera.node.app.roster.schemas.V0540RosterSchema;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RosterServiceTest {
    @Mock
    private Predicate<Roster> canAdopt;

    @Mock
    private Supplier<ReadablePlatformStateStore> platformStateStoreFactory;

    private RosterService rosterService;

    @BeforeEach
    void setUp() {
        rosterService = new RosterService(canAdopt, platformStateStoreFactory);
    }

    @Test
    void registerSchemasNullArgsThrow() {
        Assertions.assertThatThrownBy(() -> rosterService.registerSchemas(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerExpectedSchemas() {
        final var schemaRegistry = mock(SchemaRegistry.class);

        rosterService.registerSchemas(schemaRegistry);
        final var captor = ArgumentCaptor.forClass(Schema.class);
        verify(schemaRegistry).register(captor.capture());
        final var schemas = captor.getAllValues();
        assertThat(schemas).hasSize(1);
        assertThat(schemas.getFirst()).isInstanceOf(V0540RosterSchema.class);
        assertThat(schemas.getFirst()).isInstanceOf(RosterTransplantSchema.class);
    }

    @Test
    void testServiceNameReturnsCorrectName() {
        assertThat(rosterService.getServiceName()).isEqualTo(RosterService.NAME);
    }
}
