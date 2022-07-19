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

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.txns.contract.helpers.StorageExpiry;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Abstract processor of EVM transactions that prepares the {@link EVM} and all of the peripherals
 * upon instantiation. Provides a base {@link EvmTxProcessor#execute(Account, Address, long, long,
 * long, Bytes, boolean, Instant, boolean, StorageExpiry.Oracle, Address, BigInteger, long,
 * Account)} method that handles the end-to-end execution of a EVM transaction.
 */
abstract class EvmTxProcessor {
    private static final int MAX_STACK_SIZE = 1024;
    private static final int MAX_CODE_SIZE = 0x6000;
    private static final List<ContractValidationRule> VALIDATION_RULES =
            List.of(MaxCodeSizeRule.of(MAX_CODE_SIZE), PrefixCodeRule.of());

    public static final String SBH_CONTEXT_KEY = "sbh";
    public static final String EXPIRY_ORACLE_CONTEXT_KEY = "expiryOracle";

    private BlockMetaSource blockMetaSource;
    private HederaMutableWorldState worldState;

    private final GasCalculator gasCalculator;
    private final LivePricesSource livePricesSource;
    private final AbstractMessageProcessor messageCallProcessor;
    private final AbstractMessageProcessor contractCreationProcessor;
    protected final GlobalDynamicProperties dynamicProperties;

    protected EvmTxProcessor(
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Set<Operation> hederaOperations,
            final Map<String, PrecompiledContract> precompiledContractMap) {
        this(
                null,
                livePricesSource,
                dynamicProperties,
                gasCalculator,
                hederaOperations,
                precompiledContractMap,
                null);
    }

    protected void setBlockMetaSource(final BlockMetaSource blockMetaSource) {
        this.blockMetaSource = blockMetaSource;
    }

    protected void setWorldState(final HederaMutableWorldState worldState) {
        this.worldState = worldState;
    }

    protected EvmTxProcessor(
            final HederaMutableWorldState worldState,
            final LivePricesSource livePricesSource,
            final GlobalDynamicProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Set<Operation> hederaOperations,
            final Map<String, PrecompiledContract> precompiledContractMap,
            final BlockMetaSource blockMetaSource) {
        this.worldState = worldState;
        this.livePricesSource = livePricesSource;
        this.dynamicProperties = dynamicProperties;
        this.gasCalculator = gasCalculator;

        var operationRegistry = new OperationRegistry();
        registerLondonOperations(
                operationRegistry, gasCalculator, BigInteger.valueOf(dynamicProperties.chainId()));
        hederaOperations.forEach(operationRegistry::put);

        final var evm = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);
        final var precompileContractRegistry = new PrecompileContractRegistry();
        MainnetPrecompiledContracts.populateForIstanbul(
                precompileContractRegistry, this.gasCalculator);

