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

package com.hedera.node.app.service.addressbook.impl.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema;
import com.hedera.node.app.service.addressbook.impl.schemas.V056AddressBookSchema;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AddressBookServiceImplTest {
    private AddressBookServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new AddressBookServiceImpl();
    }

    @Test
    void registerSchemasNullArgsThrow() {
        assertThatThrownBy(() -> subject.registerSchemas(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registersExpectedSchema() {
        final var schemaRegistry = mock(SchemaRegistry.class);
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);
        subject.registerSchemas(schemaRegistry);
        verify(schemaRegistry, times(2)).register(schemaCaptor.capture());
        final var schemas = schemaCaptor.getAllValues();
        assertThat(schemas).hasSize(2);
        assertThat(schemas.getFirst()).isInstanceOf(V053AddressBookSchema.class);
        assertThat(schemas.get(1)).isInstanceOf(V056AddressBookSchema.class);
    }
}
