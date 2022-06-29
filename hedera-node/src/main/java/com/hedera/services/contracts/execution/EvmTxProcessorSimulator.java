package com.hedera.services.contracts.execution;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.PrecompileMessage;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.PrecompileMessage.State.COMPLETED_SUCCESS;
import static com.hedera.services.store.contracts.precompile.PrecompileMessage.State.EXCEPTIONAL_HALT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;


@Singleton
public class EvmTxProcessorSimulator {

	private final GasCalculator gasCalculator;
	private final LivePricesSource livePricesSource;
	protected final GlobalDynamicProperties dynamicProperties;
	private final HTSPrecompiledContract htsPrecompiledContract;
	private Optional<ExceptionalHaltReason> exceptionalHaltReason;
	private final HederaMutableWorldState worldState;
	private long intrinsicGas;
	private HederaWorldState.Updater updater;
	private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;
	private long gasUsedByTransaction;
	private MutableAccount mutableSender;


	@Inject
	public EvmTxProcessorSimulator(GasCalculator gasCalculator, LivePricesSource livePricesSource,
								   GlobalDynamicProperties dynamicProperties, HTSPrecompiledContract htsPrecompiledContract, HederaMutableWorldState worldState) {
		this.gasCalculator = gasCalculator;
		this.livePricesSource = livePricesSource;
		this.dynamicProperties = dynamicProperties;
		this.htsPrecompiledContract = htsPrecompiledContract;
		this.worldState = worldState;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final long gasLimit,
			final long value,
			final Bytes payload,
			final Instant consensusTime,
			final Address mirrorReceiver,
			final Id tokenId,
			final WorldLedgers ledgers) {
		populateCommonFields(sender);
		final long gasPrice = getLivePrice(consensusTime);
		final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
		final Wei upfrontCost = gasCost.add(value);

		if (intrinsicGas > gasLimit) {
			throw new InvalidTransactionException(INSUFFICIENT_GAS);
		}
		senderCanAffordGas(gasCost, upfrontCost, mutableSender);

		PrecompileMessage message = constructMessageAndCallPrecompileContract(
				sender, consensusTime, ledgers,
				payload, gasLimit, value, tokenId);
		calculateGasUsedByTransaction(gasLimit, message);

		final long refunded = calculateRefund(gasLimit, gasPrice);
		sendFeesToCoinbase(gasLimit, gasPrice, refunded);

		updater.commit();

		return buildTransactionResult(
				message, gasUsedByTransaction,
				gasPrice, mirrorReceiver, stateChanges);
	}

	public TransactionProcessingResult executeEth(
			final Account sender,
			final long gasLimit,
			final long value,
			final Bytes payload,
			final Instant consensusTime,
			final Address mirrorReceiver,
			final BigInteger userOfferedGasPrice,
			final long maxGasAllowanceInTinybars,
			final Account relayer,
			final Id tokenId,
			final WorldLedgers ledgers) {

		populateCommonFields(sender);
		final long gasPrice = getLivePrice(consensusTime);
		final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
		var allowanceCharged = Wei.ZERO;
		final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		MutableAccount mutableSender = getMutableAccount(senderAccount);
		final var relayerAccount = updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress());
		MutableAccount mutableRelayer = getMutableAccount(relayerAccount);

		if (intrinsicGas > gasLimit) {
			throw new InvalidTransactionException(INSUFFICIENT_GAS);
		}
		final var gasAllowance = Wei.of(maxGasAllowanceInTinybars);

		if (userOfferedGasPrice.equals(BigInteger.ZERO)) {
			// If sender set gas price to 0, relayer pays all the fees
			validateTrue(gasAllowance.greaterOrEqualThan(gasCost), INSUFFICIENT_TX_FEE);
			senderCanAffordGas(gasCost, (gasCost), mutableRelayer);
			allowanceCharged = gasCost;
		} else if (userOfferedGasPrice.divide(WEIBARS_TO_TINYBARS).compareTo(BigInteger.valueOf(gasPrice)) < 0) {
			// If sender gas price < current gas price, pay the difference from gas allowance
			var senderFee =
					Wei.of(userOfferedGasPrice.multiply(BigInteger.valueOf(gasLimit)).divide(WEIBARS_TO_TINYBARS));
			validateTrue(mutableSender.getBalance().compareTo(senderFee) >= 0, INSUFFICIENT_PAYER_BALANCE);
			final var remainingFee = gasCost.subtract(senderFee);
			validateTrue(gasAllowance.greaterOrEqualThan(remainingFee), INSUFFICIENT_TX_FEE);
			validateTrue(mutableRelayer.getBalance().compareTo(remainingFee) >= 0, INSUFFICIENT_PAYER_BALANCE);
			mutableSender.decrementBalance(senderFee);
			mutableRelayer.decrementBalance(remainingFee);
			allowanceCharged = remainingFee;
		} else {
			// If user gas price >= current gas price, sender pays all fees
			senderCanAffordGas(gasCost, gasCost, mutableSender);
		}
		// In any case, the sender must have sufficient balance to pay for any value sent
		final var senderCanAffordValue = mutableSender.getBalance().compareTo(Wei.of(value)) >= 0;
		validateTrue(senderCanAffordValue, INSUFFICIENT_PAYER_BALANCE);
		//construct the PrecompileMessage here
		PrecompileMessage message = constructMessageAndCallPrecompileContract(sender, consensusTime, ledgers,
				payload, gasLimit, value, tokenId);
		calculateGasUsedByTransaction(gasLimit, message);

