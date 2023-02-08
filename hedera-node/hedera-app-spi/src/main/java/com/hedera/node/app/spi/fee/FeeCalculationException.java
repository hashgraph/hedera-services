package com.hedera.node.app.spi.fee;

public class FeeCalculationException extends Exception {

	public FeeCalculationException(final String message) {
		super(message);
	}

	public FeeCalculationException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
