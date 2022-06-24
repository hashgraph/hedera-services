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
import com.hederahashgraph.api.proto.java.TokenID;
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
import java.util.ArrayList;
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

	@Inject
	public EvmTxProcessorSimulator(GasCalculator gasCalculator, LivePricesSource livePricesSource,
								   GlobalDynamicProperties dynamicProperties, HTSPrecompiledContract htsPrecompiledContract, HederaMutableWorldState worldState) {
		this.gasCalculator = gasCalculator;
		this.livePricesSource = livePricesSource;
		this.dynamicProperties = dynamicProperties;
		this.htsPrecompiledContract = htsPrecompiledContract;
		this.worldState = worldState;
		exceptionalHaltReason = Optional.empty();

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

		HederaWorldState.Updater updater = (HederaWorldState.Updater) worldState.updater();
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, false);
		final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
		final Wei upfrontCost = gasCost.add(value);
		final long intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false);
		final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;
		final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		final MutableAccount mutableSender = getMutableAccount(senderAccount);

		if (intrinsicGas > gasLimit) {
			throw new InvalidTransactionException(INSUFFICIENT_GAS);
		}
		final var senderCanAffordGas = mutableSender.getBalance().compareTo(upfrontCost) >= 0;
		validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
		mutableSender.decrementBalance(gasCost);

		var gasAvailable = gasLimit - intrinsicGas;
		//construct the PrecompileMessage here
		PrecompileMessage message = constructMessageAndCallPrecompileContract(sender, consensusTime, ledgers,
				payload, gasAvailable, value, tokenId);
		//and calculate the gas used for the hts call
		var gasUsedByTransaction = calculateGasUsedByTX(gasLimit, message.getGasRemaining());
		final long sbhRefund = updater.getSbhRefund();

		// return gas price to accounts
		final long refunded = gasLimit - gasUsedByTransaction + sbhRefund;
		final Wei refundedWei = Wei.of(refunded * gasPrice);

		if (refundedWei.greaterThan(Wei.ZERO)) {
			mutableSender.incrementBalance(refundedWei);
		}

		// Send fees to coinbase
		final long coinbaseFee = gasLimit - refunded;
		MutableAccount mutableCoinbase = getMutableAccount(updater.getOrCreate(
				Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress()));
		mutableCoinbase.incrementBalance(Wei.of(coinbaseFee * gasPrice));

		if (dynamicProperties.shouldEnableTraceability()) {
			stateChanges = updater.getFinalStateChanges();
		} else {
			stateChanges = Map.of();
		}

		updater.commit();

		if (message.getState() == COMPLETED_SUCCESS) {
			return TransactionProcessingResult.successful(
					message.getLogs(),
					gasUsedByTransaction,
					sbhRefund,
					gasPrice,
					message.getHtsOutputResult(),
					mirrorReceiver,
					stateChanges);
		} else {
			return TransactionProcessingResult.failed(
					gasUsedByTransaction,
					sbhRefund,
					gasPrice,
					message.getRevertReason(),
					exceptionalHaltReason,
					stateChanges);
		}

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

		HederaWorldState.Updater updater = (HederaWorldState.Updater) worldState.updater();
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, true);
		final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
		final long intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false);
		final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;
		final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		final MutableAccount mutableSender = getMutableAccount(senderAccount);
		var allowanceCharged = Wei.ZERO;
		final var relayerAccount = updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress());
		MutableAccount mutableRelayer = getMutableAccount(relayerAccount);

		if (intrinsicGas > gasLimit) {
			throw new InvalidTransactionException(INSUFFICIENT_GAS);
		}
		final var gasAllowance = Wei.of(maxGasAllowanceInTinybars);

		if (userOfferedGasPrice.equals(BigInteger.ZERO)) {
			// If sender set gas price to 0, relayer pays all the fees
			validateTrue(gasAllowance.greaterOrEqualThan(gasCost), INSUFFICIENT_TX_FEE);
			final var relayerCanAffordGas = mutableRelayer.getBalance().compareTo((gasCost)) >= 0;
			validateTrue(relayerCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
			mutableRelayer.decrementBalance(gasCost);
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
			final var senderCanAffordGas = mutableSender.getBalance().compareTo(gasCost) >= 0;
			validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
			mutableSender.decrementBalance(gasCost);
		}
		// In any case, the sender must have sufficient balance to pay for any value sent
		final var senderCanAffordValue = mutableSender.getBalance().compareTo(Wei.of(value)) >= 0;
		validateTrue(senderCanAffordValue, INSUFFICIENT_PAYER_BALANCE);

		var gasAvailable = gasLimit - intrinsicGas;

		//construct the PrecompileMessage here
		PrecompileMessage message = constructMessageAndCallPrecompileContract(sender, consensusTime, ledgers,
				payload, gasAvailable, value, tokenId);

		// calculate the gas used for the hts call
		var gasUsedByTransaction = calculateGasUsedByTX(gasLimit, message.getGasRemaining());
		final long sbhRefund = updater.getSbhRefund();

		// return gas price to accounts
		final long refunded = gasLimit - gasUsedByTransaction + sbhRefund;
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
		// Send fees to coinbase
		final long coinbaseFee = gasLimit - refunded;
		MutableAccount mutableCoinbase = getMutableAccount(updater.getOrCreate(
				Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress()));
		mutableCoinbase.incrementBalance(Wei.of(coinbaseFee * gasPrice));

		if (dynamicProperties.shouldEnableTraceability()) {
			stateChanges = updater.getFinalStateChanges();
		} else {
			stateChanges = Map.of();
		}

		updater.commit();

		if (message.getState() == COMPLETED_SUCCESS) {
			return TransactionProcessingResult.successful(
					new ArrayList<>(),
					gasUsedByTransaction,
					sbhRefund,
					gasPrice,
					message.getHtsOutputResult(),
					mirrorReceiver,
					stateChanges);
		} else {
			return TransactionProcessingResult.failed(
					gasUsedByTransaction,
					sbhRefund,
					gasPrice,
					message.getRevertReason(),
					exceptionalHaltReason,
					stateChanges);
		}

	}

	private PrecompileMessage constructMessageAndCallPrecompileContract(Account sender, Instant consensusTime, WorldLedgers ledgers,
																		Bytes payload, long gasAvailable, long value,
																		Id token) {
		PrecompileMessage message = PrecompileMessage.builder()
				.setLedgers(ledgers)
				.setSenderAddress(sender.canonicalAddress())
				.setValue(Wei.of(value))
				.setConsensusTime(consensusTime)
				.setGasRemaining(gasAvailable)
				.setInputData(payload)
				.setTokenID(buildTokenId(token))
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

	private MutableAccount getMutableAccount(EvmAccount senderAccount) {
		return senderAccount.getMutable();
	}

	private TokenID buildTokenId(Id token){
		return  TokenID.newBuilder()
				.setShardNum(token.shard())
				.setRealmNum(token.realm())
				.setTokenNum(token.num())
				.build();
	}

	private long calculateGasUsedByTX(final long txGasLimit, final long remainingGas) {
		long gasUsedByTransaction = txGasLimit - remainingGas;
		final var maxRefundPercent = dynamicProperties.maxGasRefundPercentage();
		gasUsedByTransaction = Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

		return gasUsedByTransaction;
	}

	private long gasPriceTinyBarsGiven(final Instant consensusTime, boolean isEthTxn) {
		return livePricesSource.currentGasPrice(consensusTime,
				isEthTxn ? HederaFunctionality.EthereumTransaction : HederaFunctionality.ContractCall);
	}
}
