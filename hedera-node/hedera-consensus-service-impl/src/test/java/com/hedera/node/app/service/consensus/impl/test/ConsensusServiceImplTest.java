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

package com.hedera.node.app.service.consensus.impl.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConsensusServiceImplTest {
    private ConsensusServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusServiceImpl();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void registerSchemasNullArgsThrow() {
        assertThatThrownBy(() -> subject.registerSchemas(null, SemanticVersion.DEFAULT))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> subject.registerSchemas(mock(SchemaRegistry.class), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerSchemasRegistersTopicSchema() {
        final var schemaRegistry = mock(SchemaRegistry.class);

        subject.registerSchemas(schemaRegistry, SemanticVersion.DEFAULT);
        verify(schemaRegistry).register(notNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    void triesToSetStateWithoutRegisteredTopicSchema() {
        final var mMap = mock(MerkleMap.class);

        assertThatThrownBy(() -> subject.setFromState(mMap)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void stateSetterDontThrow() {
        final var registry = mock(SchemaRegistry.class);
        // registerSchemas(...) is required to instantiate the token schema
        subject.registerSchemas(registry, SemanticVersion.DEFAULT);

        final var mMap = mock(MerkleMap.class);

        subject.setFromState(null);
        subject.setFromState(mMap);
    }
}
