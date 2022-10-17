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
package com.hedera.services.contracts.execution;

import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.traceability.HederaTracer;
import com.hedera.services.evm.contracts.execution.BlockMetaSource;
import com.hedera.services.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.ResourceLimitException;
import com.hedera.services.fees.PricesAndFeesImpl;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.stream.proto.SidecarType;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
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
            final PricesAndFeesImpl pricesAndFees,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps) {
        this(null, pricesAndFees, dynamicProperties, gasCalculator, mcps, ccps, null);
    }

    protected EvmTxProcessor(
            final HederaMutableWorldState worldState,
            final PricesAndFeesImpl pricesAndFees,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource) {
        super(
                worldState,
                pricesAndFees,
                dynamicProperties,
                gasCalculator,
                mcps,
                ccps,
                blockMetaSource);
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
     *     address
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

        // Enable tracing of contract actions if action sidecars are enabled and this is not a
        // static call
        final HederaTracer hederaTracer =
                new HederaTracer(!isStatic && isSideCarTypeEnabled(SidecarType.CONTRACT_ACTION));

        super.setOperationTracer(hederaTracer);
        super.execute(
                sender,
                receiver,
                gasPrice,
                gasLimit,
                value,
                payload,
                contractCreation,
                isStatic,
                mirrorReceiver);

        final var chargingResult =
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
                        (HederaWorldState.Updater) updater);

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
                        chargingResult
                                .sender()
                                .incrementBalance(refundedWei.subtract(allowanceCharged));
                    } else {
                        chargedRelayer.incrementBalance(refundedWei);
                    }
                } else {
                    chargingResult.sender().incrementBalance(refundedWei);
                }
            }
            sendToCoinbase(
                    coinbase, gasLimit - refunded, gasPrice, (HederaWorldState.Updater) updater);
            initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

            if (isSideCarTypeEnabled(SidecarType.CONTRACT_STATE_CHANGE)) {
                stateChanges = ((HederaWorldState.Updater) updater).getFinalStateChanges();
            } else {
                stateChanges = Map.of();
            }

            // Don't let a resource limit exception propagate, since then we charge no gas
            try {
                updater.commit();
            } catch (ResourceLimitException e) {
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

        // Externalise result
        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            return TransactionProcessingResult.successful(
                    initialFrame.getLogs(),
                    gasUsed,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getOutputData(),
                    mirrorReceiver,
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
        return ((GlobalDynamicProperties) dynamicProperties)
                .enabledSidecars()
                .contains(sidecarType);
    }

    private void sendToCoinbase(
            final Address coinbase,
            final long amount,
            final long gasPrice,
            final HederaWorldState.Updater updater) {
        final var mutableCoinbase = updater.getOrCreate(coinbase).getMutable();
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
        final MutableAccount mutableSender = senderAccount.getMutable();

        var allowanceCharged = Wei.ZERO;
        MutableAccount mutableRelayer = null;
        if (relayer != null) {
            final var relayerAccount = updater.getOrCreateSenderAccount(relayer);
            mutableRelayer = relayerAccount.getMutable();
        }
        if (!isStatic) {
            if (intrinsicGas > gasLimit) {
                throw new InvalidTransactionException(INSUFFICIENT_GAS);
            }
            if (relayer == null) {
                final var senderCanAffordGas =
                        mutableSender.getBalance().compareTo(upfrontCost) >= 0;
                validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                mutableSender.decrementBalance(gasCost);
            } else {
                final var gasAllowance = Wei.of(maxGasAllowanceInTinybars);
                if (userOfferedGasPrice.equals(BigInteger.ZERO)) {
                    // If sender set gas price to 0, relayer pays all the fees
                    validateTrue(gasAllowance.greaterOrEqualThan(gasCost), INSUFFICIENT_TX_FEE);
                    final var relayerCanAffordGas =
                            mutableRelayer.getBalance().compareTo((gasCost)) >= 0;
                    validateTrue(relayerCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                    mutableRelayer.decrementBalance(gasCost);
                    allowanceCharged = gasCost;
                } else if (userOfferedGasPrice
                                .divide(WEIBARS_TO_TINYBARS)
                                .compareTo(BigInteger.valueOf(gasPrice))
                        < 0) {
                    // If sender gas price < current gas price, pay the difference from gas
                    // allowance
                    var senderFee =
                            Wei.of(
                                    userOfferedGasPrice
                                            .multiply(BigInteger.valueOf(gasLimit))
                                            .divide(WEIBARS_TO_TINYBARS));
                    validateTrue(
                            mutableSender.getBalance().compareTo(senderFee) >= 0,
                            INSUFFICIENT_PAYER_BALANCE);
                    final var remainingFee = gasCost.subtract(senderFee);
                    validateTrue(
                            gasAllowance.greaterOrEqualThan(remainingFee), INSUFFICIENT_TX_FEE);
                    validateTrue(
                            mutableRelayer.getBalance().compareTo(remainingFee) >= 0,
                            INSUFFICIENT_PAYER_BALANCE);
                    mutableSender.decrementBalance(senderFee);
                    mutableRelayer.decrementBalance(remainingFee);
                    allowanceCharged = remainingFee;
                } else {
                    // If user gas price >= current gas price, sender pays all fees
                    final var senderCanAffordGas =
                            mutableSender.getBalance().compareTo(gasCost) >= 0;
                    validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
                    mutableSender.decrementBalance(gasCost);
                }
                // In any case, the sender must have sufficient balance to pay for any value sent
                final var senderCanAffordValue =
                        mutableSender.getBalance().compareTo(Wei.of(value)) >= 0;
                validateTrue(senderCanAffordValue, INSUFFICIENT_PAYER_BALANCE);
            }
        }
        return new ChargingResult(mutableSender, mutableRelayer, allowanceCharged);
    }
}
