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
package com.hedera.node.app.service.mono.sigs.order;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapWarmer {

    private static MapWarmer INSTANCE = null;

    public static MapWarmer getInstance(
            Supplier<AccountStorageAdapter> accountsStorageAdapter,
            Supplier<UniqueTokenMapAdapter> nftsAdapter,
            Supplier<TokenRelStorageAdapter> tokenRelsAdapter,
            GlobalDynamicProperties globalDynamicProperties) {
        if (INSTANCE == null) {
            INSTANCE =
                    new MapWarmer(
                            accountsStorageAdapter,
                            nftsAdapter,
                            tokenRelsAdapter,
                            globalDynamicProperties);
        }
        return INSTANCE;
    }

    private static final Logger log = LoggerFactory.getLogger(MapWarmer.class);

    private VirtualMap<EntityNumVirtualKey, OnDiskAccount> accounts = null;
    private VirtualMap<UniqueTokenKey, UniqueTokenValue> nfts = null;
    private VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> tokenRels = null;

    private final Supplier<AccountStorageAdapter> accountsStorageAdapter;
    private final Supplier<UniqueTokenMapAdapter> nftsAdapter;
    private final Supplier<TokenRelStorageAdapter> tokenRelsAdapter;

    private final Executor threadpool;

    private MapWarmer(
            Supplier<AccountStorageAdapter> accountsStorageAdapter,
            Supplier<UniqueTokenMapAdapter> nftsAdapter,
            Supplier<TokenRelStorageAdapter> tokenRelsAdapter,
            GlobalDynamicProperties globalDynamicProperties) {
        this.accountsStorageAdapter = accountsStorageAdapter;
        this.nftsAdapter = nftsAdapter;
        this.tokenRelsAdapter = tokenRelsAdapter;
        this.threadpool =
                Executors.newFixedThreadPool(globalDynamicProperties.cryptoTransferWarmThreads());
    }

    public void warmAccount(EntityNumVirtualKey accountId) {
        if (accounts == null) {
            var accountsStorage = accountsStorageAdapter.get();
            accounts = accountsStorage.areOnDisk() ? accountsStorage.getOnDiskAccounts() : null;
        }
        submitToThreadpool(accounts, accountId);
    }

    public void warmNft(UniqueTokenKey nftId) {
        if (nfts == null) {
            var nftsStorage = nftsAdapter.get();
            nfts = nftsStorage.isVirtual() ? nftsStorage.virtualMap() : null;
        }
        submitToThreadpool(nfts, nftId);
    }

    public void warmTokenRel(EntityNumVirtualKey tokenRelKey) {
        if (tokenRels == null) {
            var tokenRelStorage = tokenRelsAdapter.get();
            tokenRels = tokenRelStorage.areOnDisk() ? tokenRelStorage.getOnDiskRels() : null;
        }
        submitToThreadpool(tokenRels, tokenRelKey);
    }

    private <T extends VirtualKey<? super T>> void submitToThreadpool(
            VirtualMap<T, ?> vmap, T param) {
        if (vmap != null) {
            threadpool.execute(() -> vmap.warm(param));
        } else if (log.isDebugEnabled()) {
            log.debug("no-op 'warm' called on param {}", param);
        }
    }
}
