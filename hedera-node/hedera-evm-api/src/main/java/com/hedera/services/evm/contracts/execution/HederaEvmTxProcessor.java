/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.contracts.execution;

import com.hedera.services.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.services.evm.store.contracts.HederaEvmWorldUpdater;
import com.hedera.services.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Abstract processor of EVM transactions that prepares the {@link EVM} and all the peripherals upon
 * instantiation. Provides a base {@link HederaEvmTxProcessor#execute(HederaEvmAccount, Address,
 * long, long, long, Bytes, boolean, boolean, Address)} method that handles the end-to-end execution
 * of an EVM transaction.
 */
public abstract class HederaEvmTxProcessor {
    private static final int MAX_STACK_SIZE = 1024;

    protected BlockMetaSource blockMetaSource;
    protected HederaEvmMutableWorldState worldState;

    protected final GasCalculator gasCalculator;
    protected final PricesAndFeesProvider pricesAndFeesProvider;
    protected final Map<String, Provider<MessageCallProcessor>> mcps;
    protected final Map<String, Provider<ContractCreationProcessor>> ccps;
    protected AbstractMessageProcessor messageCallProcessor;
    protected AbstractMessageProcessor contractCreationProcessor;
    protected HederaEvmOperationTracer tracer;
    protected EvmProperties dynamicProperties;
    protected Address coinbase;
    protected HederaEvmWorldUpdater updater;
    protected long intrinsicGas;
    protected MessageFrame initialFrame;
    protected long gasUsed;
    protected long sbhRefund;

    protected HederaEvmTxProcessor(
            final PricesAndFeesProvider pricesAndFeesProvider,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps) {
        this(null, pricesAndFeesProvider, dynamicProperties, gasCalculator, mcps, ccps, null);
    }

    public void setBlockMetaSource(final BlockMetaSource blockMetaSource) {
        this.blockMetaSource = blockMetaSource;
    }

    public void setWorldState(final HederaEvmMutableWorldState worldState) {
        this.worldState = worldState;
    }

    public void setOperationTracer(final HederaEvmOperationTracer tracer) {
        this.tracer = tracer;
    }

    protected HederaEvmTxProcessor(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider pricesAndFeesProvider,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource) {
        this.worldState = worldState;
        this.pricesAndFeesProvider = pricesAndFeesProvider;
        this.dynamicProperties = dynamicProperties;
        this.gasCalculator = gasCalculator;

        this.mcps = mcps;
        this.ccps = ccps;
        this.messageCallProcessor = mcps.get(dynamicProperties.evmVersion()).get();
        this.contractCreationProcessor = ccps.get(dynamicProperties.evmVersion()).get();
        this.blockMetaSource = blockMetaSource;
    }

    /**
     * Executes the {@link MessageFrame} of the EVM transaction and fills execution results into a
     * field.
     *
     * @param sender The origin {@link EvmAccount} that initiates the transaction
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
     */
    protected HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long gasPrice,
            final long gasLimit,
            final long value,
            final Bytes payload,
            final boolean contractCreation,
            final boolean isStatic,
            final Address mirrorReceiver) {
        this.intrinsicGas =
                gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, contractCreation);
        this.updater = worldState.updater();
        this.coinbase = dynamicProperties.fundingAccountAddress();
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
                        .contextVariables(Map.of("HederaFunctionality", getFunctionType()));

        this.initialFrame = buildInitialFrame(commonInitialFrame, receiver, payload, value);
        messageFrameStack.addFirst(initialFrame);

        tracer.init(initialFrame);

        if (dynamicProperties.dynamicEvmVersion()) {
            String evmVersion = dynamicProperties.evmVersion();
            messageCallProcessor = mcps.get(evmVersion).get();
            contractCreationProcessor = ccps.get(evmVersion).get();
        }

        while (!messageFrameStack.isEmpty()) {
            process(messageFrameStack.peekFirst(), tracer);
        }

        this.gasUsed = calculateGasUsedByTX(gasLimit, initialFrame);
        this.sbhRefund = updater.getSbhRefund();

        // Externalise result
        if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            return HederaEvmTransactionProcessingResult.successful(
                    initialFrame.getLogs(),
                    gasUsed,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getOutputData(),
                    mirrorReceiver);
        } else {
            return HederaEvmTransactionProcessingResult.failed(
                    gasUsed,
                    sbhRefund,
                    gasPrice,
                    initialFrame.getRevertReason(),
                    initialFrame.getExceptionalHaltReason());
        }
    }

    protected long calculateGasUsedByTX(final long txGasLimit, final MessageFrame initialFrame) {
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
        return pricesAndFeesProvider.currentGasPrice(
                consensusTime,
                isEthTxn ? HederaFunctionality.EthereumTransaction : getFunctionType());
    }

    protected abstract HederaFunctionality getFunctionType();

    protected abstract MessageFrame buildInitialFrame(
            MessageFrame.Builder baseInitialFrame, Address to, Bytes payload, final long value);

    protected void process(final MessageFrame frame, final OperationTracer operationTracer) {
        final AbstractMessageProcessor executor = getMessageProcessor(frame.getType());

        executor.process(frame, operationTracer);
    }

    private AbstractMessageProcessor getMessageProcessor(final MessageFrame.Type type) {
        return switch (type) {
            case MESSAGE_CALL -> messageCallProcessor;
            case CONTRACT_CREATION -> contractCreationProcessor;
        };
    }
}
