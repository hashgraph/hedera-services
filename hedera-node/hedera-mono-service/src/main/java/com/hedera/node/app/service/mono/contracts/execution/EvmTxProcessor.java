/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;

import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaTracer;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaTracer.EmitActionSidecars;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaTracer.ValidateActionSidecars;
import com.hedera.node.app.service.mono.exceptions.ResourceLimitException;
import com.hedera.node.app.service.mono.store.contracts.HederaMutableWorldState;
import com.hedera.node.app.service.mono.store.contracts.HederaWorldState;
import com.hedera.node.app.service.mono.store.models.Account;
import com.hedera.services.stream.proto.SidecarType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

/**
 * Abstract processor of EVM transactions that prepares the {@link EVM} and all the peripherals upon
 * instantiation. Provides a base {@link EvmTxProcessor#execute(Account, Address, long, long, long,
 * Bytes, boolean, boolean, Address, BigInteger, long, Account)} method that handles the end-to-end
 * execution of an EVM transaction.
 */
abstract class EvmTxProcessor extends HederaEvmTxProcessor {

    protected EvmTxProcessor(
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps) {
        this(null, livePricesSource, dynamicProperties, gasCalculator, mcps, ccps, null);
    }

    protected EvmTxProcessor(
            final HederaMutableWorldState worldState,
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource) {
        super(worldState, livePricesSource, dynamicProperties, gasCalculator, mcps, ccps, blockMetaSource);
    }

