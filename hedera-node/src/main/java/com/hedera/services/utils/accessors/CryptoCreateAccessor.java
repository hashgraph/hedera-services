package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.SwirldTransaction;

public class CryptoCreateAccessor extends PlatformTxnAccessor{
	private final CryptoCreateTransactionBody transactionBody;
	private AccountID proxyId = null;

	public CryptoCreateAccessor(final SwirldTransaction txn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(txn, aliasManager);
		this.transactionBody = getTxn().getCryptoCreateAccount();
		setCryptoCreateUsageMeta();
	}

	public long getInitialBalance() {
		return transactionBody.getInitialBalance();
	}

	public long getAutoRenewPeriod() {
		return transactionBody.getAutoRenewPeriod().getSeconds();
	}

	public boolean getReceiverSigRequired() {
		return transactionBody.getReceiverSigRequired();
	}

	public boolean hasProxy() {
		return transactionBody.hasProxyAccountID();
	}

	public int getMaxAutomaticTokenAssociations() {
		return transactionBody.getMaxAutomaticTokenAssociations();
	}

	public Key getKey() {
		return transactionBody.getKey();
	}

	public AccountID getSponsor() {
		return unaliased(getPayer()).toGrpcAccountId();
	}

	public AccountID getProxy() {
		if (proxyId == null) {
			proxyId = unaliased(transactionBody.getProxyAccountID()).toGrpcAccountId();
		}
		return proxyId;
	}

	private void setCryptoCreateUsageMeta() {
		final var cryptoCreateMeta = new CryptoCreateMeta(transactionBody);
		SPAN_MAP_ACCESSOR.setCryptoCreateMeta(this, cryptoCreateMeta);
	}
}
