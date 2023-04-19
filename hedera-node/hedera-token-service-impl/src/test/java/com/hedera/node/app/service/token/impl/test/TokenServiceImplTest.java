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

package com.hedera.node.app.service.token.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    @Test
    void registersExpectedSchema() {
        final ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        final var subject = new TokenServiceImpl();

        subject.registerSchemas(registry);
        verify(registry).register(schemaCaptor.capture());

        final var schema = schemaCaptor.getValue();

        final var statesToCreate = schema.statesToCreate();
        assertEquals(6, statesToCreate.size());
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(TokenServiceImpl.ACCOUNTS_KEY, iter.next());
        assertEquals(TokenServiceImpl.ALIASES_KEY, iter.next());
        assertEquals(TokenServiceImpl.NFTS_KEY, iter.next());
        assertEquals(TokenServiceImpl.PAYER_RECORDS_KEY, iter.next());
        assertEquals(TokenServiceImpl.TOKENS_KEY, iter.next());
        assertEquals(TokenServiceImpl.TOKEN_RELS_KEY, iter.next());
    }
}
