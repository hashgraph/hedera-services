package com.hedera.services.txns.contract.process;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.SoliditySigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.contract.gascalculator.GasCalculatorHedera_0_19_0;
import com.hedera.services.txns.contract.operation.HederaBalanceOperation;
import com.hedera.services.txns.contract.operation.HederaCallCodeOperation;
import com.hedera.services.txns.contract.operation.HederaCallOperation;
import com.hedera.services.txns.contract.operation.HederaCreateOperation;
import com.hedera.services.txns.contract.operation.HederaDelegateCallOperation;
import com.hedera.services.txns.contract.operation.HederaExtCodeCopyOperation;
import com.hedera.services.txns.contract.operation.HederaExtCodeHashOperation;
import com.hedera.services.txns.contract.operation.HederaExtCodeSizeOperation;
import com.hedera.services.txns.contract.operation.HederaSStoreOperation;
import com.hedera.services.txns.contract.operation.HederaSelfDestructOperation;
import com.hedera.services.txns.contract.operation.HederaStaticCallOperation;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.operation.InvalidOperation;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static org.hyperledger.besu.evm.MainnetEVMs.registerLondonOperations;

abstract class EvmTxProcessor {

	private static final int MAX_STACK_SIZE = 1024;

	private final HederaWorldState worldState;
	private final HbarCentExchange exchange;
	private final GasCalculator gasCalculator;
	private final UsagePricesProvider usagePrices;
	protected final GlobalDynamicProperties dynamicProperties;
	private final AbstractMessageProcessor messageCallProcessor;
	private final AbstractMessageProcessor contractCreationProcessor;

	public EvmTxProcessor(
			final SoliditySigsVerifier sigsVerifier,
			final HederaWorldState worldState,
			final HbarCentExchange exchange,
			final UsagePricesProvider usagePrices,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.worldState = worldState;
		this.exchange = exchange;
		this.usagePrices = usagePrices;
		this.dynamicProperties = dynamicProperties;
		this.gasCalculator = new GasCalculatorHedera_0_19_0(dynamicProperties, usagePrices, exchange);

		var operationRegistry = new OperationRegistry();
		registerLondonOperations(operationRegistry, gasCalculator, BigInteger.valueOf(dynamicProperties.getChainId()));
		/* Register customized Hedera Opcodes */
		operationRegistry.put(new HederaBalanceOperation(gasCalculator));
		operationRegistry.put(new HederaCallCodeOperation(sigsVerifier, gasCalculator));
		operationRegistry.put(new HederaCallOperation(sigsVerifier, gasCalculator));
		operationRegistry.put(new HederaCreateOperation(gasCalculator));
		operationRegistry.put(new HederaDelegateCallOperation(gasCalculator));
		operationRegistry.put(new HederaExtCodeCopyOperation(gasCalculator));
		operationRegistry.put(new HederaExtCodeHashOperation(gasCalculator));
		operationRegistry.put(new HederaExtCodeSizeOperation(gasCalculator));
		operationRegistry.put(new HederaSelfDestructOperation(gasCalculator));
		operationRegistry.put(new HederaSStoreOperation(gasCalculator));
		operationRegistry.put(new HederaStaticCallOperation(gasCalculator));
		/* Deregister CREATE2 Opcode */
		operationRegistry.put(new InvalidOperation(0xF5, gasCalculator));

		var evm = new EVM(operationRegistry, gasCalculator, EvmConfiguration.DEFAULT);

		final PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
		MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, this.gasCalculator);

