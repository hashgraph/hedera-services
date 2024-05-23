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
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.token.CryptoServiceDefinition;
import com.hedera.node.app.service.token.TokenServiceDefinition;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        Assertions.assertThat(new TokenServiceImpl()).isNotNull();
    }

    @Test
    void argConstructor() {
        final SortedSet<Account> anyNonNullAccts = new TreeSet<>(ACCOUNT_COMPARATOR);
        final var acc1 = mock(Account.class);
        final var acc2 = mock(Account.class);
        final var acc3 = mock(Account.class);
        anyNonNullAccts.addAll(Set.of(acc1, acc2, acc3));

        Assertions.assertThat(new TokenServiceImpl(
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
        Assertions.assertThatThrownBy(() -> subject.registerSchemas(null, SemanticVersion.DEFAULT))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> subject.registerSchemas(mock(SchemaRegistry.class), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerSchemasRegistersTokenSchema() {
        final var schemaRegistry = mock(SchemaRegistry.class);

        subject.registerSchemas(schemaRegistry, SemanticVersion.DEFAULT);
        verify(schemaRegistry).register(notNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    void triesToSetStateWithoutRegisteredTokenSchema() {
        final var vMap = mock(VirtualMap.class);
        final var mMap = mock(MerkleMap.class);

        Assertions.assertThatThrownBy(() -> subject.setNftsFromState(vMap)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> subject.setTokenRelsFromState(vMap))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> subject.setAcctsFromState(vMap)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> subject.setTokensFromState(mMap)).isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() -> subject.setStakingFs(mMap, mock(MerkleNetworkContext.class)))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void stateSettersDontThrow() {
        final var registry = mock(SchemaRegistry.class);
        // registerSchemas(...) is required to instantiate the token schema
        subject.registerSchemas(registry, SemanticVersion.DEFAULT);

        final var vmap = mock(VirtualMap.class);
        final var mMap = mock(MerkleMap.class);
        final var netCtx = mock(MerkleNetworkContext.class);

        subject.setNftsFromState(null);
        subject.setNftsFromState(vmap);

        subject.setTokenRelsFromState(null);
        subject.setTokenRelsFromState(vmap);

        subject.setAcctsFromState(null);
        subject.setAcctsFromState(vmap);

        subject.setTokensFromState(null);
        subject.setTokensFromState(mMap);

        subject.setStakingFs(null, null);
        subject.setStakingFs(mMap, null);
        subject.setStakingFs(null, netCtx);
        subject.setStakingFs(mMap, netCtx);
    }

    @Test
    void verifyServiceName() {
        Assertions.assertThat(subject.getServiceName()).isEqualTo("TokenService");
    }

    @Test
    void rpcDefinitions() {
        Assertions.assertThat(subject.rpcDefinitions())
                .containsExactlyInAnyOrder(CryptoServiceDefinition.INSTANCE, TokenServiceDefinition.INSTANCE);
    }
}
