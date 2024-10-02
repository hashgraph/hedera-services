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

package com.hedera.node.app.blocks;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;

import com.hedera.hapi.block.stream.output.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.block.stream.output.UtilPrngOutput;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.blocks.impl.RecordTranslationContext;
import com.hedera.node.app.blocks.impl.contexts.AirdropOpContext;
import com.hedera.node.app.blocks.impl.contexts.ContractOpContext;
import com.hedera.node.app.blocks.impl.contexts.CryptoOpContext;
import com.hedera.node.app.blocks.impl.contexts.FileOpContext;
import com.hedera.node.app.blocks.impl.contexts.MintOpContext;
import com.hedera.node.app.blocks.impl.contexts.NodeOpContext;
import com.hedera.node.app.blocks.impl.contexts.ScheduleOpContext;
import com.hedera.node.app.blocks.impl.contexts.SubmitOpContext;
import com.hedera.node.app.blocks.impl.contexts.SupplyChangeOpContext;
import com.hedera.node.app.blocks.impl.contexts.TokenOpContext;
import com.hedera.node.app.blocks.impl.contexts.TopicOpContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates a {@link TransactionResult} and, optionally, one or more {@link TransactionOutput}s within a given
 * {@link RecordTranslationContext} into a {@link TransactionRecord} appropriate for returning from a query.
 */
@Singleton
public class RecordTranslator {
    @Inject
    public RecordTranslator() {
        // Dagger2
    }

