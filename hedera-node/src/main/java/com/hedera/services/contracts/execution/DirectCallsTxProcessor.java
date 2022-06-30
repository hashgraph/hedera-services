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
import com.hedera.services.utils.TxProcessorUtil;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.store.contracts.precompile.PrecompileMessage.State.COMPLETED_SUCCESS;
import static com.hedera.services.store.contracts.precompile.PrecompileMessage.State.EXCEPTIONAL_HALT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;


@Singleton
public class DirectCallsTxProcessor {

	private final GasCalculator gasCalculator;
	private final LivePricesSource livePricesSource;
	protected final GlobalDynamicProperties dynamicProperties;
	private final HTSPrecompiledContract htsPrecompiledContract;
	private Optional<ExceptionalHaltReason> exceptionalHaltReason;
	private final HederaMutableWorldState worldState;
	private long intrinsicGas;
	private HederaWorldState.Updater updater;
	private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;


	@Inject
	public DirectCallsTxProcessor(GasCalculator gasCalculator, LivePricesSource livePricesSource,
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
			final BigInteger userOfferedGasPrice,
			final long maxGasAllowanceInTinybars,
			final Account relayer,
			final Id tokenId,
			final WorldLedgers ledgers) {

		populateCommonFields();
		final MutableAccount mutableSender = TxProcessorUtil.getMutableSender(updater, sender);
		final var isEthTx = relayer != null;
		final long gasPrice = TxProcessorUtil.gasPriceTinyBarsGiven(
				livePricesSource, consensusTime, isEthTx,
				HederaFunctionality.ContractCall);
		final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
		final Wei upfrontCost = gasCost.add(value);

		var allowanceCharged = Wei.ZERO;
		MutableAccount mutableRelayer = null;
		if (isEthTx) {
			mutableRelayer = TxProcessorUtil.getMutableRelayer(updater, relayer);
		}
		if (intrinsicGas > gasLimit) {
			throw new InvalidTransactionException(INSUFFICIENT_GAS);
		}
		if (!isEthTx) {
			TxProcessorUtil.senderCanAffordGas(gasCost, upfrontCost, mutableSender);
		} else {
			allowanceCharged = TxProcessorUtil.chargeForEth(
					userOfferedGasPrice, gasCost, maxGasAllowanceInTinybars, mutableSender,
					mutableRelayer, Wei.ZERO, gasPrice, gasLimit, value);
		}

		//construct PrecompileMessage
		PrecompileMessage message = TxProcessorUtil.constructPrecompileMessage(
				sender, consensusTime, ledgers,
				payload, gasLimit, value, tokenId, intrinsicGas);

		//call the hts
		callHtsPrecompile(message);
		long gasUsedByTransaction = TxProcessorUtil.calculateGasUsedByTX(gasLimit, message,
				dynamicProperties.maxGasRefundPercentage());

		// return gas price to accounts
		final long refunded = isEthTx ?
				TxProcessorUtil.calculateRefundEth(gasLimit, gasPrice, mutableRelayer, allowanceCharged,
						updater.getSbhRefund(), mutableSender, gasUsedByTransaction) :
				TxProcessorUtil.calculateRefund(gasLimit, gasPrice,
						gasUsedByTransaction, updater.getSbhRefund(), mutableSender);
		// Send fees to coinbase
		TxProcessorUtil.sendFeesToCoinbase(updater, dynamicProperties, gasLimit, gasPrice, refunded);

		updater.commit();
		final var isCompletedSuccess = message.getState() == COMPLETED_SUCCESS;
		return TxProcessorUtil.buildTransactionResult(
				isCompletedSuccess, message.getLogs(),
				updater.getSbhRefund(), gasUsedByTransaction,
				gasPrice, mirrorReceiver, message.getHtsOutputResult(),
				message.getRevertReason(), exceptionalHaltReason,
				stateChanges);
	}

	private void populateCommonFields() {
		updater = (HederaWorldState.Updater) worldState.updater();
		exceptionalHaltReason = Optional.empty();
		intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false);
		stateChanges = dynamicProperties.shouldEnableTraceability() ?
				updater.getFinalStateChanges() : Map.of();
	}

	private void callHtsPrecompile(PrecompileMessage message) {
		htsPrecompiledContract.callHtsPrecompileDirectly(message);
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
	}
}
