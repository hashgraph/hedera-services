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

import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.blocks.impl.RecordTranslationContext;
import com.hedera.node.app.blocks.impl.contexts.ContractOpContext;
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
        final var receiptBuilder = TransactionReceipt.newBuilder()
                .status(result.status())
                // TODO - validate how BlockStreamBuilder sets this
                .exchangeRate(result.exchangeRate());
        final var recordBuilder = TransactionRecord.newBuilder()
                .transactionID(context.txnId())
                .memo(context.memo())
                .transactionHash(context.transactionHash())
                .consensusTimestamp(result.consensusTimestamp())
                // TODO - validate how BlockStreamBuilder sets this
                .parentConsensusTimestamp(result.parentConsensusTimestamp())
                // TODO - validate how BlockStreamBuilder sets this
                .scheduleRef(result.scheduleRef())
                .transactionFee(result.transactionFeeCharged())
                .transferList(result.transferList())
                .tokenTransferLists(result.tokenTransferLists())
                .automaticTokenAssociations(result.automaticTokenAssociations())
                .paidStakingRewards(result.paidStakingRewards());
        final var function = context.functionality();
        switch (function) {
            case CONTRACT_CALL, CONTRACT_CREATE, CONTRACT_DELETE, CONTRACT_UPDATE, ETHEREUM_TRANSACTION -> {
                receiptBuilder.contractID(as(ContractOpContext.class, context).contractId());
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
            case CRYPTO_TRANSFER -> {
                final var cryptoOutput = cryptoTransferOutputIfPresent(outputs);
                if (cryptoOutput != null) {
                    recordBuilder.assessedCustomFees(cryptoOutput.assessedCustomFees());
                }
            }
            default -> {
                final var synthResult = contractCallResultIfPresent(outputs);
                if (synthResult != null) {
                    recordBuilder.contractCallResult(synthResult);
                }
            }
        }
        return recordBuilder.receipt(receiptBuilder).build();
    }

    private static <T extends RecordTranslationContext> T as(
            @NonNull final Class<T> type, @NonNull final RecordTranslationContext context) {
        if (type.isInstance(context)) {
            return type.cast(context);
        }
        throw new IllegalArgumentException(
                "Context " + context.getClass().getSimpleName() + " is not of expected type " + type.getSimpleName());
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
}
