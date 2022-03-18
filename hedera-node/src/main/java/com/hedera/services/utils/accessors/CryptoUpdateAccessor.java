package com.hedera.services.utils.accessors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;

public class CryptoUpdateAccessor extends SignedTxnAccessor{
	private final CryptoUpdateTransactionBody transactionBody;
	private AliasManager aliasManager;

	public CryptoUpdateAccessor(final byte[] txn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(txn);
		this.aliasManager = aliasManager;
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

	public Duration getAutoRenewPeriod() {
		return transactionBody.getAutoRenewPeriod();
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

	protected EntityNum unaliased(AccountID grpcId) {
		return aliasManager.unaliased(grpcId);
	}

	private void setCryptoUpdateUsageMeta() {
		final var cryptoUpdateMeta = new CryptoUpdateMeta(transactionBody,
				getTxnId().getTransactionValidStart().getSeconds());
		SPAN_MAP_ACCESSOR.setCryptoUpdate(this, cryptoUpdateMeta);
	}
}