    /**
     * Executes the {@link MessageFrame} of the EVM transaction. Returns the result as {@link
     * TransactionProcessingResult}
     *
     * @param sender The origin {@link Account} that initiates the transaction
     * @param receiver the priority form of the receiving {@link Address} (i.e., EIP-1014 if
     *     present); or the newly created address
     * @param gasPrice GasPrice to use for gas calculations
     * @param gasLimit Externally provided gas limit
     * @param value transaction value
     * @param payload transaction payload. For Create transactions, the bytecode + constructor
     *     arguments
     * @param contractCreation if this is a contract creation transaction
     * @param isStatic Whether the execution is static
     * @param mirrorReceiver the mirror form of the receiving {@link Address}; or the newly created
     *     address; NOTE that in some cases (e.g. a top-level lazy create as per HIP-583), this
     *     value will equal @param receiver and *will not* be a mirror address
     * @return the result of the EVM execution returned as {@link TransactionProcessingResult}
     */
    protected TransactionProcessingResult execute(
            final Account sender,
            final Address receiver,
            final long gasPrice,
            final long gasLimit,
            final long value,
            final Bytes payload,
            final boolean contractCreation,
            final boolean isStatic,
            final Address mirrorReceiver,
            final BigInteger userOfferedGasPrice,
            final long maxGasAllowanceInTinybars,
            final Account relayer) {
        final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
        final Wei upfrontCost = gasCost.add(value);

        super.setupFields(contractCreation);

        final var chargingResult = chargeForGas(
                gasCost,
                upfrontCost,
                value,
                maxGasAllowanceInTinybars,
                intrinsicGas,
                gasPrice,
                gasLimit,
                isStatic,
                userOfferedGasPrice,
                sender.getId().asEvmAddress(),
                relayer == null ? null : relayer.getId().asEvmAddress(),
                (HederaWorldState.Updater) updater);

        // Enable tracing of contract actions if action sidecars are enabled and this is not a
        // static call
        final HederaTracer.EmitActionSidecars doEmitActionSidecars =
                !isStatic && isSideCarTypeEnabled(SidecarType.CONTRACT_ACTION)
                        ? EmitActionSidecars.ENABLED
                        : EmitActionSidecars.DISABLED;
        final HederaTracer.ValidateActionSidecars doValidateActionSidecars =
                isSidecarValidationEnabled() ? ValidateActionSidecars.ENABLED : ValidateActionSidecars.DISABLED;
        final HederaTracer hederaTracer = new HederaTracer(doEmitActionSidecars, doValidateActionSidecars);
        super.setOperationTracer(hederaTracer);

        try {
            super.execute(sender, receiver, gasPrice, gasLimit, value, payload, isStatic, mirrorReceiver);
        } catch (final ResourceLimitException e) {
            handleResourceLimitExceeded(
                    sender,
                    gasPrice,
                    gasLimit,
                    value,
                    isStatic,
                    userOfferedGasPrice,
                    maxGasAllowanceInTinybars,
                    relayer,
                    gasCost,
                    upfrontCost);
            return createResourceLimitExceededResult(gasPrice, gasLimit, e);
        }

        final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;

        if (isStatic) {
            stateChanges = Map.of();
        } else {
            // Return gas price to accounts
            final long refunded = gasLimit - gasUsed + sbhRefund;
            final Wei refundedWei = Wei.of(refunded * gasPrice);
            if (refundedWei.greaterThan(Wei.ZERO)) {
                final var allowanceCharged = chargingResult.allowanceCharged();
                final var chargedRelayer = chargingResult.relayer();
                if (chargedRelayer != null && allowanceCharged.greaterThan(Wei.ZERO)) {
                    // If allowance has been charged, we always try to refund relayer first
                    if (refundedWei.greaterOrEqualThan(allowanceCharged)) {
                        chargedRelayer.incrementBalance(allowanceCharged);
                        chargingResult.sender().incrementBalance(refundedWei.subtract(allowanceCharged));
                    } else {
                        chargedRelayer.incrementBalance(refundedWei);
                    }
                } else {
                    chargingResult.sender().incrementBalance(refundedWei);
                }
            }
            sendToCoinbase(coinbase, gasLimit - refunded, gasPrice, (HederaWorldState.Updater) updater);
            initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

            if (isSideCarTypeEnabled(SidecarType.CONTRACT_STATE_CHANGE)) {
                stateChanges = ((HederaWorldState.Updater) updater).getFinalStateChanges();
            } else {
                stateChanges = Map.of();
            }

            // Don't let a resource limit exception propagate, since then we charge no gas
            try {
                updater.commit();
            } catch (final ResourceLimitException e) {
                handleResourceLimitExceeded(
                        sender,
                        gasPrice,
                        gasLimit,
                        value,
                        isStatic,
                        userOfferedGasPrice,
                        maxGasAllowanceInTinybars,
                        relayer,
                        gasCost,
                        upfrontCost);
                return createResourceLimitExceededResult(gasPrice, gasLimit, e);
            }
        }

        // Externalise result
        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            return TransactionProcessingResult.successful(
                    initialFrame.getLogs(),
                    gasUsed,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getOutputData(),
                    ((HederaWorldState.Updater) updater).aliases().resolveForEvm(mirrorReceiver),
                    stateChanges,
                    hederaTracer.getActions());
        } else {
            return TransactionProcessingResult.failed(
                    gasUsed,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getRevertReason(),
                    initialFrame.getExceptionalHaltReason(),
                    stateChanges,
                    hederaTracer.getActions());
        }
    }

    private boolean isSideCarTypeEnabled(final SidecarType sidecarType) {
        return ((GlobalDynamicProperties) dynamicProperties).enabledSidecars().contains(sidecarType);
    }

    private boolean isSidecarValidationEnabled() {
        return ((GlobalDynamicProperties) dynamicProperties).validateSidecarsEnabled();
    }

    private void sendToCoinbase(
            final Address coinbase, final long amount, final long gasPrice, final HederaWorldState.Updater updater) {
        final var mutableCoinbase = updater.getOrCreate(coinbase);
        mutableCoinbase.incrementBalance(Wei.of(amount * gasPrice));
    }

    private ChargingResult chargeForGas(
            final Wei gasCost,
            final Wei upfrontCost,
            final long value,
            final long maxGasAllowanceInTinybars,
            final long intrinsicGas,
            final long gasPrice,
            final long gasLimit,
            final boolean isStatic,
            final BigInteger userOfferedGasPrice,
            final Address sender,
            @Nullable final Address relayer,
            final HederaWorldState.Updater updater) {
        final var senderAccount = updater.getOrCreateSenderAccount(sender);

        var allowanceCharged = Wei.ZERO;
        MutableAccount mutableRelayer = null;
        if (relayer != null) {
            mutableRelayer = updater.getOrCreateSenderAccount(relayer);
        }
        if (!isStatic) {
            if (intrinsicGas > gasLimit) {
                throw new InvalidTransactionException(INSUFFICIENT_GAS);
            }
            if (relayer == null) {
                final var senderCanAffordGas = senderAccount.getBalance().compareTo(upfrontCost) >= 0;
                validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                senderAccount.decrementBalance(gasCost);
            } else {
                final var gasAllowance = Wei.of(maxGasAllowanceInTinybars);
                if (userOfferedGasPrice.equals(BigInteger.ZERO)) {
                    // If sender set gas price to 0, relayer pays all the fees
                    validateTrue(gasAllowance.greaterOrEqualThan(gasCost), INSUFFICIENT_TX_FEE);
                    final var relayerCanAffordGas = mutableRelayer.getBalance().compareTo((gasCost)) >= 0;
                    validateTrue(relayerCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                    mutableRelayer.decrementBalance(gasCost);
                    allowanceCharged = gasCost;
                } else if (userOfferedGasPrice.divide(WEIBARS_TO_TINYBARS).compareTo(BigInteger.valueOf(gasPrice))
                        < 0) {
                    // If sender gas price < current gas price, pay the difference from gas
                    // allowance
                    final var senderFee = Wei.of(userOfferedGasPrice
                            .multiply(BigInteger.valueOf(gasLimit))
                            .divide(WEIBARS_TO_TINYBARS));
                    validateTrue(senderAccount.getBalance().compareTo(senderFee) >= 0, INSUFFICIENT_PAYER_BALANCE);
                    final var remainingFee = gasCost.subtract(senderFee);
                    validateTrue(gasAllowance.greaterOrEqualThan(remainingFee), INSUFFICIENT_TX_FEE);
                    validateTrue(mutableRelayer.getBalance().compareTo(remainingFee) >= 0, INSUFFICIENT_PAYER_BALANCE);
                    senderAccount.decrementBalance(senderFee);
                    mutableRelayer.decrementBalance(remainingFee);
                    allowanceCharged = remainingFee;
                } else {
                    // If user gas price >= current gas price, sender pays all fees
                    final var senderCanAffordGas = senderAccount.getBalance().compareTo(gasCost) >= 0;
                    validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                    senderAccount.decrementBalance(gasCost);
                }
                // In any case, the sender must have sufficient balance to pay for any value sent
                final var senderCanAffordValue = senderAccount.getBalance().compareTo(Wei.of(value)) >= 0;
                validateTrue(senderCanAffordValue, INSUFFICIENT_PAYER_BALANCE);
            }
        }
        return new ChargingResult(senderAccount, mutableRelayer, allowanceCharged);
    }

    private void handleResourceLimitExceeded(
            final Account sender,
            final long gasPrice,
            final long gasLimit,
            final long value,
            final boolean isStatic,
            final BigInteger userOfferedGasPrice,
            final long maxGasAllowanceInTinybars,
            final Account relayer,
            final Wei gasCost,
            final Wei upfrontCost) {
        // Consume all gas on resource exhaustion, using a clean updater
        final var feesOnlyUpdater = (HederaWorldState.Updater) worldState.updater();
        chargeForGas(
                gasCost,
                upfrontCost,
                value,
                maxGasAllowanceInTinybars,
                intrinsicGas,
                gasPrice,
                gasLimit,
                isStatic,
                userOfferedGasPrice,
                sender.getId().asEvmAddress(),
                relayer == null ? null : relayer.getId().asEvmAddress(),
                feesOnlyUpdater);
        sendToCoinbase(coinbase, gasLimit, gasPrice, feesOnlyUpdater);
        // We can't go through the top-level commit() because that would
        // re-try to commit the storage changes
        feesOnlyUpdater.trackingAccounts().commit();
    }

    private TransactionProcessingResult createResourceLimitExceededResult(
            final long gasPrice, final long gasLimit, final ResourceLimitException e) {
        return TransactionProcessingResult.failed(
                gasLimit,
                0,
                gasPrice,
                Optional.of(e.messageBytes()),
                Optional.empty(),
                Collections.emptyMap(),
                List.of());
    }
}
