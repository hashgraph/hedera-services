/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.EVM_ADDRESS_LENGTH_AS_INT;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.throwIfUnsuccessful;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.contract.impl.ContractServiceComponent;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.infra.EthTxSigsCache;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionStreamBuilder;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#ETHEREUM_TRANSACTION}.
 */
@Singleton
public class EthereumTransactionHandler extends AbstractContractTransactionHandler {
    private final byte[] EMPTY_ADDRESS = new byte[20];
    private final EthTxSigsCache ethereumSignatures;
    private final EthereumCallDataHydration callDataHydration;

    /**
     * @param ethereumSignatures the ethereum signatures
     * @param callDataHydration the ethereum call data hydratino utility to be used for EthTxData
     * @param provider the provider to be used
     * @param gasCalculator the gas calculator to be used
     */
    @Inject
    public EthereumTransactionHandler(
            @NonNull final EthTxSigsCache ethereumSignatures,
            @NonNull final EthereumCallDataHydration callDataHydration,
            @NonNull final Provider<TransactionComponent.Factory> provider,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final ContractServiceComponent component) {
        super(provider, gasCalculator, component);
        this.ethereumSignatures = requireNonNull(ethereumSignatures);
        this.callDataHydration = requireNonNull(callDataHydration);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        // Ignore the return value; we just want to cache the signature for use in handle()
        computeEthTxSigsFor(
                context.body().ethereumTransactionOrThrow(),
                context.createStore(ReadableFileStore.class),
                context.configuration());
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        try {
            final var ethTxData = populateEthTxData(
                    requireNonNull(txn.ethereumTransactionOrThrow().ethereumData())
                            .toByteArray());
            validateTruePreCheck(nonNull(ethTxData), INVALID_ETHEREUM_TRANSACTION);
            final byte[] callData = ethTxData.hasCallData() ? ethTxData.callData() : new byte[0];
            final var intrinsicGas =
                    gasCalculator.transactionIntrinsicGasCost(org.apache.tuweni.bytes.Bytes.wrap(callData), false);
            validateTruePreCheck(ethTxData.gasLimit() >= intrinsicGas, INSUFFICIENT_GAS);
            // Do not allow sending HBars to Burn Address
            if (ethTxData.value().compareTo(BigInteger.ZERO) > 0) {
                validateFalsePreCheck(Arrays.equals(ethTxData.to(), EMPTY_ADDRESS), INVALID_SOLIDITY_ADDRESS);
            }
            // sanity check evm address if there is one
            if (ethTxData.hasToAddress()) {
                validateTruePreCheck(ethTxData.to().length == EVM_ADDRESS_LENGTH_AS_INT, INVALID_CONTRACT_ID);
            }
        } catch (@NonNull final Exception e) {
            bumpExceptionMetrics(ETHEREUM_TRANSACTION, e);
            if (e instanceof NullPointerException) {
                component.contractMetrics().incrementRejectedType3EthTx();
            }
            throw e;
        }
    }

    /**
     * If the given transaction, when hydrated from the given file store with the given config, implies a valid
     * {@link EthTxSigs}, returns it. Otherwise, returns null.
     *
     * @param op the transaction
     * @param fileStore the file store
     * @param config the configuration
     * @return the implied Ethereum signature metadata
     */
    public @Nullable EthTxSigs maybeEthTxSigsFor(
            @NonNull final EthereumTransactionBody op,
            @NonNull final ReadableFileStore fileStore,
            @NonNull final Configuration config) {
        requireNonNull(op);
        requireNonNull(config);
        requireNonNull(fileStore);
        try {
            return computeEthTxSigsFor(op, fileStore, config);
        } catch (PreCheckException ignore) {
            return null;
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        // Create the transaction-scoped component
        final var component = getTransactionComponent(context, ETHEREUM_TRANSACTION);

        // Run its in-scope transaction and get the outcome
        final var outcome = component.contextTransactionProcessor().call();

        // Assemble the appropriate top-level record for the result
        final var ethTxData =
                requireNonNull(requireNonNull(component.hydratedEthTxData()).ethTxData());
        context.savepointStack()
                .getBaseBuilder(EthereumTransactionStreamBuilder.class)
                .ethereumHash(Bytes.wrap(ethTxData.getEthereumHash()));
        if (ethTxData.hasToAddress()) {
            outcome.addCallDetailsTo(context.savepointStack().getBaseBuilder(ContractCallStreamBuilder.class));
        } else {
            outcome.addCreateDetailsTo(context.savepointStack().getBaseBuilder(ContractCreateStreamBuilder.class));
        }

        throwIfUnsuccessful(outcome.status());
    }

    /**
     * Does work needed to externalize details after an Ethereum transaction is throttled.
     * @param context the handle context
     */
    public void handleThrottled(@NonNull final HandleContext context) {
        final var component = getTransactionComponent(context, ETHEREUM_TRANSACTION);
        final var ethTxData =
                requireNonNull(requireNonNull(component.hydratedEthTxData()).ethTxData());
        context.savepointStack()
                .getBaseBuilder(EthereumTransactionStreamBuilder.class)
                .ethereumHash(Bytes.wrap(ethTxData.getEthereumHash()));
    }

    /**
     * Calculates the fees for the given {@link FeeContext}.
     * Even though the actual fees are not charged if the evmEthTransactionZeroHapiFeesEnabled feature flag is enabled
     * if the transaction fails, there is a fee charged to the relayer account
     * (see {@link com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor#chargeOnFailedEthTxn(HederaEvmTransaction) chargeOnFailedEthTxn})
     * @param feeContext the {@link FeeContext} with all information needed for the calculation
     * @return
     */
    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var zeroHapiFeeEnabled =
                feeContext.configuration().getConfigData(ContractsConfig.class).evmEthTransactionZeroHapiFeesEnabled();
        return zeroHapiFeeEnabled ? Fees.FREE : getLegacyCalculateFees(feeContext);
    }

    private Fees getLegacyCalculateFees(@NonNull final FeeContext feeContext) {
        final var body = feeContext.body();
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(SubType.DEFAULT)
                .legacyCalculate(
                        sigValueObj -> usageEstimator.getEthereumTransactionFeeMatrices(fromPbj(body), sigValueObj));
    }

    private EthTxSigs computeEthTxSigsFor(
            @NonNull final EthereumTransactionBody op,
            @NonNull final ReadableFileStore fileStore,
            @NonNull final Configuration config)
            throws PreCheckException {
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        final var hydratedTx = callDataHydration.tryToHydrate(op, fileStore, hederaConfig.firstUserEntity());
        validateTruePreCheck(hydratedTx.status() == OK, hydratedTx.status());
        final var ethTxData = hydratedTx.ethTxData();
        validateTruePreCheck(ethTxData != null, INVALID_ETHEREUM_TRANSACTION);
        try {
            return ethereumSignatures.computeIfAbsent(ethTxData);
        } catch (RuntimeException ignore) {
            // Ignore and translate any signature computation exception
            throw new PreCheckException(INVALID_ETHEREUM_TRANSACTION);
        }
    }
}
