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
import com.hedera.services.store.contracts.world.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeBuilder;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;

abstract class EvmTxProcessor {

	private static final int MAX_STACK_SIZE = 1024;

	private final HbarCentExchange exchange;
	private final GasCalculator gasCalculator;
	private final UsagePricesProvider usagePrices;
	protected final HederaWorldState.Updater updater;
	protected final GlobalDynamicProperties dynamicProperties;
	private final AbstractMessageProcessor messageCallProcessor;
	private final AbstractMessageProcessor contractCreationProcessor;

	public EvmTxProcessor(
			final HbarCentExchange exchange,
			final UsagePricesProvider usagePrices,
			final HederaWorldState.Updater updater,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.updater = updater;
		this.exchange = exchange;
		this.usagePrices = usagePrices;
		this.dynamicProperties = dynamicProperties;
		this.gasCalculator = new LondonGasCalculator();

		final var evm = MainnetEVMs.london();
		final PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
		MainnetPrecompiledContracts.populateForIstanbul(precompileContractRegistry, this.gasCalculator);

		this.messageCallProcessor = new MessageCallProcessor(evm, precompileContractRegistry);
		this.contractCreationProcessor = new ContractCreationProcessor(
				gasCalculator,
				evm,
				true,
				List.of(MaxCodeSizeRule.of(MainnetProtocolSpecs.SPURIOUS_DRAGON_CONTRACT_SIZE_LIMIT), PrefixCodeRule.of()),
				1);
	}

	// TODO we can remove the Transaction object
	protected TransactionProcessingResult execute (Account sender, final Transaction transaction, Instant consensusTime) {
		try {
			//noinspection OptionalGetWithoutIsPresent
			final var gasPrice = transaction.getGasPrice().get();
			final Wei upfrontCost = Wei.of(transaction.getGasLimit()).multiply(gasPrice).add(transaction.getValue());
			final Gas intrinsicGas =
					gasCalculator.transactionIntrinsicGasCost(transaction.getPayload(), transaction.isContractCreation());

			validateFalse(upfrontCost.compareTo(Wei.of(sender.getBalance())) > 0,
					ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);
			validateFalse(intrinsicGas.compareTo(Gas.of(transaction.getGasLimit())) > 0,
					ResponseCodeEnum.INSUFFICIENT_GAS);

			final Address coinbase = Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress();
			final HederaBlockHeader blockHeader = new HederaBlockHeader(coinbase, transaction.getGasLimit(), consensusTime.getEpochSecond());
			final MutableAccount mutableSender = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()).getMutable();
			mutableSender.decrementBalance(upfrontCost);

			final Gas gasAvailable = Gas.of(transaction.getGasLimit()).minus(intrinsicGas);
			final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();

			final var stackedUpdater = updater.updater();
			final MessageFrame.Builder commonInitialFrame =
					MessageFrame.builder()
							.messageFrameStack(messageFrameStack)
							.maxStackSize(MAX_STACK_SIZE)
							.worldUpdater(stackedUpdater)
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
				stackedUpdater.commit();
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
			final var mutableCoinbase = updater.getOrCreate(coinbase).getMutable();
			final Gas coinbaseFee = Gas.of(transaction.getGasLimit()).minus(refunded);
			mutableCoinbase.incrementBalance(coinbaseFee.priceFor(gasPrice));

			initialFrame.getSelfDestructs().forEach(updater::deleteAccount);

			updater.commit();

			/* Externalise Result */
			if (initialFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
				return TransactionProcessingResult.successful(
						initialFrame.getLogs(),
						gasUsedByTransaction.toLong(),
						refunded.toLong(),
						initialFrame.getOutputData(),
						null);
			} else {
				return TransactionProcessingResult.failed(
						gasUsedByTransaction.toLong(),
						refunded.toLong(),
						null,
						initialFrame.getRevertReason());
			}
		} catch (RuntimeException re) {
			return TransactionProcessingResult.invalid(
					ValidationResult.invalid(
							TransactionInvalidReason.INTERNAL_ERROR,
							"Internal Error in Besu - " + re.toString()));
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
						.dividedBy(gasCalculator.getMaxRefundQuotient());
		final Gas refundAllowance = maxRefundAllowance.min(gasRefund);
		return gasRemaining.plus(refundAllowance);
	}
}

