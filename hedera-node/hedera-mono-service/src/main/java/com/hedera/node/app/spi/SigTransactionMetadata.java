/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.spi;

import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature verification.
 */
public class SigTransactionMetadata implements TransactionMetadata {
	protected @Nullable List<JKey> requiredKeys = new ArrayList<>();
	protected Transaction txn;
	protected AccountStore store;

	protected ResponseCodeEnum status = OK;

	public SigTransactionMetadata(final AccountStore store,
			final Transaction txn,
			final AccountID payer,
			final List<JKey> otherKeys) {
		this.store = store;
		this.txn = txn;
		requiredKeys.addAll(otherKeys);
		addPayerKey(payer);
	}

	public SigTransactionMetadata(final AccountStore store,
			final Transaction txn,
			final AccountID payer) {
		this(store, txn, payer, Collections.emptyList());
	}

	private void addPayerKey(final AccountID payer){
		final var account = store.getAccountLeaf(payer);
		if (account.isEmpty()) {
			this.status = INVALID_PAYER_ACCOUNT_ID;
		} else {
			requiredKeys.add(account.get().getAccountKey());
		}
	}

	@Override
	public Transaction getTxn() {
		return txn;
	}

	@Override
	public List<JKey> getReqKeys() {
		return requiredKeys;
	}

	@Override
	public boolean failed() {
		return !status.equals(OK);
	}
	@Override
	public ResponseCodeEnum failureStatus() {
		return status;
	}
}
