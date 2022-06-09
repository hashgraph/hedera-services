package com.hedera.services.contracts.execution;

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
import org.hyperledger.besu.evm.account.MutableAccount;
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

	private GasCalculator gasCalculator;
	private final LivePricesSource livePricesSource;
	protected final GlobalDynamicProperties dynamicProperties;
	private final HTSPrecompiledContract htsPrecompiledContract;
	private final HederaMutableWorldState worldState;

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
			final BigInteger userOfferedGasPrice,
			final long maxGasAllowanceInTinybars,
			final Account relayer,
			long tokenId, WorldLedgers ledgers) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, false);
		final boolean isStatic = false;

		final Wei gasCost = Wei.of(Math.multiplyExact(gasLimit, gasPrice));
		final Wei upfrontCost = gasCost.add(value);
		final long intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false);

		final HederaWorldState.Updater updater = (HederaWorldState.Updater) worldState.updater();
		final var senderAccount = updater.getOrCreateSenderAccount(sender.getId().asEvmAddress());
		final MutableAccount mutableSender = senderAccount.getMutable();

		var allowanceCharged = Wei.ZERO;
		MutableAccount mutableRelayer = null;
		if (relayer != null) {
			final var relayerAccount = updater.getOrCreateSenderAccount(relayer.getId().asEvmAddress());
			mutableRelayer = relayerAccount.getMutable();
		}
		if (!isStatic) {
			if (intrinsicGas > gasLimit) {
				throw new InvalidTransactionException(INSUFFICIENT_GAS);
			}
			if (relayer == null) {
				final var senderCanAffordGas = mutableSender.getBalance().compareTo(upfrontCost) >= 0;
				validateTrue(senderCanAffordGas, INSUFFICIENT_PAYER_BALANCE);
				mutableSender.decrementBalance(gasCost);
			} else {
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
			}
		}

		final var coinbase = Id.fromGrpcAccount(dynamicProperties.fundingAccount()).asEvmAddress();
		var gasAvailable = gasLimit - intrinsicGas;
		final var valueAsWei = Wei.of(value);

		//construct the PrecompileMessage here
		PrecompileMessage provider = new PrecompileMessage(ledgers, sender.canonicalAddress());

		//call the hts
		final var redirectBytes = constructRedirectBytes(payload, tokenId);
		htsPrecompiledContract.callHtsDirectly(redirectBytes, provider, consensusTime.getEpochSecond());
		final var gasRequirement = provider.getGasRequired();

		//check gasAvailable < requiredGas
		if (gasAvailable < gasRequirement) {
			gasAvailable -= 0;
			provider.setState(EXCEPTIONAL_HALT);
		} else if (provider.getHtsOutputResult() != null) {
			gasAvailable -= gasRequirement;
			provider.setState(COMPLETED_SUCCESS);
		} else {
			provider.setState(EXCEPTIONAL_HALT);
		}

		//and calculate the gas used for the hts call
		var gasUsedByTransaction = calculateGasUsedByTX(gasLimit, gasAvailable);
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

			if (dynamicProperties.shouldEnableTraceability()) {
				stateChanges = updater.getFinalStateChanges();
			} else {
				stateChanges = Map.of();
			}

			// Commit top level updater
			//updater.commit();
		}
		if (provider.getState() == COMPLETED_SUCCESS) {
			return TransactionProcessingResult.successful(
					new ArrayList<>(),
					gasUsedByTransaction,
					0,
					gasPrice,
					provider.getHtsOutputResult(),
					mirrorReceiver,
					stateChanges);
		} else {
			//revertReason and haltReason any idea here?
			return TransactionProcessingResult.failed(
					gasUsedByTransaction,
					0,
					gasPrice,
					Optional.empty(),
					Optional.empty(),
					stateChanges);
		}

	}

	private Bytes constructRedirectBytes(Bytes input, long tokenId) {
		final String TOKEN_CALL_REDIRECT_HEX = "0x618dc65e0000000000000000000000000000000000000";
		var redirectBytes = Bytes.fromHexString(
				TOKEN_CALL_REDIRECT_HEX
						.concat(Long.toHexString(tokenId))
						.concat(input.toHexString()
								.replace("0x", "")));
		return redirectBytes;
	}

	private long calculateGasUsedByTX(final long txGasLimit, final long remainingGas) {
		long gasUsedByTransaction = txGasLimit - remainingGas;
		final var maxRefundPercent = dynamicProperties.maxGasRefundPercentage();
		gasUsedByTransaction = Math.max(gasUsedByTransaction, txGasLimit - txGasLimit * maxRefundPercent / 100);

		return gasUsedByTransaction;
	}

	//TODO its always HederaFunctionality.ContractCall for not eth txs?
	private long gasPriceTinyBarsGiven(final Instant consensusTime, boolean isEthTxn) {
		return livePricesSource.currentGasPrice(consensusTime,
				isEthTxn ? HederaFunctionality.EthereumTransaction : HederaFunctionality.ContractCall);
	}
}