        this.messageCallProcessor =
                new HederaMessageCallProcessor(
                        evm, precompileContractRegistry, precompiledContractMap);
        this.contractCreationProcessor =
                new ContractCreationProcessor(gasCalculator, evm, true, VALIDATION_RULES, 1);
        this.blockMetaSource = blockMetaSource;
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
     * @param value Evm transaction value (HBars)
     * @param payload Transaction payload. For Create transactions, the bytecode + constructor
     *     arguments
     * @param contractCreation Whether or not this is a contract creation transaction
     * @param consensusTime Current consensus time
     * @param isStatic Whether the execution is static
     * @param expiryOracle the oracle to use when determining the expiry of newly allocated storage
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
            final Instant consensusTime,
            final boolean isStatic,
            final StorageExpiry.Oracle expiryOracle,
            final Address mirrorReceiver,
            final BigInteger userOfferedGasPrice,
            final long maxGasAllowanceInTinybars,
            final Account relayer) {
        final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
        final Wei upfrontCost = gasCost.add(value);
        final long intrinsicGas =
                gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, contractCreation);

        final HederaWorldState.Updater updater = (HederaWorldState.Updater) worldState.updater();
        final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
        final MutableAccount mutableSender = senderAccount.getMutable();

        var allowanceCharged = Wei.ZERO;
        MutableAccount mutableRelayer = null;
        if (relayer != null) {
            final var relayerAccount =
                    updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress());
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

        final var coinbase = Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress();
        final var blockValues = blockMetaSource.computeBlockValues(gasLimit);
        final var gasAvailable = gasLimit - intrinsicGas;
        final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

        final var valueAsWei = Wei.of(value);
        final var stackedUpdater = updater.updater();
        final var senderEvmAddress = sender.canonicalAddress();
        final MessageFrame.Builder commonInitialFrame =
                MessageFrame.builder()
                        .messageFrameStack(messageFrameStack)
                        .maxStackSize(MAX_STACK_SIZE)
                        .worldUpdater(stackedUpdater)
                        .initialGas(gasAvailable)
                        .originator(senderEvmAddress)
                        .gasPrice(Wei.of(gasPrice))
                        .sender(senderEvmAddress)
                        .value(valueAsWei)
                        .apparentValue(valueAsWei)
                        .blockValues(blockValues)
                        .depth(0)
                        .completer(unused -> {})
                        .isStatic(isStatic)
                        .miningBeneficiary(coinbase)
                        .blockHashLookup(blockMetaSource::getBlockHash)
                        .contextVariables(
                                Map.of(
                                        "sbh",
                                        storageByteHoursTinyBarsGiven(consensusTime),
                                        "HederaFunctionality",
                                        getFunctionType(),
                                        EXPIRY_ORACLE_CONTEXT_KEY,
                                        expiryOracle));

        final MessageFrame initialFrame =
                buildInitialFrame(commonInitialFrame, receiver, payload, value);
        messageFrameStack.addFirst(initialFrame);

        while (!messageFrameStack.isEmpty()) {
            process(messageFrameStack.peekFirst(), new HederaTracer());
        }

        var gasUsedByTransaction = calculateGasUsedByTX(gasLimit, initialFrame);
        final long sbhRefund = updater.getSbhRefund();
        final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;

        if (isStatic) {
            stateChanges = Map.of();
        } else {
            // return gas price to accounts
            final long refunded = gasLimit - gasUsedByTransaction + sbhRefund;
            final Wei refundedWei = Wei.of(refunded * gasPrice);

            if (refundedWei.greaterThan(Wei.ZERO)) {
                if (relayer != null && allowanceCharged.greaterThan(Wei.ZERO)) {
                    // If allowance has been charged, we always try to refund relayer first
                    if (refundedWei.greaterOrEqualThan(allowanceCharged)) {
                        mutableRelayer.incrementBalance(allowanceCharged);
                        mutableSender.incrementBalance(refundedWei.subtract(allowanceCharged));
                    } else {
                        mutableRelayer.incrementBalance(refundedWei);
                    }
                } else {
                    mutableSender.incrementBalance(refundedWei);
                }
            }

            // Send fees to coinbase
            final var mutableCoinbase = updater.getOrCreate(coinbase).getMutable();
            final long coinbaseFee = gasLimit - refunded;

            mutableCoinbase.incrementBalance(Wei.of(coinbaseFee * gasPrice));
            initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

            if (dynamicProperties.enabledSidecars().contains(SidecarType.CONTRACT_STATE_CHANGE)) {
                stateChanges = updater.getFinalStateChanges();
            } else {
                stateChanges = Map.of();
            }

            // Commit top level updater
            updater.commit();
        }

        // Externalise result
        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            return TransactionProcessingResult.successful(
                    initialFrame.getLogs(),
                    gasUsedByTransaction,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getOutputData(),
                    mirrorReceiver,
                    stateChanges);
        } else {
            return TransactionProcessingResult.failed(
                    gasUsedByTransaction,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getRevertReason(),
                    initialFrame.getExceptionalHaltReason(),
                    stateChanges);
        }
    }

    private long calculateGasUsedByTX(final long txGasLimit, final MessageFrame initialFrame) {
        long gasUsedByTransaction = txGasLimit - initialFrame.getRemainingGas();
        /* Return leftover gas */
        final long selfDestructRefund =
                gasCalculator.getSelfDestructRefundAmount()
                        * Math.min(
                                initialFrame.getSelfDestructs().size(),
                                gasUsedByTransaction / (gasCalculator.getMaxRefundQuotient()));

        gasUsedByTransaction =
                gasUsedByTransaction - selfDestructRefund - initialFrame.getGasRefund();

        final var maxRefundPercent = dynamicProperties.maxGasRefundPercentage();
        gasUsedByTransaction =
                Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

        return gasUsedByTransaction;
    }

    protected long gasPriceTinyBarsGiven(final Instant consensusTime, boolean isEthTxn) {
        return livePricesSource.currentGasPrice(
                consensusTime,
                isEthTxn ? HederaFunctionality.EthereumTransaction : getFunctionType());
    }

    protected long storageByteHoursTinyBarsGiven(final Instant consensusTime) {
        return livePricesSource.currentGasPrice(consensusTime, getFunctionType());
    }

    protected abstract HederaFunctionality getFunctionType();

    protected abstract MessageFrame buildInitialFrame(
            MessageFrame.Builder baseInitialFrame, Address to, Bytes payload, final long value);

    protected void process(final MessageFrame frame, final OperationTracer operationTracer) {
        final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());

        executor.process(frame, operationTracer);
    }

    private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
        switch (type) {
            case MESSAGE_CALL:
                return messageCallProcessor;
            case CONTRACT_CREATION:
                return contractCreationProcessor;
            default:
                throw new IllegalStateException(
                        "Request for unsupported message processor type " + type);
        }
    }
}
