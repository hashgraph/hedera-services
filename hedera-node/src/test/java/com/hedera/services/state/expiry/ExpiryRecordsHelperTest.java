/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry;

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.MiscUtils.*;
import static com.hedera.test.utils.TxnUtils.*;
import static java.util.stream.Collectors.toList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.expiry.removal.CryptoGcOutcome;
import com.hedera.services.state.expiry.removal.FungibleTreasuryReturns;
import com.hedera.services.state.expiry.removal.NonFungibleTreasuryReturns;
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryRecordsHelperTest {
    private final long fee = 1_234L;
    private final long newExpiry = 1_234_567L + 7776000L;
    private final Instant instantNow = Instant.ofEpochSecond(1_234_567L);
    private final AccountID removedId = IdUtils.asAccount("0.0.3");
    private final EntityId autoRenewId = EntityId.fromIdentityCode(4);
    private final AccountID funding = IdUtils.asAccount("0.0.98");
    private final EntityNum expiredNum = EntityNum.fromAccountId(removedId);
    private final TransactionBody.Builder mockBody =
            TransactionBody.newBuilder()
                    .setTransactionID(
                            TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.789")));

    @Mock private RecordStreaming recordStreaming;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ConsensusTimeTracker consensusTimeTracker;

    private ExpiryRecordsHelper subject;

    @BeforeEach
    void setUp() {
        subject =
                new ExpiryRecordsHelper(
                        recordStreaming,
                        syntheticTxnFactory,
                        new MockGlobalDynamicProps(),
                        consensusTimeTracker);
    }

    @Test
    void streamsExpectedAccountRemovalRecord() {
        final var aToken = TokenID.newBuilder().setTokenNum(1_234L).build();
        final var bToken = TokenID.newBuilder().setTokenNum(2_345L).build();
        final var nfToken = TokenID.newBuilder().setTokenNum(666L).build();
        final var from = AccountID.newBuilder().setAccountNum(3_456L).build();
        final var firstTo = AccountID.newBuilder().setAccountNum(5_678L).build();
        final var secondTo = AccountID.newBuilder().setAccountNum(4_567L).build();
        final var sender = AccountID.newBuilder().setAccountNum(777L).build();
        final var receiver = AccountID.newBuilder().setAccountNum(888L).build();
        final var aBalance = 100L;
        final var bBalance = 200L;
        final var removalTime = instantNow.plusNanos(0);
        final var displacements =
                List.of(
                        ttlOf(aToken, from, firstTo, aBalance),
                        ttlOf(bToken, from, secondTo, bBalance),
                        exchangeOf(nfToken, sender, receiver, 1L));
        final var rso =
                expectedRso(
                        cryptoRemovalRecord(
                                false, true, removedId, removalTime, removedId, displacements),
                        0);

        given(syntheticTxnFactory.synthAccountAutoRemove(expiredNum)).willReturn(mockBody);
        given(consensusTimeTracker.nextStandaloneRecordTime()).willReturn(instantNow);

        final var returns =
                new CryptoGcOutcome(
                        new FungibleTreasuryReturns(
                                tokensFrom(displacements).subList(0, 2),
                                adjustmentsFrom(displacements).subList(0, 2),
                                true),
                        new NonFungibleTreasuryReturns(
                                tokensFrom(displacements).subList(2, 3),
                                exchangesFrom(displacements).subList(2, 3),
                                true),
                        true);
        subject.streamCryptoRemovalStep(false, expiredNum, null, returns);

        // then:
        verify(recordStreaming).streamSystemRecord(rso);
        verify(consensusTimeTracker).nextStandaloneRecordTime();
    }

    @Test
    void streamsExpectedContractTreasuryReturnRecord() {
        final var aToken = TokenID.newBuilder().setTokenNum(1_234L).build();
        final var bToken = TokenID.newBuilder().setTokenNum(2_345L).build();
        final var nfToken = TokenID.newBuilder().setTokenNum(666L).build();
        final var from = AccountID.newBuilder().setAccountNum(3_456L).build();
        final var firstTo = AccountID.newBuilder().setAccountNum(5_678L).build();
        final var secondTo = AccountID.newBuilder().setAccountNum(4_567L).build();
        final var sender = AccountID.newBuilder().setAccountNum(777L).build();
        final var receiver = AccountID.newBuilder().setAccountNum(888L).build();
        final var aBalance = 100L;
        final var bBalance = 200L;
        final var removalTime = instantNow.plusNanos(0);
        final var displacements =
                List.of(
                        ttlOf(aToken, from, firstTo, aBalance),
                        ttlOf(bToken, from, secondTo, bBalance),
                        exchangeOf(nfToken, sender, receiver, 1L));
        final var rso =
                expectedRso(
                        cryptoRemovalRecord(
                                true,
                                false,
                                removedId,
                                removalTime,
                                autoRenewId.toGrpcAccountId(),
                                displacements),
                        0);

        given(consensusTimeTracker.nextStandaloneRecordTime()).willReturn(instantNow);

        final var returns =
                new CryptoGcOutcome(
                        new FungibleTreasuryReturns(
                                tokensFrom(displacements).subList(0, 2),
                                adjustmentsFrom(displacements).subList(0, 2),
                                true),
                        new NonFungibleTreasuryReturns(
                                tokensFrom(displacements).subList(2, 3),
                                exchangesFrom(displacements).subList(2, 3),
                                true),
                        false);
        given(syntheticTxnFactory.synthTokenTransfer(returns)).willReturn(mockBody);
        subject.streamCryptoRemovalStep(true, expiredNum, autoRenewId, returns);

        // then:
        verify(recordStreaming).streamSystemRecord(rso);
        verify(consensusTimeTracker).nextStandaloneRecordTime();
    }

    @Test
    void streamsExpectedAccountRenewalRecord() {
        final var renewalTime = instantNow.plusNanos(0);
        final var rso =
                expectedRso(
                        cryptoRenewalRecord(
                                removedId, renewalTime, removedId, fee, newExpiry, funding, false),
                        0);
        given(syntheticTxnFactory.synthAccountAutoRenew(expiredNum, newExpiry))
                .willReturn(mockBody);
        given(consensusTimeTracker.nextStandaloneRecordTime()).willReturn(rso.getTimestamp());

        subject.streamCryptoRenewal(expiredNum, fee, newExpiry, false, expiredNum);

        // then:
        verify(consensusTimeTracker).nextStandaloneRecordTime();
        verify(recordStreaming).streamSystemRecord(rso);
    }

    @Test
    void streamsExpectedContractRenewalRecord() {
        final var renewalTime = instantNow.plusNanos(0);
        final var rso =
                expectedRso(
                        cryptoRenewalRecord(
                                removedId, renewalTime, removedId, fee, newExpiry, funding, true),
                        0);
        given(
                        syntheticTxnFactory.synthContractAutoRenew(
                                expiredNum, newExpiry, expiredNum.toGrpcAccountId()))
                .willReturn(mockBody);
        given(consensusTimeTracker.nextStandaloneRecordTime()).willReturn(rso.getTimestamp());

        subject.streamCryptoRenewal(expiredNum, fee, newExpiry, true, expiredNum);

        // then:
        verify(consensusTimeTracker).nextStandaloneRecordTime();
        verify(recordStreaming).streamSystemRecord(rso);
    }

    static List<EntityId> tokensFrom(final List<TokenTransferList> ttls) {
        return ttls.stream()
                .map(TokenTransferList::getToken)
                .map(EntityId::fromGrpcTokenId)
                .collect(toList());
    }

    private RecordStreamObject expectedRso(final TransactionRecord record, final int nanosOffset) {
        return new RecordStreamObject(
                fromGprc(record),
                synthFromBody(mockBody.build()),
                instantNow.plusNanos(nanosOffset));
    }

    private TransactionRecord cryptoRemovalRecord(
            final boolean isContract,
            final boolean isFinished,
            final AccountID accountRemoved,
            final Instant removedAt,
            final AccountID autoRenewAccount,
            final List<TokenTransferList> displacements) {
        final var receipt = TransactionReceipt.newBuilder().setAccountID(accountRemoved).build();
        final var transactionID = TransactionID.newBuilder().setAccountID(autoRenewAccount).build();

        return TransactionRecord.newBuilder()
                .setReceipt(receipt)
                .setConsensusTimestamp(asTimestamp(removedAt))
                .setTransactionID(transactionID)
                .setMemo(
                        String.format(
                                "%s %s %s",
                                (isContract ? "Contract" : "Account"),
                                asLiteralString(accountRemoved),
                                (isFinished
                                        ? "was automatically deleted"
                                        : "returned treasury assets")))
                .setTransactionFee(0L)
                .addAllTokenTransferLists(displacements)
                .build();
    }

    private TransactionRecord cryptoRenewalRecord(
            final AccountID accountRenewed,
            final Instant renewedAt,
            final AccountID autoRenewAccount,
            final long fee,
            final long newExpirationTime,
            final AccountID feeCollector,
            final boolean isContract) {
        final var receipt = TransactionReceipt.newBuilder().setAccountID(accountRenewed).build();
        final var transactionID = TransactionID.newBuilder().setAccountID(autoRenewAccount).build();
        final var memo =
                String.format(
                        "%s %s was automatically renewed. New expiration time: %d.",
                        isContract ? "Contract" : "Account",
                        asLiteralString(accountRenewed),
                        newExpirationTime);
        final var payerAmount =
                AccountAmount.newBuilder()
                        .setAccountID(autoRenewAccount)
                        .setAmount(-1 * fee)
                        .build();
        final var payeeAmount =
                AccountAmount.newBuilder().setAccountID(feeCollector).setAmount(fee).build();
        final var transferList =
                TransferList.newBuilder()
                        .addAccountAmounts(payeeAmount)
                        .addAccountAmounts(payerAmount)
                        .build();

        return TransactionRecord.newBuilder()
                .setReceipt(receipt)
                .setConsensusTimestamp(asTimestamp(renewedAt))
                .setTransactionID(transactionID)
                .setMemo(memo)
                .setTransactionFee(fee)
                .setTransferList(transferList)
                .build();
    }
}
