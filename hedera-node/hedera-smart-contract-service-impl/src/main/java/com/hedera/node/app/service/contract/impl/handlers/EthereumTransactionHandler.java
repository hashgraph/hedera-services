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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.WRONG_NONCE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.throwIfUnsuccessful;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.node.app.hapi.utils.fee.SmartContractFeeBuilder;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.infra.EthTxSigsCache;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionRecordBuilder;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.mono.fees.calculation.ethereum.txns.EthereumTransactionResourceUsage;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#ETHEREUM_TRANSACTION}.
 */
@Singleton
public class EthereumTransactionHandler implements TransactionHandler {
    private final EthTxSigsCache ethereumSignatures;
    private final EthereumCallDataHydration callDataHydration;
    private final Provider<TransactionComponent.Factory> provider;

    @Inject
    public EthereumTransactionHandler(
            @NonNull final EthTxSigsCache ethereumSignatures,
            @NonNull final EthereumCallDataHydration callDataHydration,
            @NonNull final Provider<TransactionComponent.Factory> provider) {
        this.ethereumSignatures = requireNonNull(ethereumSignatures);
        this.callDataHydration = requireNonNull(callDataHydration);
        this.provider = requireNonNull(provider);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var body = context.body().ethereumTransactionOrThrow();
        final var fileStore = context.createStore(ReadableFileStore.class);
        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        final var hydratedTx = callDataHydration.tryToHydrate(body, fileStore, hederaConfig.firstUserEntity());

        validateTruePreCheck(hydratedTx.status() == OK, hydratedTx.status());

        final var ethTxData = hydratedTx.ethTxData();
        validateTruePreCheck(ethTxData != null, INVALID_ETHEREUM_TRANSACTION);

        // Ignore the return value; we just want to cache the signature for use in handle()
        ethereumSignatures.computeIfAbsent(ethTxData);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        // Create the transaction-scoped component
        final var component = provider.get().create(context, ETHEREUM_TRANSACTION);

        final var hevmTransactionFactory = component.contextTransactionProcessor().hevmTransactionFactory;
        final var hevmTransaction = hevmTransactionFactory.fromHapiTransaction(context.body());

        final var accountStore = context.readableStore(ReadableAccountStore.class);
        final var sender = accountStore.getAccountById(Objects.requireNonNull(hevmTransaction.senderId()));

        // Assemble the appropriate top-level record for the result
        final var ethTxData =
                requireNonNull(requireNonNull(component.hydratedEthTxData()).ethTxData());

        // Run its in-scope transaction and get the outcome
        final var outcome = component.contextTransactionProcessor().call();

        final var recordBuilder = context.recordBuilder(EthereumTransactionRecordBuilder.class)
                .ethereumHash(Bytes.wrap(ethTxData.getEthereumHash()))
                .status(outcome.status())
                .feeChargedToPayer(outcome.tinybarGasCost());

        if (ethTxData.hasToAddress()) {
            // The Ethereum transaction was a top-level MESSAGE_CALL
            recordBuilder.contractID(outcome.recipientId()).contractCallResult(outcome.result());
        } else {
            // The Ethereum transaction was a top-level CONTRACT_CREATION
            recordBuilder.contractID(outcome.recipientIdIfCreated()).contractCreateResult(outcome.result());
        }

        validateTrue(sender.ethereumNonce() == ethTxData.nonce(), WRONG_NONCE);

        throwIfUnsuccessful(outcome.status());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var body = feeContext.body();
        return feeContext
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(sigValueObj -> new EthereumTransactionResourceUsage(new SmartContractFeeBuilder())
                        .usageGiven(fromPbj(body), sigValueObj, null));
    }
}
