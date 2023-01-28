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
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Optional;
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

    // "warmTokenObjs" as in "warm the token and its associated token relation objects"
    // for both the sending and receiving accounts
    public void warmTokenObjs(
            AccountID sender, AccountID receiver, long tokenNum, long senderSerialNum) {
        if (accountsStorageAdapter.get().areOnDisk()
                && nftsAdapter.get().isVirtual()
                && tokenRelsAdapter.get().areOnDisk()) {
            threadpool.execute(newTokenRunnable(sender, receiver, tokenNum, senderSerialNum));
        } else if (log.isDebugEnabled()) {
            log.debug(
                    "no-op 'warm' not called on any token objects due to non-matching config:"
                            + " accounts on disk = {}, nfts on disk = {}, tokenRels on disk = {}",
                    accountsStorageAdapter.get().areOnDisk(),
                    nftsAdapter.get().isVirtual(),
                    tokenRelsAdapter.get().areOnDisk());
        }
    }

    private Runnable newTokenRunnable(
            AccountID sender, AccountID receiver, long tokenNum, long senderSerialNum) {
        return () -> {
            // Sending Account:
            final var senderAcctKey = EntityNumVirtualKey.from(EntityNum.fromAccountId(sender));
            final var acctMap = accountsStorageAdapter.get().getOnDiskAccounts();
            if (acctMap != null) {
                acctMap.warm(senderAcctKey);
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on sender account key {}", senderAcctKey);
            }

            // Receiving Account:
            final var receiverAcctKey = EntityNumVirtualKey.from(EntityNum.fromAccountId(receiver));
            if (acctMap != null) {
                acctMap.warm(receiverAcctKey);
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on receiver account key {}", receiverAcctKey);
            }

            // NFT:
            final var nftKey = UniqueTokenKey.from(NftNumPair.fromLongs(tokenNum, senderSerialNum));
            final var nftMap = nftsAdapter.get().getOnDiskNfts();
            final var nftValue =
                    Optional.ofNullable(nftMap).map(vmap -> vmap.get(nftKey)).orElse(null);
            if (nftValue != null) {
                nftMap.warm(
                        UniqueTokenKey.from(nftValue.getPrev().asEntityNumPair().asNftNumPair()));
                nftMap.warm(
                        UniqueTokenKey.from(nftValue.getNext().asEntityNumPair().asNftNumPair()));
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on unique token key {}", nftKey);
            }

            // Sender TokenRel:
            final var senderTokenRelKey =
                    EntityNumVirtualKey.fromPair(
                            EntityNumPair.fromLongs(sender.getAccountNum(), tokenNum));
            final var tokenRelMap = tokenRelsAdapter.get().getOnDiskRels();
            final var senderTokenRel =
                    Optional.ofNullable(tokenRelMap)
                            .map(vmap -> vmap.get(senderTokenRelKey))
                            .orElse(null);
            if (senderTokenRel != null) {
                tokenRelMap.warm(
                        EntityNumVirtualKey.from(EntityNum.fromLong(senderTokenRel.getPrev())));
                tokenRelMap.warm(
                        EntityNumVirtualKey.from(EntityNum.fromLong(senderTokenRel.getNext())));
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on tokenRel sender key {}", senderTokenRelKey);
            }

            // Receiver TokenRel:
            final var receiverTokenRelKey =
                    EntityNumVirtualKey.fromPair(
                            EntityNumPair.fromLongs(receiver.getAccountNum(), tokenNum));
            final var receiverTokenRel =
                    Optional.ofNullable(tokenRelMap)
                            .map(vmap -> vmap.get(receiverTokenRelKey))
                            .orElse(null);
            if (receiverTokenRel != null) {
                tokenRelMap.warm(
                        EntityNumVirtualKey.from(EntityNum.fromLong(receiverTokenRel.getPrev())));
                tokenRelMap.warm(
                        EntityNumVirtualKey.from(EntityNum.fromLong(receiverTokenRel.getNext())));
            } else if (log.isDebugEnabled()) {
                log.debug("no-op 'warm' called on tokenRel receiver key {}", receiverTokenRelKey);
            }
        };
    }
}
