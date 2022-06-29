package com.hedera.services.utils;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;

import java.math.BigInteger;

import static com.hedera.services.ethereum.EthTxData.WEIBARS_TO_TINYBARS;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;

public class TxProcessorUtil {

	public static Wei chargeForEth(BigInteger userOfferedGasPrice, Wei gasCost,
								   long maxGasAllowanceInTinybars, MutableAccount mutableSender,
								   MutableAccount mutableRelayer, Wei allowanceCharged, long gasPrice,
								   long gasLimit, long value) {
		{
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
		return allowanceCharged;
	}

	private TxProcessorUtil() {
		throw new UnsupportedOperationException("Utility Class");
	}
}

