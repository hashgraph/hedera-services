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

import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.services.utils.MiscUtils.synthFromBody;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.expiry.removal.CryptoGcOutcome;
import com.hedera.services.state.logic.RecordStreaming;
import com.hedera.services.state.submerkle.*;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExpiryRecordsHelper {
    private final RecordStreaming recordStreaming;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final GlobalDynamicProperties dynamicProperties;
    private final ConsensusTimeTracker consensusTimeTracker;

    @Inject
    public ExpiryRecordsHelper(
            final RecordStreaming recordStreaming,
            final SyntheticTxnFactory syntheticTxnFactory,
            final GlobalDynamicProperties dynamicProperties,
            final ConsensusTimeTracker consensusTimeTracker) {
        this.recordStreaming = recordStreaming;
        this.dynamicProperties = dynamicProperties;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.consensusTimeTracker = consensusTimeTracker;
    }

    public void streamCryptoRemovalStep(
            final boolean isContract,
            final EntityNum entityNum,
            @Nullable final EntityId autoRenewId,
            final CryptoGcOutcome cryptoGcOutcome) {
        final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
        final var grpcId = entityNum.toGrpcAccountId();
        final var memo =
                (isContract ? "Contract " : "Account ")
                        + entityNum.toIdString()
                        + (cryptoGcOutcome.finished()
                                ? " was automatically deleted"
                                : " returned treasury assets");

        final var expirableTxnRecord =
                forTouchedAccount(grpcId, eventTime, autoRenewId)
                        .setMemo(memo)
                        .setTokens(cryptoGcOutcome.allReturnedTokens())
                        .setTokenAdjustments(cryptoGcOutcome.parallelAdjustments())
                        .setNftTokenAdjustments(cryptoGcOutcome.parallelExchanges())
                        .build();
        final var synthBody =
                cryptoGcOutcome.finished()
                        ? syntheticTxnFactory.synthAccountAutoRemove(entityNum)
                        : syntheticTxnFactory.synthTokenTransfer(cryptoGcOutcome);
        stream(expirableTxnRecord, synthBody, eventTime);
    }

    public void streamCryptoRenewal(
            final EntityNum entityNum,
            final long fee,
            final long newExpiry,
            final boolean isContract,
            final EntityNum payerForAutoRenew) {
        final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
        final var grpcId = entityNum.toGrpcAccountId();
        final var payerId = payerForAutoRenew.toGrpcAccountId();
        final var memo =
                (isContract ? "Contract " : "Account ")
                        + entityNum.toIdString()
                        + " was automatically renewed. New expiration time: "
                        + newExpiry
                        + ".";
        final var synthBody =
                isContract
                        ? syntheticTxnFactory.synthContractAutoRenew(entityNum, newExpiry, payerId)
                        : syntheticTxnFactory.synthAccountAutoRenew(entityNum, newExpiry);
        final var expirableTxnRecord =
                forTouchedAccount(grpcId, eventTime, payerForAutoRenew.toEntityId())
                        .setMemo(memo)
                        .setHbarAdjustments(feeXfers(fee, payerId))
                        .setFee(fee)
                        .build();
        stream(expirableTxnRecord, synthBody, eventTime);
    }

    private void stream(
            final ExpirableTxnRecord expiringRecord,
            final TransactionBody.Builder synthBody,
            final Instant at) {
        final var rso =
                new RecordStreamObject(expiringRecord, synthFromBody(synthBody.build()), at);
        recordStreaming.streamSystemRecord(rso);
    }

    private CurrencyAdjustments feeXfers(final long amount, final AccountID payer) {
        final var funding = dynamicProperties.fundingAccount();
        return new CurrencyAdjustments(
                new long[] {amount, -amount},
                new long[] {funding.getAccountNum(), payer.getAccountNum()});
    }

    private ExpirableTxnRecord.Builder forTouchedAccount(
            final AccountID accountId,
            final Instant consensusTime,
            @Nullable EntityId autoRenewId) {
        final var at = RichInstant.fromJava(consensusTime);
        final var id = EntityId.fromGrpcAccountId(accountId);
        final var receipt = new TxnReceipt();
        receipt.setAccountId(id);

        final var effectivePayerId =
                (autoRenewId != null) ? autoRenewId : EntityId.fromGrpcAccountId(accountId);
        final var txnId =
                new TxnId(effectivePayerId, MISSING_INSTANT, false, USER_TRANSACTION_NONCE);
        return ExpirableTxnRecord.newBuilder()
                .setTxnId(txnId)
                .setReceipt(receipt)
                .setConsensusTime(at);
    }
}
