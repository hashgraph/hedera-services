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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bouncycastle.util.encoders.Hex;
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
            INSTANCE = new MapWarmer(accountsStorageAdapter, nftsAdapter, tokenRelsAdapter, globalDynamicProperties);
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

    private final ThreadPoolExecutor threadpool;

    private MapWarmer(
            Supplier<AccountStorageAdapter> accountsStorageAdapter,
            Supplier<UniqueTokenMapAdapter> nftsAdapter,
            Supplier<TokenRelStorageAdapter> tokenRelsAdapter,
            GlobalDynamicProperties globalDynamicProperties) {
        this.accountsStorageAdapter = accountsStorageAdapter;
        this.nftsAdapter = nftsAdapter;
        this.tokenRelsAdapter = tokenRelsAdapter;
        this.threadpool = new ThreadPoolExecutor(
                globalDynamicProperties.cryptoTransferWarmThreads(),
                globalDynamicProperties.cryptoTransferWarmThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
    }

    public void initiateCacheWarmup(Round round) {
        threadpool.execute(new RoundLabeledRunnable(round, this));
    }

    // "warmTokenObjs" as in "warm the token and its associated token relation objects"
    // for both the sending and receiving accounts
    private void warmTokenObjs(AccountID sender, AccountID receiver, long tokenNum, long senderSerialNum) {
        if (accountsStorageAdapter.get().areOnDisk()
                && nftsAdapter.get().isVirtual()
                && tokenRelsAdapter.get().areOnDisk()) {

            threadpool.execute(() -> {
                // Sender:
                final var acctMap = accountsStorageAdapter.get().getOnDiskAccounts();
                final OnDiskAccount account = Objects.requireNonNull(acctMap).get(EntityNumVirtualKey.from(EntityNum.fromAccountId(sender)));
                if (account != null) {
                    final var headNum = account.getHeadNftTokenNum();
                    if (headNum != 0) {
                        final var headSerialNum = account.getHeadNftSerialNum();
                        final var nftMap = nftsAdapter.get().getOnDiskNfts();
                        Objects.requireNonNull(nftMap).warm(new UniqueTokenKey(headNum, headSerialNum));
                    }
                }
            });

            threadpool.execute(() -> {
                // Receiver:
                final var acctMap = accountsStorageAdapter.get().getOnDiskAccounts();
                final OnDiskAccount account = Objects.requireNonNull(acctMap).get(EntityNumVirtualKey.from(EntityNum.fromAccountId(receiver)));
                if (account != null) {
                    final var headTokenId = account.getHeadTokenId();
                    if (headTokenId != 0) {
                        final var tokenRelMap = tokenRelsAdapter.get().getOnDiskRels();
                        Objects.requireNonNull(tokenRelMap).warm(EntityNumVirtualKey.fromPair(EntityNumPair.fromLongs(receiver.getAccountNum(), headTokenId)));
                    }

                    final var headNum = account.getHeadNftTokenNum();
                    if (headNum != 0) {
                        final var headSerialNum = account.getHeadNftSerialNum();
                        final var nftMap = nftsAdapter.get().getOnDiskNfts();
                        Objects.requireNonNull(nftMap).warm(new UniqueTokenKey(headNum, headSerialNum));
                    }
                }
            });

            threadpool.execute(() -> {
                // NFT:
                final var nftKey = new UniqueTokenKey(tokenNum, senderSerialNum);
                final var nftMap = nftsAdapter.get().getOnDiskNfts();
                Objects.requireNonNull(nftMap).warm(nftKey);
            });

            threadpool.execute(() -> {
                // Sender TokenRel:
                final var senderTokenRelKey =
                        EntityNumVirtualKey.fromPair(EntityNumPair.fromLongs(sender.getAccountNum(), tokenNum));
                final var tokenRelMap = tokenRelsAdapter.get().getOnDiskRels();
                Objects.requireNonNull(tokenRelMap).warm(senderTokenRelKey);
            });

            threadpool.execute(() -> {
                // Receiver TokenRel:
                final var receiverTokenRelKey =
                        EntityNumVirtualKey.fromPair(EntityNumPair.fromLongs(receiver.getAccountNum(), tokenNum));
                final var tokenRelMap = tokenRelsAdapter.get().getOnDiskRels();
                Objects.requireNonNull(tokenRelMap).warm(receiverTokenRelKey);
            });
        } else if (log.isDebugEnabled()) {
            log.debug(
                    "no-op 'warm' not called on any token objects due to non-matching config:"
                            + " accounts on disk = {}, nfts on disk = {}, tokenRels on disk = {}",
                    accountsStorageAdapter.get().areOnDisk(),
                    nftsAdapter.get().isVirtual(),
                    tokenRelsAdapter.get().areOnDisk());
        }
    }

    private void warmAccount(AccountID account) {
        if (accountsStorageAdapter.get().areOnDisk()) {
            threadpool.execute(() -> {
                final var acctMap = accountsStorageAdapter.get().getOnDiskAccounts();
                Objects.requireNonNull(acctMap).warm(EntityNumVirtualKey.from(EntityNum.fromAccountId(account)));
            });
        }
    }

    public void cancelPendingWarmups(long roundNum) {
        threadpool
                .getQueue()
                .removeIf(queuedObj -> (queuedObj instanceof RoundLabeledRunnable)
                        && ((RoundLabeledRunnable) queuedObj).getRound() == roundNum);
    }

    private void doWarmups(Round round) {
        if (round == null) {
            log.warn("Round is null, skipping cache warmup");
            return;
        }

        for (final ConsensusEvent event : round) {
            event.forEachTransaction(txn -> {
                final PlatformTxnAccessor txnAccess;
                final TransactionBody txnBody;
                try {
                    txnAccess = PlatformTxnAccessor.from(txn.getContents());
                    txnBody = txnAccess.getTxn();
                } catch (InvalidProtocolBufferException e) {
                    log.error("Unable to parse transaction: "
                            + e.getMessage()
                            + "\nErrant Txn: ["
                            + Hex.toHexString(txn.getContents())
                            + "]");
                    return;
                }

                if (!txnBody.hasCryptoTransfer()) {
                    log.debug("No crypto transfer in transaction (txn size " + txn.getSize() + ")");
                    return;
                }

                final var cryptoTransfer = txnBody.getCryptoTransfer();
                TransferList transferList = cryptoTransfer.getTransfers();
                for (final AccountAmount amount : transferList.getAccountAmountsList()) {
                    warmAccount(amount.getAccountID());
                }
                for (final TokenTransferList transfers : cryptoTransfer.getTokenTransfersList()) {
                    for (final NftTransfer adjustment : transfers.getNftTransfersList()) {
                        warmTokenObjs(
                                adjustment.getSenderAccountID(),
                                adjustment.getReceiverAccountID(),
                                transfers.getToken().getTokenNum(),
                                adjustment.getSerialNumber());
                    }
                }
            });
        }
    }

    // This class represents a runnable associated with a specific round, indicated by the round
    // number. This is done so we can cancel pending warmups for a specific round instead of purging
    // the queue entirely. TODO: is this even needed?
    private record RoundLabeledRunnable(Round round, MapWarmer mapWarmer) implements Runnable {
        long getRound() {
            return round.getRoundNum();
        }

        @Override
        public void run() {
            mapWarmer.doWarmups(round);
        }
    }
}
