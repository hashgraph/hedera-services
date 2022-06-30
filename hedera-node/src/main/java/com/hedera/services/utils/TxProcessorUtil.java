package com.hedera.services.utils;

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
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.PrecompileMessage;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.PrecompileMessage.State.COMPLETED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;

public class TxProcessorUtil {

	public static Wei chargeForEth(BigInteger userOfferedGasPrice, Wei gasCost,
								   long maxGasAllowanceInTinybars, MutableAccount mutableSender,
								   MutableAccount mutableRelayer, Wei allowanceCharged, long gasPrice,
								   long gasLimit, long value) {

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

		return allowanceCharged;
	}

	public static long gasPriceTinyBarsGiven(LivePricesSource livePricesSource, final Instant consensusTime,
											 boolean isEthTxn, HederaFunctionality txType) {
		return livePricesSource.currentGasPrice(consensusTime,
				isEthTxn ? HederaFunctionality.EthereumTransaction : txType);
	}

	public static long calculateGasUsedByTX(final GasCalculator gasCalculator, final long txGasLimit,
											final MessageFrame initialFrame, final int maxRefundPercent) {
		long gasUsedByTransaction = txGasLimit - initialFrame.getRemainingGas();
		/* Return leftover gas */
		final long selfDestructRefund =
				gasCalculator.getSelfDestructRefundAmount() *
						Math.min(
								initialFrame.getSelfDestructs().size(),
								gasUsedByTransaction / (gasCalculator.getMaxRefundQuotient()));

		gasUsedByTransaction = gasUsedByTransaction - selfDestructRefund - initialFrame.getGasRefund();

		gasUsedByTransaction = Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

		return gasUsedByTransaction;
	}

	public static long calculateGasUsedByTX(final long txGasLimit, PrecompileMessage message,
											final int maxRefundPercent) {
		long usedGas = txGasLimit - message.getGasRemaining();
		return Math.max(
				usedGas, txGasLimit - txGasLimit * maxRefundPercent / 100);
	}

	public static void senderCanAffordGas(Wei gasCost, Wei upfrontCost, MutableAccount mutableSender) {
		final var senderCanAffordGas = mutableSender.getBalance().compareTo(upfrontCost) >= 0;
		validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
		mutableSender.decrementBalance(gasCost);
	}

	public static MutableAccount getMutableSender(HederaWorldState.Updater updater, Account sender) {
		final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		return senderAccount.getMutable();
	}

	public static MutableAccount getMutableRelayer(HederaWorldState.Updater updater, Account relayer) {
		final var relayerAccount = updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress());
		return relayerAccount.getMutable();
	}

	public static PrecompileMessage constructPrecompileMessage(Account sender, Instant consensusTime,
															   WorldLedgers ledgers,
															   Bytes payload, long gasLimit, long value,
															   Id token, long intrinsicGas) {
		return PrecompileMessage.builder()
				.setLedgers(ledgers)
				.setSenderAddress(sender.canonicalAddress())
				.setValue(Wei.of(value))
				.setConsensusTime(consensusTime)
				.setGasRemaining(gasLimit - intrinsicGas)
				.setInputData(payload)
				.setTokenID(token.asGrpcToken())
				.build();
	}

	public static long calculateRefund(long gasLimit, long gasPrice, long gasUsedByTransaction,
									   long sbhRefund, MutableAccount sender) {
		final long refunded = gasLimit - gasUsedByTransaction + sbhRefund;
		final Wei refundedWei = Wei.of(refunded * gasPrice);

		if (refundedWei.greaterThan(Wei.ZERO)) {
			sender.incrementBalance(refundedWei);
		}
		return refunded;
	}

	public static long calculateRefundEth(long gasLimit, long gasPrice, MutableAccount
			mutableRelayer, Wei allowanceCharged, long sbhRefund, MutableAccount sender,
										  long gasUsedByTransaction) {
		final long refunded = gasLimit - gasUsedByTransaction + sbhRefund;
		final Wei refundedWei = Wei.of(refunded * gasPrice);

		if (refundedWei.greaterThan(Wei.ZERO)) {
			if (allowanceCharged.greaterThan(Wei.ZERO)) {
				// If allowance has been charged, we always try to refund relayer first
				if (refundedWei.greaterOrEqualThan(allowanceCharged)) {
					mutableRelayer.incrementBalance(allowanceCharged);
					sender.incrementBalance(refundedWei.subtract(allowanceCharged));
				} else {
					mutableRelayer.incrementBalance(refundedWei);
				}
			} else {
				sender.incrementBalance(refundedWei);
			}
		}
		return refunded;
	}

	public static void sendFeesToCoinbase(HederaWorldState.Updater updater, GlobalDynamicProperties dynamicProperties,
										  long gasLimit, long gasPrice, long refunded) {
		final long coinbaseFee = gasLimit - refunded;
		MutableAccount mutableCoinbase = updater.getOrCreate(
				Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress()).getMutable();
		mutableCoinbase.incrementBalance(Wei.of(coinbaseFee * gasPrice));
	}


	public static TransactionProcessingResult buildTransactionResult(boolean isCompletedSuccess, List<Log> logs,
																	 long sbhRefund, long gasUsedByTransaction,
																	 long gasPrice, Address mirrorReceiver,
																	 Bytes output, Optional<Bytes> revertReason,
																	 Optional<ExceptionalHaltReason> exceptionalHaltReason,
																	 Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
		if (isCompletedSuccess) {
			return TransactionProcessingResult.successful(
					logs,
					gasUsedByTransaction,
					sbhRefund,
					gasPrice,
					output,
					mirrorReceiver,
					stateChanges);
		} else {
			return TransactionProcessingResult.failed(
					gasUsedByTransaction,
					sbhRefund,
					gasPrice,
					revertReason,
					exceptionalHaltReason,
					stateChanges);
		}
	}


	private TxProcessorUtil() {
		throw new UnsupportedOperationException("Utility Class");
	}
}

