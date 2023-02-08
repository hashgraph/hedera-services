package com.hedera.node.app.spi.fee.sample;

import com.hedera.node.app.spi.fee.CalculationParameter;
import com.hedera.node.app.spi.fee.Chargeable;
import com.hedera.node.app.spi.fee.Currency;
import com.hedera.node.app.spi.fee.ExchangeRate;
import com.hedera.node.app.spi.fee.FeeCalculationException;
import com.hedera.node.app.spi.fee.FeeCalculator;

import java.math.BigDecimal;

public class FeeCalculatorSample {

	public static void main(final String[] args) throws FeeCalculationException {
		final FeeCalculator feeCalculator = null;
		final Chargeable uploadFileHandler = null;

		final ExchangeRate exchangeRate = feeCalculator.getExchangeRate();
		final CalculationParameter byteCount = new CalculationParameter("byteCount", 17);

		final long feeInTinyBar = feeCalculator.calculateFee(uploadFileHandler, byteCount);
		final BigDecimal costInUsd = exchangeRate.exchange(Currency.TINYBAR, Currency.USD, feeInTinyBar);
		System.out.println(uploadFileHandler.getName() + " operation cost " + costInUsd + " USD");
	}
}
