package com.hedera.services.ledger.properties;

import com.hederahashgraph.api.proto.java.TokenID;

public class TokenScopedPropertyValue {
	private final Object value;
	private final TokenID token;

	public TokenScopedPropertyValue(Object value, TokenID token) {
		this.value = value;
		this.token = token;
	}

	public Object value() {
		return value;
	}

	public TokenID token() {
		return token;
	}
}
