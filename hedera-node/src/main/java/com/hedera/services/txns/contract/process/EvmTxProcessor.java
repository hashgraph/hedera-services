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
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.evm.HederaMutableAccount;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.GasAndAccessedState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BerlinTransactionGasCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.TransactionGasCalculator;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;

abstract class EvmTxProcessor {

	private static final int MAX_STACK_SIZE = 1024;

	private final HbarCentExchange exchange;
	private final GasCalculator gasCalculator;
	// TODO this must NOT be a property but a new instance must be created for every `execute`
	protected final HederaWorldUpdater worldState;
	private final UsagePricesProvider usagePrices;
	protected final GlobalDynamicProperties dynamicProperties;
	private final AbstractMessageProcessor messageCallProcessor;
	private final TransactionGasCalculator transactionGasCalculator;
	private final AbstractMessageProcessor contractCreationProcessor;

	public EvmTxProcessor(
			final HbarCentExchange exchange,
			final HederaWorldUpdater worldState,
			final UsagePricesProvider usagePrices,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.exchange = exchange;
		this.worldState = worldState;
		this.usagePrices = usagePrices;
		this.dynamicProperties = dynamicProperties;
		this.transactionGasCalculator = new BerlinTransactionGasCalculator();

		final var evm = MainnetEVMs.berlin();
		this.gasCalculator = evm.getGasCalculator();
		final PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
		MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, gasCalculator);

		this.messageCallProcessor = new MessageCallProcessor(evm, precompileContractRegistry);
		this.contractCreationProcessor = new ContractCreationProcessor(
				gasCalculator,
				evm,
				true,
				List.of(MaxCodeSizeRule.of(MainnetProtocolSpecs.SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT), PrefixCodeRule.of()),
				1);
	}

	/**
	 * TODO we must extend the {@link Transaction} object with new properties such as `memo`, `proxyAccount` and `adminKey`
	 */
	protected void execute (Account sender, final Transaction transaction, Instant consensusTime) {

		final var gasPrice = transaction.getGasPrice().get();
		final Wei upfrontCost = Wei.of(transaction.getGasLimit()).multiply(gasPrice).add(transaction.getValue());
		final GasAndAccessedState gasAndAccessedState =
				transactionGasCalculator.transactionIntrinsicGasCostAndAccessedState(transaction);
		final Gas intrinsicGas = gasAndAccessedState.getGas();

		validateFalse(upfrontCost.compareTo(Wei.of(sender.getBalance())) > 0,
				ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);
		validateFalse(intrinsicGas.compareTo(Gas.of(transaction.getGasLimit())) > 0,
				ResponseCodeEnum.INSUFFICIENT_GAS);

		final Address coinbase = Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress();
		final HederaBlockHeader blockHeader = new HederaBlockHeader(coinbase, transaction.getGasLimit(), consensusTime.getEpochSecond());
		final HederaMutableAccount mutableSender = (HederaMutableAccount) worldState.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		mutableSender.decrementBalance(upfrontCost);

		final Gas gasAvailable = Gas.of(transaction.getGasLimit()).minus(intrinsicGas);
		final WorldUpdater worldUpdater = worldState.updater();
		final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
		/*
		  TODO Do we need those variables
		  final var contextVariablesBuilder =
		            ImmutableMap.<String, Object>builder()
		                .put(KEY_IS_PERSISTING_PRIVATE_STATE, isPersistingPrivateState)
		                .put(KEY_TRANSACTION, transaction)
		                .put(KEY_TRANSACTION_HASH, transaction.getHash());
		 */

		final MessageFrame.Builder commonInitialFrame =
				MessageFrame.builder()
						.messageFrameStack(messageFrameStack)
						.maxStackSize(MAX_STACK_SIZE)
						.worldUpdater(worldUpdater.updater())
						.initialGas(gasAvailable)
						.originator(transaction.getSender())
						.gasPrice(gasPrice)
						.sender(transaction.getSender())
						.value(transaction.getValue())
						.apparentValue(transaction.getValue())
						.blockHeader(blockHeader)
						.depth(0)
						.completer(__ -> {})
						.miningBeneficiary(coinbase)
						.blockHashLookup(h -> null);
		final MessageFrame initialFrame = buildInitialFrame(commonInitialFrame, transaction);
		messageFrameStack.addFirst(initialFrame);

		while (!messageFrameStack.isEmpty()) {
			process(messageFrameStack.peekFirst(), OperationTracer.NO_TRACING);
		}

		if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
			worldUpdater.commit();
		}

		/* Return leftover gas */
		final Gas selfDestructRefund =
				gasCalculator.getSelfDestructRefundAmount().times(initialFrame.getSelfDestructs().size());
		final Gas refundGas = initialFrame.getGasRefund().plus(selfDestructRefund);
		final Gas refunded = refunded(transaction, initialFrame.getRemainingGas(), refundGas);
		final Wei refundedWei = refunded.priceFor(gasPrice);
		mutableSender.incrementBalance(refundedWei);

		/* Send TX fees to coinbase */
		final Gas gasUsedByTransaction =
				Gas.of(transaction.getGasLimit()).minus(initialFrame.getRemainingGas());
		final var mutableCoinbase = worldState.getOrCreate(coinbase).getMutable();
		final Gas coinbaseFee = Gas.of(transaction.getGasLimit()).minus(refunded);
		mutableCoinbase.incrementBalance(coinbaseFee.priceFor(gasPrice));

		initialFrame.getSelfDestructs().forEach(worldState::deleteAccount);

		// TODO we must call worldState.persist() maybe in the Transition logic. Externalising the results there as-well

		/* Externalise Result */
		// TODO
	}

	protected long gasPriceTinyBarsGiven(Instant consensusTime) {
		final var functionType = getFunctionType();
		final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
		FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
		long feeInTinyCents = prices.getServicedata().getGas() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}

	protected abstract HederaFunctionality getFunctionType();

	protected abstract MessageFrame buildInitialFrame(MessageFrame.Builder baseInitialFrame, Transaction transaction);

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

	private Gas refunded(final Transaction transaction, final Gas gasRemaining, final Gas gasRefund) {
		// Integer truncation takes care of the the floor calculation needed after the divide.
		final Gas maxRefundAllowance =
				Gas.of(transaction.getGasLimit())
						.minus(gasRemaining)
						.dividedBy(transactionGasCalculator.getMaxRefundQuotient());
		final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
		return gasRemaining.plus(refundAllowance);
	}
}

