package com.hedera.test.factories.txns;

import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenCreateFactory extends SignedTxnFactory<TokenCreateFactory> {
	private boolean frozen = false;
	private boolean omitAdmin = false;

	private TokenCreateFactory() {}

	public static TokenCreateFactory newSignedTokenCreate() {
		return new TokenCreateFactory();
	}

	public TokenCreateFactory frozen() {
		frozen = true;
		return this;
	}

	public TokenCreateFactory missingAdmin() {
		omitAdmin = true;
		return this;
	}

	@Override
	protected TokenCreateFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = TokenCreation.newBuilder();
		if (!omitAdmin) {
			op.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey());
		}
		if (frozen) {
			op.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey());
		}
		txn.setTokenCreation(op);
	}
}
