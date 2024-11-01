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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swirlds.common.RosterStateId;
import com.swirlds.platform.state.service.schemas.V0540RosterSchema;
import com.swirlds.state.merkle.Schema;
import com.swirlds.state.merkle.SchemaRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link RosterService}.
 */
class RosterServiceTest {
    private RosterService rosterService;

    @BeforeEach
    void setUp() {
        rosterService = new RosterService();
    }

    @Test
    void defaultConstructor() {
        assertThat(new RosterService()).isNotNull();
    }

    @Test
    void registerSchemasNullArgsThrow() {
        Assertions.assertThatThrownBy(() -> rosterService.registerSchemas(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerSchemasRegistersTokenSchema() {
        final var schemaRegistry = mock(SchemaRegistry.class);

        rosterService.registerSchemas(schemaRegistry);
        final var captor = ArgumentCaptor.forClass(Schema.class);
        verify(schemaRegistry, times(1)).register(captor.capture());
        final var schemas = captor.getAllValues();
        assertThat(schemas).hasSize(1);
        assertThat(schemas.getFirst()).isInstanceOf(V0540RosterSchema.class);
    }

    @Test
    void testServiceNameReturnsCorrectName() {
        assertThat(rosterService.getServiceName()).isEqualTo(RosterStateId.NAME);
    }
}
