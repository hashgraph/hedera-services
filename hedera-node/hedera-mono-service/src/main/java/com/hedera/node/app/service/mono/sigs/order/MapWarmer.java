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
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
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

    // Note: these suppliers need to be stored here as they are (instead of storing what
    // the supplier references) in order for this class to get the latest copies of the
    // underlying data structures
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

    // Intentionally keeping this around in case it's needed
    @SuppressWarnings("unused")
    public void warmAccount(EntityNumVirtualKey accountId) {
        if (accountsStorageAdapter.get().areOnDisk()) {
            submitToThreadpool(accountsStorageAdapter.get().getOnDiskAccounts(), accountId);
        }
    }

    // "warmTokenObjs" as in "warm the token and its associated token relation objects"
    public void warmTokenObjs(long tokenNum, long serialNum) {
        if (nftsAdapter.get().isVirtual() && tokenRelsAdapter.get().areOnDisk()) {
            threadpool.execute(newTokenRunnable(tokenNum, serialNum));
        }
    }

    private Runnable newTokenRunnable(long tokenNum, long serialNum) {
        return () -> {
            // NFT:
            final var tokenKey = UniqueTokenKey.from(NftNumPair.fromLongs(tokenNum, serialNum));
            final var nftMap = nftsAdapter.get().getOnDiskNfts();
            if (nftMap != null) {
                nftMap.warm(tokenKey);
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on token key {}", tokenKey);
            }

            // tokenRel:
            final var tokenRelKey =
                    EntityNumVirtualKey.fromPair(EntityNumPair.fromLongs(tokenNum, serialNum));
            final var tokenRelMap = tokenRelsAdapter.get().getOnDiskRels();
            if (tokenRelMap != null) {
                final var hederaTokenRel = tokenRelMap.get(tokenRelKey);
                if (hederaTokenRel != null) {
                    tokenRelMap.warm(
                            EntityNumVirtualKey.from(EntityNum.fromLong(hederaTokenRel.getPrev())));
                    tokenRelMap.warm(
                            EntityNumVirtualKey.from(EntityNum.fromLong(hederaTokenRel.getNext())));
                }
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on tokenRel key {}", tokenRelKey);
            }
        };
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
