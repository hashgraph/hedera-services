package com.hedera.services.exceptions;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class InvalidTransactionException extends RuntimeException {
	private final ResponseCodeEnum responseCode;

	public InvalidTransactionException(ResponseCodeEnum responseCode) {
		this.responseCode = responseCode;
	}

	public ResponseCodeEnum getResponseCode() {
		return responseCode;
	}
}
