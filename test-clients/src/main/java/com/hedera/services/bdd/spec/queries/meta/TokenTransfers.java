package com.hedera.services.bdd.spec.queries.meta;

import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TokenTransfers {
	private final List<TokenTransfer> transfers;

	private TokenTransfers() {
		transfers = new ArrayList<>();
	}

	private TokenTransfers(List<TokenTransfer> tokenTransfers) {
		this.transfers = tokenTransfers;
	}

	public static TokenTransfers with(TokenTransfer ...transfers) {
		return new TokenTransfers(Arrays.asList(transfers));
	}
}


