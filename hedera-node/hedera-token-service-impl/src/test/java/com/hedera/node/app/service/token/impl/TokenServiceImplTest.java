/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.token.CryptoServiceDefinition;
import com.hedera.node.app.service.token.TokenServiceDefinition;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0500TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0530TokenSchema;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import java.util.SortedMap;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {
    @Mock
    private Supplier<SortedMap<Long, Long>> pendingRewards;

    private TokenServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new TokenServiceImpl(pendingRewards);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void registerSchemasNullArgsThrow() {
        Assertions.assertThatThrownBy(() -> subject.registerSchemas(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerSchemasRegistersTokenSchema() {
        final var schemaRegistry = mock(SchemaRegistry.class);

        subject.registerSchemas(schemaRegistry);
        final var captor = ArgumentCaptor.forClass(Schema.class);
        verify(schemaRegistry, times(3)).register(captor.capture());
        final var schemas = captor.getAllValues();
        assertThat(schemas).hasSize(3);
        assertThat(schemas.getFirst()).isInstanceOf(V0490TokenSchema.class);
        assertThat(schemas.get(1)).isInstanceOf(V0500TokenSchema.class);
        assertThat(schemas.getLast()).isInstanceOf(V0530TokenSchema.class);
    }

    @Test
    void verifyServiceName() {
        assertThat(subject.getServiceName()).isEqualTo("TokenService");
    }

    @Test
    void rpcDefinitions() {
        assertThat(subject.rpcDefinitions())
                .containsExactlyInAnyOrder(CryptoServiceDefinition.INSTANCE, TokenServiceDefinition.INSTANCE);
    }
}