		this.messageCallProcessor = new MessageCallProcessor(evm, precompileContractRegistry);
		this.contractCreationProcessor = new ContractCreationProcessor(
				gasCalculator,
				evm,
				true,
				List.of(MaxCodeSizeRule.of(0x6000), //FIXME magic constant
						PrefixCodeRule.of()),
				1);
	}

	protected TransactionProcessingResult execute(Account sender, Address receiver, long gasPrice,
												  long providedGasLimit, long value, Bytes payload, boolean contractCreation,
												  Instant consensusTime, boolean isStatic, Optional<Long> expiry) {
		try {
			final long gasLimit = providedGasLimit > dynamicProperties.maxGas()
					? dynamicProperties.maxGas()
					: providedGasLimit;
			final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
			final Wei upfrontCost = gasCost.add(value);
			final Gas intrinsicGas =
					gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, contractCreation);
			final var updater = worldState.updater();

			if (!isStatic) {
				validateFalse(upfrontCost.compareTo(Wei.of(sender.getBalance())) > 0,
						ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE);
				if (intrinsicGas.toLong() > gasLimit) {
					throw new InvalidTransactionException(
							gasLimit < dynamicProperties.maxGas()
									? INSUFFICIENT_GAS
									: MAX_GAS_LIMIT_EXCEEDED);
				}
			}

			final Address coinbase = Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress();
			final HederaBlockValues blockValues = new HederaBlockValues(gasLimit,
					consensusTime.getEpochSecond());
			Address senderEvmAddress = sender.getId().asEvmAddress();
			final MutableAccount mutableSender = updater.getOrCreateSenderAccount(senderEvmAddress).getMutable();
			if (!isStatic) {
				mutableSender.decrementBalance(gasCost);
			}

			final Gas gasAvailable = Gas.of(gasLimit).minus(intrinsicGas);
			final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

			final var stackedUpdater = updater.updater();
			Wei valueAsWei = Wei.of(value);
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
							.completer(__ -> {
							})
							.isStatic(isStatic)
							.miningBeneficiary(coinbase)
							.blockHashLookup(h -> null)
							.contextVariables(Map.of(
									"sbh", storageByteHoursTinyBarsGiven(consensusTime),
									"HederaFunctionality", getFunctionType(),
									"expiry", expiry));

			final MessageFrame initialFrame = buildInitialFrame(commonInitialFrame,
					updater,
					receiver, payload);
			messageFrameStack.addFirst(initialFrame);

			while (!messageFrameStack.isEmpty()) {
				process(messageFrameStack.peekFirst(), new HederaTracer());
			}

			if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS && !isStatic) {
				stackedUpdater.commit();
			}

			Gas gasUsedByTransaction = Gas.of(gasLimit).minus(initialFrame.getRemainingGas());
			/* Return leftover gas */
			final Gas selfDestructRefund =
					gasCalculator.getSelfDestructRefundAmount().times(initialFrame.getSelfDestructs().size()).min(
							gasUsedByTransaction.dividedBy(gasCalculator.getMaxRefundQuotient()));
			gasUsedByTransaction = gasUsedByTransaction.minus(selfDestructRefund);

			if (!isStatic) {
				// return gas price to accounts
				final Gas refunded = Gas.of(gasLimit).minus(gasUsedByTransaction).plus(initialFrame.getGasRefund());
				final Wei refundedWei = refunded.priceFor(Wei.of(gasPrice));

				mutableSender.incrementBalance(refundedWei);

				/* Send TX fees to coinbase */
				final var mutableCoinbase = updater.getOrCreate(coinbase).getMutable();
				final Gas coinbaseFee = Gas.of(gasLimit).minus(refunded);

				mutableCoinbase.incrementBalance(coinbaseFee.priceFor(Wei.of(gasPrice)));
				initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

				/* Commit top level Updater */
				updater.commit();
			}

			/* Externalise Result */
			if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
				final List<Log> logs = initialFrame.getLogs();
				final var bloom = LogsBloomFilter.builder().insertLogs(logs).build();
				return TransactionProcessingResult.successful(
						logs,
						Optional.of(bloom),
						gasUsedByTransaction.toLong(),
						gasPrice,
						initialFrame.getOutputData(),
						initialFrame.getRecipientAddress());
			} else {
				return TransactionProcessingResult.failed(
						gasUsedByTransaction.toLong(),
						gasPrice,
						initialFrame.getRevertReason(),
						initialFrame.getExceptionalHaltReason());
			}
		} catch (RuntimeException re) {
			throw new InvalidTransactionException("Internal Error in Besu - " + re, FAIL_INVALID);
		}
	}

	protected long gasPriceTinyBarsGiven(Instant consensusTime) {
		final var functionType = getFunctionType();
		final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
		FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
		long feeInTinyCents = prices.getServicedata().getGas() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}

	protected long storageByteHoursTinyBarsGiven(Instant consensusTime) {
		final var functionType = getFunctionType();
		final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
		FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
		long feeInTinyCents = prices.getServicedata().getSbh() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}

	protected abstract HederaFunctionality getFunctionType();

	protected abstract MessageFrame buildInitialFrame(MessageFrame.Builder baseInitialFrame,
			HederaWorldState.Updater updater, Address to, Bytes payload);

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
				throw new IllegalStateException("Request for unsupported message processor type " + type);
		}
	}
}

