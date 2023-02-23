/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.test.utils.SemVerUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private SchemaRegistry registry;

    @Test
    void registersExpectedSchema() {
        final var captor = ArgumentCaptor.forClass(Schema.class);

        subject().registerSchemas(registry);

        Mockito.verify(registry).register(captor.capture());
        final var schema = captor.getValue();

        assertEquals(SemVerUtils.standardSemverWith(0, 34, 0), schema.getVersion());
        assertTrue(schema.statesToRemove().isEmpty());
        final var requestedStates = schema.statesToCreate();
        assertEquals(1, requestedStates.size());
        final var legacyBlobsDef = requestedStates.iterator().next();
        assertEquals("BLOBS", legacyBlobsDef.stateKey());
    }

    private FileService subject() {
        return new FileServiceImpl();
    }
}
