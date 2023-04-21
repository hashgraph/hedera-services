/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.cache;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class EntityMapWarmerTest {
    private static final AccountID HBAR_TRANSFER_ACCOUNT_1 =
            AccountID.newBuilder().setAccountNum(1).build();
    // Sender NFTs:
    private static final long NFT_TOKEN_ID_1000 = 1000;
    private static final long NFT_TOKEN_NUM_1000 = NFT_TOKEN_ID_1000;
    private static final long NFT_TOKEN_1000_SERIAL0 = 0L;
    private static final long NFT_TOKEN_1000_SERIAL1 = 1L;
    private static final long NFT_TOKEN_1000_SERIAL2 = 2L;
    // Receiver NFT:
    private static final long NFT_TOKEN_ID_2000 = 2000;
    private static final long NFT_TOKEN_NUM_2000 = NFT_TOKEN_ID_2000;
    private static final long NFT_TOKEN_2000_SERIAL3 = 3L;
    private final AccountID NFT_SENDER_ACCOUNT_123 =
            AccountID.newBuilder().setAccountNum(123).build();
    private final AccountID NFT_RECEIVER_ACCOUNT_456 =
            AccountID.newBuilder().setAccountNum(456).build();

    @Mock
    private AccountStorageAdapter accountAdpt;

    private final Supplier<AccountStorageAdapter> accountAdptSupplier = () -> accountAdpt;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private UniqueTokenMapAdapter nftAdpt;

    private final Supplier<UniqueTokenMapAdapter> nftAdptSupplier = () -> nftAdpt;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TokenRelStorageAdapter tokenRelAdpt;

    private final Supplier<TokenRelStorageAdapter> tokenRelAdptSupplier = () -> tokenRelAdpt;

    @Test
    void getInstanceReturnsSameReference() {
        // given:
        final var globalProps = mock(GlobalDynamicProperties.class);
        lenient().when(globalProps.cacheCryptoTransferWarmThreads()).thenReturn(3); // Any number > 0

        // when:
        final var firstInstance =
                EntityMapWarmer.getInstance(accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, globalProps);
        final var secondInstance =
                EntityMapWarmer.getInstance(accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, globalProps);

        // then:
        Assertions.assertThat(firstInstance).isSameAs(secondInstance);
    }

    @Test
    void warmOnNullRoundDoesNotWarm() {
        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(null);

        // then:
        verifyNoCacheWarming();
    }

    @Test
    void cancelOnRoundNotPresentDoesNoWarming() {
        // given:
        final var pastRoundNum = 5;

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.cancelPendingWarmups(pastRoundNum);

        // then:
        verifyNoCacheWarming();
    }

    @Test
    void cancelOnBogusRoundNumDoesNoWarming() {
        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.cancelPendingWarmups(-1);

        // then:
        verifyNoCacheWarming();
    }

    @Test
    void cancelOnlyPurgesGivenRoundNumber() throws InterruptedException {
        // This test enqueues three mock rounds, each with its own round number, cancels any
        // pending warmups for a given round number, and then verifies that only the round
        // runnable with the given round number was purged

        // given:
        final var workQueue = new LinkedBlockingQueue<Runnable>();
        final var threadpool = new RecursiveSynchronousThreadpoolDouble(workQueue);
        final var subject = new EntityMapWarmer(accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, threadpool);
        final var mockRound1 = mock(Round.class);
        given(mockRound1.getRoundNum()).willReturn(1L);
        workQueue.put(new EntityMapWarmer.RoundLabeledRunnable(mockRound1, subject));
        final var mockRound2 = mock(Round.class);
        given(mockRound2.getRoundNum()).willReturn(2L);
        workQueue.put(new EntityMapWarmer.RoundLabeledRunnable(mockRound2, subject));
        final var mockRound3 = mock(Round.class);
        given(mockRound3.getRoundNum()).willReturn(3L);
        workQueue.put(new EntityMapWarmer.RoundLabeledRunnable(mockRound3, subject));

        // when:
        subject.cancelPendingWarmups(1);

        // then:
        final var first = (EntityMapWarmer.RoundLabeledRunnable) workQueue.poll();
        Assertions.assertThat(first).isNotNull();
        Assertions.assertThat(first.getRound()).isEqualTo(2);
        final var second = (EntityMapWarmer.RoundLabeledRunnable) workQueue.poll();
        Assertions.assertThat(second).isNotNull();
        Assertions.assertThat(second.getRound()).isEqualTo(3);
        final var last = workQueue.poll();
        Assertions.assertThat(last).isNull();
    }

    @Test
    void acctStorageNotOnDiskDoesNotWarmAccounts() {
        // given:
        given(accountAdpt.areOnDisk()).willReturn(false);
        given(nftAdpt.isVirtual()).willReturn(true);
        given(tokenRelAdpt.areOnDisk()).willReturn(true);

        // when:
        final var noOnDiskAcctsInstance = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        noOnDiskAcctsInstance.warmCache(mockRoundWithTxn());

        // then:
        verify(accountAdpt, atLeast(1)).areOnDisk();
        verify(accountAdpt, never()).getOnDiskAccounts();
    }

    @Test
    void nftStorageNotOnDiskDoesNotWarmNfts() {
        // given:
        given(accountAdpt.areOnDisk()).willReturn(true);
        given(accountAdpt.getOnDiskAccounts()).willReturn(VirtualMapLike.from(mock(VirtualMap.class)));
        given(nftAdpt.isVirtual()).willReturn(false);
        given(tokenRelAdpt.areOnDisk()).willReturn(true);

        // when:
        final var noOnDiskNftsInstance = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        noOnDiskNftsInstance.warmCache(mockRoundWithTxn());

        // then:
        verify(nftAdpt, atLeast(1)).isVirtual();
        verify(nftAdpt, never()).getOnDiskNfts();
    }

    @Test
    void tokenRelNotOnDiskDoesNotWarmTokenRels() {
        // given:
        given(accountAdpt.areOnDisk()).willReturn(true);
        given(accountAdpt.getOnDiskAccounts()).willReturn(VirtualMapLike.from(mock(VirtualMap.class)));
        given(nftAdpt.isVirtual()).willReturn(true);
        given(nftAdpt.getOnDiskNfts()).willReturn(VirtualMapLike.from(mock(VirtualMap.class)));
        given(tokenRelAdpt.areOnDisk()).willReturn(false);

        // when:
        final var noOnDiskTokenRelsInstance = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        noOnDiskTokenRelsInstance.warmCache(mockRoundWithTxn());

        // then:
        verify(tokenRelAdpt, atLeast(1)).areOnDisk();
        verify(tokenRelAdpt, never()).getOnDiskRels();
    }

    @Test
    void txnWithoutCryptoTransferDoesNotWarm() {
        // given:
        final var nonTransferTxn = newNonCryptoTransferTxn();
        final var multiTxnRound = mockRoundWithTxns(new SwirldTransaction(nonTransferTxn));

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(multiTxnRound);

        // then:
        verify(accountAdpt, never()).areOnDisk();
        verify(nftAdpt, never()).isVirtual();
        verify(tokenRelAdpt, never()).areOnDisk();
        verifyNoCacheWarming();
    }

    @Test
    void warmExistingSenderAccount() {
        // given:
        simulateAllEntitiesOnDiskEnabled();
        // We'll use this method to return a non-null mock we can call warm() on, but we don't
        // care about the actual results of the warm() call for these NFTs. Mostly useful so
        // there are no unexpected null pointer exceptions. This also holds true for the virtual
        // maps in a number of other tests throughout this class
        final var nfts = simulateOnDiskNfts();
        simulateOnDiskTokenRels();

        // We may want to replace the mocks of data classes with actual objects (e.g. actual
        // OnDiskAccount and VirtualMap objects). Not done here because it's time-consuming
        final var senderAcct = mock(OnDiskAccount.class);
        given(senderAcct.getHeadNftTokenNum()).willReturn(NFT_TOKEN_NUM_1000);
        given(senderAcct.getHeadNftSerialNum()).willReturn(NFT_TOKEN_1000_SERIAL0);
        final var accts = mock(VirtualMap.class);
        given(accts.get(EntityNumVirtualKey.fromLong(NFT_SENDER_ACCOUNT_123.getAccountNum())))
                .willReturn(senderAcct);
        simulateOnDiskAccounts(accts);

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(mockRoundWithTxn());

        // then:
        verify(accts).warm(EntityNumVirtualKey.fromLong(HBAR_TRANSFER_ACCOUNT_1.getAccountNum()));
        // Verify the call retrieves the sender account instead of warming it since we need to
        // pull info off of the sender account object. Since we're retrieving the sender account
        // in all cases, it makes no sense to warm it
        verify(accts).get(EntityNumVirtualKey.fromLong(NFT_SENDER_ACCOUNT_123.getAccountNum()));
        // The transaction is transferring NFT_TOKEN_1000_SERIAL1, but for this test case, the cache
        // should warm the sender's head NFT serial number, which is NFT_TOKEN_1000_SERIAL0
        verify(nfts).warm(new UniqueTokenKey(NFT_TOKEN_NUM_1000, NFT_TOKEN_1000_SERIAL0));
    }

    @Test
    void warmExistingReceiverAccount() {
        // given:
        simulateAllEntitiesOnDiskEnabled();
        final var nfts = simulateOnDiskNfts();
        final var tokenRels = simulateOnDiskTokenRels();

        final var receiverAcct = mock(OnDiskAccount.class);
        given(receiverAcct.getHeadTokenId()).willReturn(NFT_TOKEN_ID_2000);
        given(receiverAcct.getHeadNftTokenNum()).willReturn(NFT_TOKEN_NUM_2000);
        given(receiverAcct.getHeadNftSerialNum()).willReturn(NFT_TOKEN_2000_SERIAL3);
        final var accts = mock(VirtualMap.class);
        given(accts.get(EntityNumVirtualKey.fromLong(NFT_SENDER_ACCOUNT_123.getAccountNum())))
                .willReturn(null);
        given(accts.get(EntityNumVirtualKey.fromLong(NFT_RECEIVER_ACCOUNT_456.getAccountNum())))
                .willReturn(receiverAcct);
        simulateOnDiskAccounts(accts);

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(mockRoundWithTxn());

        // then:
        verify(accts).get(EntityNumVirtualKey.fromLong(NFT_RECEIVER_ACCOUNT_456.getAccountNum()));
        verify(nfts).warm(new UniqueTokenKey(NFT_TOKEN_NUM_2000, NFT_TOKEN_2000_SERIAL3));
        verify(tokenRels, atLeast(1))
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_RECEIVER_ACCOUNT_456.getAccountNum(), NFT_TOKEN_NUM_2000)));
    }

    @Test
    void warmExistingNft() {
        // given:
        simulateAllEntitiesOnDiskEnabled();
        simulateOnDiskAccounts();
        simulateOnDiskTokenRels();

        final var targetNft = mock(UniqueTokenValue.class);
        given(targetNft.getPrev()).willReturn(NftNumPair.fromLongs(NFT_TOKEN_NUM_1000, NFT_TOKEN_1000_SERIAL0));
        given(targetNft.getNext()).willReturn(NftNumPair.fromLongs(NFT_TOKEN_NUM_1000, NFT_TOKEN_1000_SERIAL2));
        final var nfts = mock(VirtualMap.class);
        given(nfts.get(new UniqueTokenKey(NFT_TOKEN_NUM_1000, NFT_TOKEN_1000_SERIAL1)))
                .willReturn(targetNft);
        simulateOnDiskNfts(nfts);

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(mockRoundWithTxn());

        // then:
        // Prev NFT:
        verify(nfts).warm(new UniqueTokenKey(NFT_TOKEN_NUM_1000, NFT_TOKEN_1000_SERIAL0));
        // Next NFT:
        verify(nfts).warm(new UniqueTokenKey(NFT_TOKEN_NUM_1000, NFT_TOKEN_1000_SERIAL2));
    }

    @Test
    void warmSenderTokenRel() {
        // given:
        simulateAllEntitiesOnDiskEnabled();
        simulateOnDiskAccounts();
        simulateOnDiskNfts();
        final var tokenRels = simulateOnDiskTokenRels();

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(mockRoundWithTxn());

        // then:
        verify(tokenRels)
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_SENDER_ACCOUNT_123.getAccountNum(), NFT_TOKEN_NUM_1000)));
    }

    @Test
    void warmReceiverTokenRel() {
        // given:
        simulateAllEntitiesOnDiskEnabled();
        simulateOnDiskAccounts();
        simulateOnDiskNfts();
        final var tokenRels = simulateOnDiskTokenRels();

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(mockRoundWithTxn());

        // then:
        verify(tokenRels)
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_RECEIVER_ACCOUNT_456.getAccountNum(), NFT_TOKEN_NUM_1000)));
    }

    @Test
    void warmForMultipleTransactions() {
        // This test checks that single token transfers in two separate transactions are
        // all warmed correctly

        // given:
        simulateAllEntitiesOnDiskEnabled();
        simulateOnDiskAccounts();
        simulateOnDiskNfts();
        final var tokenRels = simulateOnDiskTokenRels();

        final var txn1 = newTransferTxn();
        final var txn2TokenTransfer = newReceiverSendsNftTokenTransfer();
        final var txn2 = newTransferTxn(txn2TokenTransfer);
        final var multiTxnRound = mockRoundWithTxns(new SwirldTransaction(txn1), new SwirldTransaction(txn2));

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(multiTxnRound);

        // then:
        // In this test, we use tokenRels warming as the verification because the token rels are
        // the final entities warmed by the implementation. If these are warmed successfully,
        // then it's reasonable to assume that the warming code has run as expected for both
        // transactions
        // verify txn1's warming:
        verify(tokenRels)
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_RECEIVER_ACCOUNT_456.getAccountNum(), NFT_TOKEN_NUM_1000)));
        // verify txn2's warming:
        verify(tokenRels)
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_SENDER_ACCOUNT_123.getAccountNum(), NFT_TOKEN_NUM_2000)));
    }

    @Test
    void warmForMultipleTransfers() {
        // This test checks that multiple transfers in a SINGLE transaction are all warmed correctly

        // given:
        simulateAllEntitiesOnDiskEnabled();
        simulateOnDiskAccounts();
        simulateOnDiskNfts();
        final var tokenRels = simulateOnDiskTokenRels();

        final var tokenTransfers = List.of(
                newStandardTokenTransfer().get(0),
                newReceiverSendsNftTokenTransfer().get(0));
        final var txn = newTransferTxn(tokenTransfers);
        final var multiTxnRound = mockRoundWithTxns(new SwirldTransaction(txn));

        // when:
        final var subject = new EntityMapWarmer(
                accountAdptSupplier, nftAdptSupplier, tokenRelAdptSupplier, new RecursiveSynchronousThreadpoolDouble());
        subject.warmCache(multiTxnRound);

        // then:
        // verify txn1's warming:
        verify(tokenRels)
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_RECEIVER_ACCOUNT_456.getAccountNum(), NFT_TOKEN_NUM_1000)));
        // verify txn2's warming:
        verify(tokenRels)
                .warm(EntityNumVirtualKey.fromPair(
                        EntityNumPair.fromLongs(NFT_SENDER_ACCOUNT_123.getAccountNum(), NFT_TOKEN_NUM_2000)));
    }

    private void simulateOnDiskAccounts() {
        simulateOnDiskAccounts(mock(VirtualMap.class));
    }

    private void simulateOnDiskAccounts(VirtualMap vmap) {
        given(accountAdpt.getOnDiskAccounts()).willReturn(VirtualMapLike.from(vmap));
    }

    private VirtualMap simulateOnDiskNfts() {
        return simulateOnDiskNfts(mock(VirtualMap.class));
    }

    private VirtualMap simulateOnDiskNfts(VirtualMap vmap) {
        given(nftAdpt.getOnDiskNfts()).willReturn(VirtualMapLike.from(vmap));
        return vmap;
    }

    private VirtualMap simulateOnDiskTokenRels() {
        return simulateOnDiskTokenRels(mock(VirtualMap.class));
    }

    private VirtualMap simulateOnDiskTokenRels(VirtualMap vmap) {
        given(tokenRelAdpt.getOnDiskRels()).willReturn(VirtualMapLike.from(vmap));
        return vmap;
    }

    private void simulateAllEntitiesOnDiskEnabled() {
        given(accountAdpt.areOnDisk()).willReturn(true);
        given(nftAdpt.isVirtual()).willReturn(true);
        given(tokenRelAdpt.areOnDisk()).willReturn(true);
    }

    private String newTxnMemo(Timestamp timestamp) {
        return "txn" + timestamp.getSeconds() + "." + timestamp.getNanos();
    }

    private byte[] newTransferTxn() {
        return newTransferTxn(newStandardTokenTransfer());
    }

    private List<TokenTransferList> newStandardTokenTransfer() {
        // In this case, a 'standard' token transfer refers to the case where sender account 123
        // sends token 1000 to receiver account 456
        return List.of(TokenTransferList.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(NFT_TOKEN_ID_1000).build())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(NFT_SENDER_ACCOUNT_123)
                        .setReceiverAccountID(NFT_RECEIVER_ACCOUNT_456)
                        .setSerialNumber(NFT_TOKEN_1000_SERIAL1)
                        .build())
                .build());
    }

    private List<TokenTransferList> newReceiverSendsNftTokenTransfer() {
        return List.of(TokenTransferList.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(NFT_TOKEN_ID_2000).build())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setSenderAccountID(
                                // Here we're using the original receiver account as the sender, and the
                                // original sender account as the receiver
                                NFT_RECEIVER_ACCOUNT_456)
                        .setReceiverAccountID(NFT_SENDER_ACCOUNT_123)
                        .setSerialNumber(NFT_TOKEN_2000_SERIAL3)
                        .build())
                .build());
    }

    private byte[] newNonCryptoTransferTxn() {
        final var now = newNowTimestamp();
        final var memo = newTxnMemo(newNowTimestamp());
        // Any non-transfer txn will do here
        final var cryptoUpdateBody = CryptoUpdateTransactionBody.newBuilder()
                .setAccountIDToUpdate(HBAR_TRANSFER_ACCOUNT_1)
                .clearMemo()
                .build();
        final var cryptoUpdateTxnBody = TransactionBody.newBuilder()
                .setCryptoUpdateAccount(cryptoUpdateBody)
                .setMemo(memo)
                .setTransactionID(
                        TransactionID.newBuilder().setTransactionValidStart(now).build())
                .build();
        return Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(cryptoUpdateTxnBody.toByteArray()))
                .build()
                .toByteArray();
    }

    private byte[] newTransferTxn(List<TokenTransferList> tokenTransfers) {
        final var now = newNowTimestamp();
        final var memo = newTxnMemo(now);
        var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(AccountAmount.newBuilder()
                                .setAccountID(HBAR_TRANSFER_ACCOUNT_1)
                                .setAmount(-1)
                                .build())
                        .build())
                .addAllTokenTransfers(tokenTransfers)
                .build();
        final var cryptoTransferTxnBody = TransactionBody.newBuilder()
                .setCryptoTransfer(cryptoTransferBody)
                .setMemo(memo)
                .setTransactionID(
                        TransactionID.newBuilder().setTransactionValidStart(now).build())
                .build();
        return Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(cryptoTransferTxnBody.toByteArray()))
                .build()
                .toByteArray();
    }

    private Round mockRoundWithTxn() {
        return mockRoundWithTxns(new SwirldTransaction(newTransferTxn()));
    }

    private Round mockRoundWithTxns(SwirldTransaction... txns) {
        final List<com.swirlds.common.system.transaction.Transaction> roundTxns = List.of(txns);

        var event = mock(EventImpl.class);
        Mockito.lenient().when(event.transactionIterator()).thenReturn(roundTxns.iterator());
        lenient().doCallRealMethod().when(event).forEachTransaction(notNull());

        final var round = mock(ConsensusRound.class);
        lenient().when(round.getRoundNum()).thenReturn(1L);
        lenient()
                .when(round.iterator())
                .thenReturn(List.of((ConsensusEvent) event).iterator());
        return round;
    }

    private void verifyNoCacheWarming() {
        verify(accountAdpt, never()).getOnDiskAccounts();
        verify(nftAdpt, never()).getOnDiskNfts();
        verify(tokenRelAdpt, never()).getOnDiskRels();
    }

    private static Timestamp newNowTimestamp() {
        return Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
                .setNanos(1)
                .build();
    }

    private static class RecursiveSynchronousThreadpoolDouble extends ThreadPoolExecutor {
        private final List<Runnable> taskQueue = new ArrayList<>();

        RecursiveSynchronousThreadpoolDouble() {
            this(Mockito.mock(BlockingQueue.class));
        }

        RecursiveSynchronousThreadpoolDouble(BlockingQueue<Runnable> workQueue) {
            // This is hacky, but we're going to completely ignore the actual threadpool. Instead,
            // we're going to use taskQueue to simulate a synchronous threadpool. We could do
            // this with a mock, but we want to actually run the commands that are placed in the
            // queue by any single command. The 'recursive' designation refers to the fact that
            // any command can place more commands in the queue, and we want to run those too.

            // Furthermore, while it's hacky, the production code needs to be re-worked, so this
            // implementation is sufficient for now.
            super(1, 1, 0, TimeUnit.SECONDS, workQueue);
        }

        @Override
        public void execute(@NonNull Runnable command) {
            // This code is intended to not only run the command received, but also any subsequent
            // commands that are placed in the task queue by a command itself. This is the
            // recursive piece of `RecursiveSynchronousThreadpoolDouble`.
            taskQueue.add(command);
            while (!taskQueue.isEmpty()) {
                taskQueue.remove(0).run();
            }
        }
    }
}
