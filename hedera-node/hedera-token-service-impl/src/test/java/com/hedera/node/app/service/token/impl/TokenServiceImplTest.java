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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.CryptoServiceDefinition;
import com.hedera.node.app.service.token.TokenServiceDefinition;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.impl.schemas.V0500TokenSchema;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenServiceImplTest {

    private TokenServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new TokenServiceImpl();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void constructorNullArgsThrow() {
        Assertions.assertThatThrownBy(() -> new TokenServiceImpl(
                        null,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> new TokenServiceImpl(
                        Collections::emptySortedSet,
                        null,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> new TokenServiceImpl(
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        null,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> new TokenServiceImpl(
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        null,
                        Collections::emptySortedSet))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> new TokenServiceImpl(
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        Collections::emptySortedSet,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defaultConstructor() {
        assertThat(new TokenServiceImpl()).isNotNull();
    }

    @Test
    void argConstructor() {
        final SortedSet<Account> anyNonNullAccts = new TreeSet<>(ACCOUNT_COMPARATOR);
        final var acc1 = mock(Account.class);
        final var acc2 = mock(Account.class);
        final var acc3 = mock(Account.class);
        anyNonNullAccts.addAll(Set.of(acc1, acc2, acc3));

        assertThat(new TokenServiceImpl(
                        () -> anyNonNullAccts,
                        () -> anyNonNullAccts,
                        () -> anyNonNullAccts,
                        () -> anyNonNullAccts,
                        () -> anyNonNullAccts))
                .isNotNull();
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
        verify(schemaRegistry, times(2)).register(captor.capture());
        final var schemas = captor.getAllValues();
        assertThat(schemas).hasSize(2);
        assertThat(schemas.getFirst()).isInstanceOf(V0490TokenSchema.class);
        assertThat(schemas.getLast()).isInstanceOf(V0500TokenSchema.class);
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
