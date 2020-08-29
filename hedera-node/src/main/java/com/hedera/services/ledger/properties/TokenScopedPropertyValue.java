package com.hedera.services.ledger.properties;

import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.TokenID;

public class TokenScopedPropertyValue {
	private final Object value;
	private final TokenID id;
	private final MerkleToken token;

	public TokenScopedPropertyValue(TokenID id, MerkleToken token, Object value) {
		this.value = value;
		this.id = id;
		this.token = token;
	}

	public TokenID id() {
		return id;
	}

	public Object value() {
		return value;
	}

	public MerkleToken token() {
		return token;
	}
}
