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

package com.hedera.node.app.service.contract.impl.test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.service.contract.impl.schemas.V0500ContractSchema;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContractServiceImplTest {
    private final InstantSource instantSource = InstantSource.system();

    private final ContractServiceImpl subject = new ContractServiceImpl(instantSource);

    @Test
    void handlersAreAvailable() {
        assertNotNull(subject.handlers());
    }

    @Test
    void registersContractSchema() {
        final var captor = ArgumentCaptor.forClass(Schema.class);
        final var mockRegistry = mock(SchemaRegistry.class);
        subject.registerSchemas(mockRegistry);
        verify(mockRegistry, times(2)).register(captor.capture());
        final var schemas = captor.getAllValues();
        assertInstanceOf(V0490ContractSchema.class, schemas.getFirst());
        assertInstanceOf(V0500ContractSchema.class, schemas.getLast());
    }
}