    public TransactionRecord translate(
            @NonNull final RecordTranslationContext context,
            @NonNull final TransactionResult result,
            @Nullable final TransactionOutput... outputs) {
        final var receiptBuilder =
                TransactionReceipt.newBuilder().status(result.status()).exchangeRate(result.exchangeRate());
        final var recordBuilder = TransactionRecord.newBuilder()
                .transactionID(context.txnId())
                .memo(context.memo())
                .transactionHash(context.transactionHash())
                .consensusTimestamp(result.consensusTimestamp())
                .parentConsensusTimestamp(result.parentConsensusTimestamp())
                .scheduleRef(result.scheduleRef())
                .transactionFee(result.transactionFeeCharged())
                .transferList(result.transferList())
                .tokenTransferLists(result.tokenTransferLists())
                .automaticTokenAssociations(result.automaticTokenAssociations())
                .paidStakingRewards(result.paidStakingRewards());
        final var function = context.functionality();
        switch (function) {
            case CONTRACT_CALL, CONTRACT_CREATE, CONTRACT_DELETE, CONTRACT_UPDATE, ETHEREUM_TRANSACTION -> {
                receiptBuilder.contractID(((ContractOpContext) context).contractId());
                if (function == CONTRACT_CALL) {
                    recordBuilder.contractCallResult(contractCallResultIfPresent(outputs));
                } else if (function == CONTRACT_CREATE) {
                    recordBuilder.contractCreateResult(contractCreateResultIfPresent(outputs));
                } else if (function == ETHEREUM_TRANSACTION) {
                    final var ethOutput = ethereumOutputIfPresent(outputs);
                    if (ethOutput != null) {
                        recordBuilder.ethereumHash(ethOutput.ethereumHash());
                        switch (ethOutput.ethResult().kind()) {
                            case ETHEREUM_CALL_RESULT -> recordBuilder.contractCallResult(
                                    ethOutput.ethereumCallResultOrThrow());
                            case ETHEREUM_CREATE_RESULT -> recordBuilder.contractCreateResult(
                                    ethOutput.ethereumCreateResultOrThrow());
                        }
                    }
                }
            }
            default -> {
                final var synthResult = contractCallResultIfPresent(outputs);
                if (synthResult != null) {
                    recordBuilder.contractCallResult(synthResult);
                }
                switch (function) {
                    case CRYPTO_TRANSFER -> {
                        final var cryptoOutput = cryptoTransferOutputIfPresent(outputs);
                        if (cryptoOutput != null) {
                            recordBuilder.assessedCustomFees(cryptoOutput.assessedCustomFees());
                        }
                    }
                    case CRYPTO_CREATE, CRYPTO_UPDATE -> {
                        receiptBuilder.accountID(((CryptoOpContext) context).accountId());
                        recordBuilder.evmAddress(((CryptoOpContext) context).evmAddress());
                    }
                    case FILE_CREATE -> receiptBuilder.fileID(((FileOpContext) context).fileId());
                    case NODE_CREATE -> receiptBuilder.nodeId(((NodeOpContext) context).nodeId());
                    case SCHEDULE_CREATE -> {
                        final var scheduleOutput = createScheduleOutputIfPresent(outputs);
                        if (scheduleOutput != null) {
                            receiptBuilder
                                    .scheduleID(scheduleOutput.scheduleId())
                                    .scheduledTransactionID(scheduleOutput.scheduledTransactionId());
                        }
                    }
                    case SCHEDULE_DELETE -> receiptBuilder.scheduleID(((ScheduleOpContext) context).scheduleId());
                    case SCHEDULE_SIGN -> {
                        final var signOutput = signScheduleOutputIfPresent(outputs);
                        if (signOutput != null) {
                            receiptBuilder.scheduledTransactionID(signOutput.scheduledTransactionId());
                        }
                    }
                    case CONSENSUS_SUBMIT_MESSAGE -> receiptBuilder
                            .topicRunningHashVersion(((SubmitOpContext) context).runningHashVersion())
                            .topicSequenceNumber(((SubmitOpContext) context).sequenceNumber())
                            .topicRunningHash(((SubmitOpContext) context).runningHash());
                    case TOKEN_AIRDROP -> recordBuilder.newPendingAirdrops(
                            ((AirdropOpContext) context).pendingAirdropRecords());
                    case TOKEN_MINT -> receiptBuilder
                            .newTotalSupply(((MintOpContext) context).newTotalSupply())
                            .serialNumbers(((MintOpContext) context).serialNumbers());
                    case TOKEN_BURN, TOKEN_ACCOUNT_WIPE -> receiptBuilder.newTotalSupply(
                            ((SupplyChangeOpContext) context).newTotalSupply());
                    case TOKEN_CREATE -> receiptBuilder.tokenID(((TokenOpContext) context).tokenId());
                    case CONSENSUS_CREATE_TOPIC -> receiptBuilder.topicID(((TopicOpContext) context).topicId());
                    case UTIL_PRNG -> {
                        final var prngOutput = utilPrngOutputIfPresent(outputs);
                        if (prngOutput != null) {
                            switch (prngOutput.entropy().kind()) {
                                case PRNG_BYTES -> recordBuilder.prngBytes(prngOutput.prngBytesOrThrow());
                                case PRNG_NUMBER -> recordBuilder.prngNumber(prngOutput.prngNumberOrThrow());
                            }
                        }
                    }
                }
            }
        }
        return recordBuilder.receipt(receiptBuilder).build();
    }

    private static @Nullable ContractFunctionResult contractCallResultIfPresent(
            @Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasContractCall()) {
                return output.contractCallOrThrow().contractCallResultOrThrow();
            }
        }
        return null;
    }

    private static @Nullable ContractFunctionResult contractCreateResultIfPresent(
            @Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasContractCreate()) {
                return output.contractCreateOrThrow().contractCreateResultOrThrow();
            }
        }
        return null;
    }

    private static @Nullable EthereumOutput ethereumOutputIfPresent(@Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasEthereumCall()) {
                return output.ethereumCallOrThrow();
            }
        }
        return null;
    }

    private static @Nullable CryptoTransferOutput cryptoTransferOutputIfPresent(
            @Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasCryptoTransfer()) {
                return output.cryptoTransferOrThrow();
            }
        }
        return null;
    }

    private static @Nullable CreateScheduleOutput createScheduleOutputIfPresent(
            @Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasCreateSchedule()) {
                return output.createScheduleOrThrow();
            }
        }
        return null;
    }

    private static @Nullable SignScheduleOutput signScheduleOutputIfPresent(
            @Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasSignSchedule()) {
                return output.signScheduleOrThrow();
            }
        }
        return null;
    }

    private static @Nullable UtilPrngOutput utilPrngOutputIfPresent(@Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (output.hasUtilPrng()) {
                return output.utilPrngOrThrow();
            }
        }
        return null;
    }
}
