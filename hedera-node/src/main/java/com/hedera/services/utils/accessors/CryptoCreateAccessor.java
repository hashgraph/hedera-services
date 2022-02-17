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
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
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

	@Override
	public String getMemo() {
		return transactionBody.getMemo();
	}

	public long getInitialBalance() {
		return transactionBody.getInitialBalance();
	}

	public Duration getAutoRenewPeriod() {
		return transactionBody.getAutoRenewPeriod();
	}

	public boolean getReceiverSigRequired() {
		return transactionBody.getReceiverSigRequired();
	}

	public boolean hasProxy() {
		return transactionBody.hasProxyAccountID();
	}

	public boolean hasKey() {
		return transactionBody.hasKey();
	}

	public boolean hasAutoRenewPeriod() {
		return transactionBody.hasAutoRenewPeriod();
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

	public long getSendRecordThreshold() {
		return transactionBody.getSendRecordThreshold();
	}

	public long getReceiveRecordThreshold() {
		return transactionBody.getReceiveRecordThreshold();
	}

	private void setCryptoCreateUsageMeta() {
		final var cryptoCreateMeta = new CryptoCreateMeta(transactionBody);
		SPAN_MAP_ACCESSOR.setCryptoCreateMeta(this, cryptoCreateMeta);
	}
}
