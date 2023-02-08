/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store;

import static com.hedera.node.app.spi.config.PropertyNames.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.ledger.interceptors.UniqueTokensLinkManager;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.swirlds.jasperdb.JasperDbBuilder;
import org.junit.jupiter.api.Test;

class StoresModuleTest {
    @Test
    void testTransactionalLedgerWhenVirtualNftsEnabled() {
        final var bootstrapProperties = mock(BootstrapProperties.class);
        final var usageLimits = mock(UsageLimits.class);
        final var uniqueTokensLinkManager = mock(UniqueTokensLinkManager.class);
        given(bootstrapProperties.getBooleanProperty(TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        final var virtualMap =
                new VirtualMapFactory(JasperDbBuilder::new).newVirtualizedUniqueTokenStorage();
        final var transactionalLedger =
                StoresModule.provideNftsLedger(
                        bootstrapProperties,
                        usageLimits,
                        uniqueTokensLinkManager,
                        () -> UniqueTokenMapAdapter.wrap(virtualMap));
        transactionalLedger.begin();
        final var nftId = NftId.withDefaultShardRealm(3, 4);
        final var token = UniqueTokenAdapter.newEmptyVirtualToken();
        transactionalLedger.put(nftId, token);
        transactionalLedger.commit();
        assertEquals(token, transactionalLedger.getImmutableRef(nftId));
    }

    @Test
    void picksOnDiskAccountWhenOnDiskIsTrue() {
        final var bootstrapProperties = mock(BootstrapProperties.class);
        given(bootstrapProperties.getBooleanProperty(ACCOUNTS_STORE_ON_DISK)).willReturn(true);
        final var subject = StoresModule.provideAccountSupplier(bootstrapProperties);
        assertInstanceOf(OnDiskAccount.class, subject.get());
    }

    @Test
    void picksMerkleAccountWhenOnDiskIsFalse() {
        final var bootstrapProperties = mock(BootstrapProperties.class);
        final var subject = StoresModule.provideAccountSupplier(bootstrapProperties);
        assertInstanceOf(MerkleAccount.class, subject.get());
    }

    @Test
    void picksOnDiskRelWhenOnDiskIsTrue() {
        final var bootstrapProperties = mock(BootstrapProperties.class);
        given(bootstrapProperties.getBooleanProperty(TOKENS_STORE_RELS_ON_DISK)).willReturn(true);
        final var subject = StoresModule.provideTokenRelSupplier(bootstrapProperties);
        assertInstanceOf(OnDiskTokenRel.class, subject.get());
    }

    @Test
    void picksMerkleRelWhenOnDiskIsFalse() {
        final var bootstrapProperties = mock(BootstrapProperties.class);
        final var subject = StoresModule.provideTokenRelSupplier(bootstrapProperties);
        assertInstanceOf(MerkleTokenRelStatus.class, subject.get());
    }
}
