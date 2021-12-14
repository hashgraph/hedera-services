package com.hedera.services.bdd.spec.queries.meta;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.List;
import java.util.Optional;

public class ChildRecord {
	public Optional<ResponseCodeEnum> expectedStatus = Optional.empty();
	public Optional<List<TokenTransfers>> expectedTransfers = Optional.empty();

	public static ChildRecord with() {
		return new ChildRecord();
	}

	public ChildRecord status(ResponseCodeEnum expectingStatus) {
		this.expectedStatus = Optional.of(expectingStatus);
		return this;
	}

	public ChildRecord transfers(TokenTransfers ...expectingTokenTransfers) {
		this.expectedTransfers = Optional.of(List.of(expectingTokenTransfers));
		return this;
	}
}
