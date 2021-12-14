package com.hedera.services.bdd.spec.queries.meta;

public class TokenTransfer {
	String token;
	String account;
	Long amount;

	public TokenTransfer(String token, String account, Long amount) {
		this.token = token;
		this.account = account;
		this.amount = amount;
	}

	public static TokenTransfer with(String token, String account, Long amount) {
		return new TokenTransfer(token, account, amount);
	}
}
