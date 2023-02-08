package com.hedera.node.app.spi.fee;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public interface FeeCalculator {

	/**
	 * @param chargeable
	 * @param params
	 * @return the calculated fee in TINYBAR
	 * @throws FeeCalculationException
	 */
	long calculateFee(@NonNull Chargeable chargeable,
			@Nullable CalculationParameter... params) throws FeeCalculationException;

	@NonNull
	ExchangeRate getExchangeRate();

}
