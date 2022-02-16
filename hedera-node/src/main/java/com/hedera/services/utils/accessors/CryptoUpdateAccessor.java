package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.SwirldTransaction;

public class CryptoUpdateAccessor extends PlatformTxnAccessor{
	private final CryptoUpdateTransactionBody transactionBody;

	public CryptoUpdateAccessor(final SwirldTransaction platformTxn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(platformTxn, aliasManager);
		this.transactionBody = getTxn().getCryptoUpdateAccount();
		setCryptoUpdateUsageMeta();
	}

	public AccountID getTarget() {
		return unaliased(transactionBody.getAccountIDToUpdate()).toGrpcAccountId();
	}

	public boolean hasExpirationTime() {
		return transactionBody.hasExpirationTime();
	}

	public boolean hasKey() {
		return transactionBody.hasKey();
	}

	public boolean hasProxy() {
		return transactionBody.hasProxyAccountID();
	}

	public boolean hasReceiverSigRequiredWrapper() {
		return transactionBody.hasReceiverSigRequiredWrapper();
	}

	public boolean getReceiverSigRequiredWrapperValue() {
		return transactionBody.getReceiverSigRequiredWrapper().getValue();
	}

	public boolean getReceiverSigRequired() {
		return transactionBody.getReceiverSigRequired();
	}

	public boolean hasAutoRenewPeriod() {
		return transactionBody.hasAutoRenewPeriod();
	}

	public boolean hasMemo() {
		return transactionBody.hasMemo();
	}

	 public boolean hasMaxAutomaticTokenAssociations() {
		return transactionBody.hasMaxAutomaticTokenAssociations();
	 }

	 public int getMaxAutomaticTokenAssociations() {
		return transactionBody.getMaxAutomaticTokenAssociations().getValue();
	 }

	@Override
	public String getMemo() {
		return transactionBody.getMemo().getValue();
	}

	public long getAutoRenewPeriod() {
		return transactionBody.getAutoRenewPeriod().getSeconds();
	}

	public Timestamp getExpirationTime() {
		return transactionBody.getExpirationTime();
	}

	public AccountID getProxy() {
		return unaliased(transactionBody.getProxyAccountID()).toGrpcAccountId();
	}

	public Key getKey() {
		return transactionBody.getKey();
	}

	private void setCryptoUpdateUsageMeta() {
		final var cryptoUpdateMeta = new CryptoUpdateMeta(transactionBody,
				getTxnId().getTransactionValidStart().getSeconds());
		SPAN_MAP_ACCESSOR.setCryptoUpdate(this, cryptoUpdateMeta);
	}
}
