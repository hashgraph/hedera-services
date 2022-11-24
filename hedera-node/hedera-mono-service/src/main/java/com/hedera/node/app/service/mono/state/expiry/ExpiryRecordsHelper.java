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
package com.hedera.node.app.service.mono.state.expiry;

import static com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.node.app.service.mono.utils.MiscUtils.synthWithRecordTxnId;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.ConsensusTimeTracker;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.removal.CryptoGcOutcome;
import com.hedera.node.app.service.mono.state.logic.RecordStreaming;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.stream.RecordStreamObject;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExpiryRecordsHelper {
    private final RecordStreaming recordStreaming;
    private final RecordsHistorian recordsHistorian;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final SideEffectsTracker sideEffectsTracker;

    @Inject
    public ExpiryRecordsHelper(
            final RecordStreaming recordStreaming,
            final RecordsHistorian recordsHistorian,
            final SyntheticTxnFactory syntheticTxnFactory,
            final ConsensusTimeTracker consensusTimeTracker,
            final SideEffectsTracker sideEffectsTracker) {
        this.recordStreaming = recordStreaming;
        this.recordsHistorian = recordsHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.consensusTimeTracker = consensusTimeTracker;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    public void streamCryptoRemovalStep(
            final boolean isContract,
            final EntityNum entityNum,
            final CryptoGcOutcome cryptoGcOutcome) {
        final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
        final var txnId = recordsHistorian.computeNextSystemTransactionId();
        final var memo =
                (isContract ? "Contract " : "Account ")
                        + entityNum.toIdString()
                        + (cryptoGcOutcome.finished()
                                ? " was automatically deleted"
                                : " returned treasury assets");

        final var expirableTxnRecord =
                baseRecordWith(eventTime, txnId)
                        .setMemo(memo)
                        .setTokens(cryptoGcOutcome.allReturnedTokens())
                        .setTokenAdjustments(cryptoGcOutcome.parallelAdjustments())
                        .setNftTokenAdjustments(cryptoGcOutcome.parallelExchanges());
        final TransactionBody.Builder synthBody;
        if (cryptoGcOutcome.finished()) {
            synthBody =
                    isContract
                            ? syntheticTxnFactory.synthContractAutoRemove(entityNum)
                            : syntheticTxnFactory.synthAccountAutoRemove(entityNum);
        } else {
            synthBody = syntheticTxnFactory.synthTokenTransfer(cryptoGcOutcome);
        }
        finalizeAndStream(expirableTxnRecord, synthBody, eventTime, recordStreaming);
    }

    public void streamCryptoRenewal(
            final EntityNum entityNum,
            final long fee,
            final long newExpiry,
            final boolean isContract) {
        final var eventTime = consensusTimeTracker.nextStandaloneRecordTime();
        final var memo =
                (isContract ? "Contract " : "Account ")
                        + entityNum.toIdString()
                        + " was automatically renewed; new expiration time: "
                        + newExpiry;
        final var synthBody =
                isContract
                        ? syntheticTxnFactory.synthContractAutoRenew(entityNum, newExpiry)
                        : syntheticTxnFactory.synthAccountAutoRenew(entityNum, newExpiry);
        final var txnId = recordsHistorian.computeNextSystemTransactionId();
        final var expirableTxnRecord =
                baseRecordWith(eventTime, txnId)
                        .setMemo(memo)
                        .setHbarAdjustments(sideEffectsTracker.getNetTrackedHbarChanges())
                        .setStakingRewardsPaid(sideEffectsTracker.getStakingRewardsPaid())
                        .setFee(fee);
        finalizeAndStream(expirableTxnRecord, synthBody, eventTime, recordStreaming);
    }

    public static void finalizeAndStream(
            final ExpirableTxnRecord.Builder expiringRecord,
            final TransactionBody.Builder synthBody,
            final Instant at,
            final RecordStreaming recordStreaming) {
        finalizeAndStream(expiringRecord, synthBody, at, recordStreaming, Collections.emptyList());
    }

    public static void finalizeAndStream(
            final ExpirableTxnRecord.Builder expiringRecord,
            final TransactionBody.Builder synthBody,
            final Instant at,
            final RecordStreaming recordStreaming,
            final List<TransactionSidecarRecord.Builder> sidecars) {
        final var synthTxn = synthWithRecordTxnId(synthBody, expiringRecord);
        final var rso = new RecordStreamObject(expiringRecord.build(), synthTxn, at, sidecars);
        recordStreaming.streamSystemRecord(rso);
    }

    public static ExpirableTxnRecord.Builder baseRecordWith(
            final Instant consensusTime, final TxnId txnId) {
        final var at = RichInstant.fromJava(consensusTime);
        final var receipt = new TxnReceipt();
        receipt.setStatus(SUCCESS_LITERAL);
        return ExpirableTxnRecord.newBuilder()
                .setTxnId(txnId)
                .setReceipt(receipt)
                .setConsensusTime(at);
    }
}
