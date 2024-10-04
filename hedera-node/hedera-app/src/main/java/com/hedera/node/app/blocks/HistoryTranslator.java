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
import com.hedera.node.app.blocks.impl.TranslationContext;
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
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Translates a {@link TransactionResult} and, optionally, one or more {@link TransactionOutput}s within a given
 * {@link TranslationContext} into a {@link TransactionRecord} appropriate for returning from a query.
 */
public class HistoryTranslator {
    private static final Function<TransactionOutput, ContractFunctionResult> CONTRACT_CALL_EXTRACTOR =
            output -> output.contractCallOrThrow().contractCallResultOrThrow();
    private static final Function<TransactionOutput, ContractFunctionResult> CONTRACT_CREATE_EXTRACTOR =
            output -> output.contractCreateOrThrow().contractCreateResultOrThrow();

    public static final HistoryTranslator HISTORY_TRANSLATOR = new HistoryTranslator();

    /**
     * Translate the given {@link TransactionResult} and optional {@link TransactionOutput}s into a
     * {@link TransactionReceipt} appropriate for returning from a query.
     * @param context the context of the transaction
     * @param result the result of the transaction
     * @param outputs the outputs of the transaction
     * @return the translated receipt
     */
    public TransactionReceipt translateReceipt(
            @NonNull final TranslationContext context,
            @NonNull final TransactionResult result,
            @Nullable final TransactionOutput... outputs) {
        final var receiptBuilder =
                TransactionReceipt.newBuilder().status(result.status()).exchangeRate(result.exchangeRate());
        final var function = context.functionality();
        switch (function) {
            case CONTRACT_CALL,
                    CONTRACT_CREATE,
                    CONTRACT_DELETE,
                    CONTRACT_UPDATE,
                    ETHEREUM_TRANSACTION -> receiptBuilder.contractID(((ContractOpContext) context).contractId());
            case CRYPTO_CREATE, CRYPTO_UPDATE -> receiptBuilder.accountID(((CryptoOpContext) context).accountId());
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
            case TOKEN_MINT -> receiptBuilder
                    .newTotalSupply(((MintOpContext) context).newTotalSupply())
                    .serialNumbers(((MintOpContext) context).serialNumbers());
            case TOKEN_BURN, TOKEN_ACCOUNT_WIPE -> receiptBuilder.newTotalSupply(
                    ((SupplyChangeOpContext) context).newTotalSupply());
            case TOKEN_CREATE -> receiptBuilder.tokenID(((TokenOpContext) context).tokenId());
            case CONSENSUS_CREATE_TOPIC -> receiptBuilder.topicID(((TopicOpContext) context).topicId());
        }
        return receiptBuilder.build();
    }

    public TransactionRecord translateRecord(
            @NonNull final TranslationContext context,
            @NonNull final TransactionResult result,
            @Nullable final TransactionOutput... outputs) {
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
                    case CRYPTO_CREATE, CRYPTO_UPDATE -> recordBuilder.evmAddress(
                            ((CryptoOpContext) context).evmAddress());
                    case TOKEN_AIRDROP -> recordBuilder.newPendingAirdrops(
                            ((AirdropOpContext) context).pendingAirdropRecords());
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
        return recordBuilder.receipt(translateReceipt(context, result, outputs)).build();
    }

    private static @Nullable ContractFunctionResult contractCallResultIfPresent(
            @Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(TransactionOutput::hasContractCall, CONTRACT_CALL_EXTRACTOR, outputs);
    }

    private static @Nullable ContractFunctionResult contractCreateResultIfPresent(
            @Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(TransactionOutput::hasContractCreate, CONTRACT_CREATE_EXTRACTOR, outputs);
    }

    private static @Nullable EthereumOutput ethereumOutputIfPresent(@Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(
                TransactionOutput::hasEthereumCall, TransactionOutput::ethereumCallOrThrow, outputs);
    }

    private static @Nullable CryptoTransferOutput cryptoTransferOutputIfPresent(
            @Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(
                TransactionOutput::hasCryptoTransfer, TransactionOutput::cryptoTransferOrThrow, outputs);
    }

    private static @Nullable CreateScheduleOutput createScheduleOutputIfPresent(
            @Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(
                TransactionOutput::hasCreateSchedule, TransactionOutput::createScheduleOrThrow, outputs);
    }

    private static @Nullable SignScheduleOutput signScheduleOutputIfPresent(
            @Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(
                TransactionOutput::hasSignSchedule, TransactionOutput::signScheduleOrThrow, outputs);
    }

    private static @Nullable UtilPrngOutput utilPrngOutputIfPresent(@Nullable final TransactionOutput... outputs) {
        return outputValueIfPresent(TransactionOutput::hasUtilPrng, TransactionOutput::utilPrngOrThrow, outputs);
    }

    private static <T> @Nullable T outputValueIfPresent(
            @NonNull final Predicate<TransactionOutput> filter,
            @NonNull final Function<TransactionOutput, T> extractor,
            @Nullable final TransactionOutput... outputs) {
        if (outputs == null) {
            return null;
        }
        for (final var output : outputs) {
            if (filter.test(output)) {
                return extractor.apply(output);
            }
        }
        return null;
    }
}