		final long refunded = calculateRefundEth(gasLimit, gasPrice, mutableRelayer, allowanceCharged);
		// Send fees to coinbase
		sendFeesToCoinbase(gasLimit, gasPrice, refunded);

		updater.commit();
		return buildTransactionResult(
				message, gasUsedByTransaction, gasPrice,
				mirrorReceiver, stateChanges);
	}

	private void populateCommonFields(Account sender) {
		updater = (HederaWorldState.Updater) worldState.updater();
		exceptionalHaltReason = Optional.empty();
		intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false);
		stateChanges = dynamicProperties.shouldEnableTraceability() ?
				updater.getFinalStateChanges() : Map.of();
		final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		mutableSender = getMutableAccount(senderAccount);
	}

	private long getLivePrice(Instant consensusTime) {
		return livePricesSource.currentGasPrice(consensusTime,
				HederaFunctionality.ContractCall);
	}

	private void senderCanAffordGas(Wei gasCost, Wei upfrontCost, MutableAccount mutableAccount) {
		final var senderCanAffordGas = mutableAccount.getBalance().compareTo(upfrontCost) >= 0;
		validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
		mutableAccount.decrementBalance(gasCost);
	}

	private void calculateGasUsedByTransaction(final long txGasLimit, PrecompileMessage message) {
		var usedGas = txGasLimit - message.getGasRemaining();
		gasUsedByTransaction = Math.max(usedGas, txGasLimit - txGasLimit * dynamicProperties.maxGasRefundPercentage() / 100);
	}

	private PrecompileMessage constructMessageAndCallPrecompileContract(Account sender, Instant consensusTime, WorldLedgers ledgers,
																		Bytes payload, long gasLimit, long value,
																		Id token) {
		final var gasAvailable = gasLimit - intrinsicGas;
		PrecompileMessage message = PrecompileMessage.builder()
				.setLedgers(ledgers)
				.setSenderAddress(sender.canonicalAddress())
				.setValue(Wei.of(value))
				.setConsensusTime(consensusTime)
				.setGasRemaining(gasAvailable)
				.setInputData(payload)
				.setTokenID(token.asGrpcToken())
				.build();

		//call the hts
		htsPrecompiledContract.callHtsDirectly(message);
		final var gasRequirement = message.getGasRequired();

		if (message.getGasRemaining() < gasRequirement) {
			message.decrementRemainingGas(message.getGasRemaining());
			exceptionalHaltReason = Optional.of(
					org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS);
			message.setState(EXCEPTIONAL_HALT);
		} else if (message.getHtsOutputResult() != null) {
			message.decrementRemainingGas(gasRequirement);
			message.setState(COMPLETED_SUCCESS);
		} else {
			message.setState(EXCEPTIONAL_HALT);
		}

		return message;
	}

	private long calculateRefund(long gasLimit, long gasPrice) {
		final long refunded = gasLimit - gasUsedByTransaction + updater.getSbhRefund();
		final Wei refundedWei = Wei.of(refunded * gasPrice);

		if (refundedWei.greaterThan(Wei.ZERO)) {
			mutableSender.incrementBalance(refundedWei);
		}
		return refunded;

	}

	private long calculateRefundEth(long gasLimit, long gasPrice, MutableAccount
			mutableRelayer, Wei allowanceCharged) {
		final long refunded = gasLimit - gasUsedByTransaction + updater.getSbhRefund();
		final Wei refundedWei = Wei.of(refunded * gasPrice);

		if (refundedWei.greaterThan(Wei.ZERO)) {
			if (allowanceCharged.greaterThan(Wei.ZERO)) {
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
		return refunded;
	}

	private void sendFeesToCoinbase(long gasLimit, long gasPrice, long refunded) {
		final long coinbaseFee = gasLimit - refunded;
		MutableAccount mutableCoinbase = getMutableAccount(updater.getOrCreate(
				Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress()));
		mutableCoinbase.incrementBalance(Wei.of(coinbaseFee * gasPrice));
	}

	private TransactionProcessingResult buildTransactionResult(PrecompileMessage message,
															   long gasUsedByTransaction,
															   long gasPrice, Address mirrorReceiver,
															   Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
		if (message.getState() == COMPLETED_SUCCESS) {
			return TransactionProcessingResult.successful(
					message.getLogs(),
					gasUsedByTransaction,
					updater.getSbhRefund(),
					gasPrice,
					message.getHtsOutputResult(),
					mirrorReceiver,
					stateChanges);
		} else {
			return TransactionProcessingResult.failed(
					gasUsedByTransaction,
					updater.getSbhRefund(),
					gasPrice,
					message.getRevertReason(),
					exceptionalHaltReason,
					stateChanges);
		}
	}

	private MutableAccount getMutableAccount(EvmAccount senderAccount) {
		return senderAccount.getMutable();
	}

}
