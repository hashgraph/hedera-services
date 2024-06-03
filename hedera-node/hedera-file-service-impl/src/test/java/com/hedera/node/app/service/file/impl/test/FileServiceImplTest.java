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

package com.hedera.node.app.service.file.impl.test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.file.impl.schemas.InitialModFileGenesisSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.config.ConfigProvider;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    private ConfigProvider configProvider;

    @BeforeEach
    void setUp() {
        configProvider = new BootstrapConfigProviderImpl();
    }

    @Test
    void registersExpectedSchema() {
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        subject().registerSchemas(registry, TestSchema.CURRENT_VERSION);

        verify(registry).register(schemaCaptor.capture());

        final var schema = schemaCaptor.getValue();

        final var statesToCreate = schema.statesToCreate();
        assertThat(11).isEqualTo(statesToCreate.size());
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertThat(FileServiceImpl.BLOBS_KEY).isEqualTo(iter.next());
    }

    @Test
    void testSetFs() throws NoSuchFieldException, IllegalAccessException {
        FileServiceImpl fileService = new FileServiceImpl(configProvider);
        fileService.registerSchemas(registry, TestSchema.CURRENT_VERSION);
        Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> mockSupplier = mock(Supplier.class);
        InitialModFileGenesisSchema mockInitialFileSchema = mock(InitialModFileGenesisSchema.class);

        Field field = FileServiceImpl.class.getDeclaredField("initialFileSchema");
        field.setAccessible(true);
        field.set(fileService, mockInitialFileSchema);

        fileService.setFs(mockSupplier);

        verify(mockInitialFileSchema).setFs(mockSupplier);
    }

    private FileService subject() {
        return new FileServiceImpl(configProvider);
    }
}
