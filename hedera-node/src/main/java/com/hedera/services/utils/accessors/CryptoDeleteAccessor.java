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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.swirlds.common.SwirldTransaction;

public class CryptoDeleteAccessor extends PlatformTxnAccessor {
	private final CryptoDeleteTransactionBody transactionBody;

	public CryptoDeleteAccessor(final SwirldTransaction platformTxn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(platformTxn, aliasManager);
		this.transactionBody = getTxn().getCryptoDelete();
	}

	public boolean hasTarget() {
		return transactionBody.hasDeleteAccountID();
	}

	public boolean hasTransferAccount() {
		return transactionBody.hasTransferAccountID();
	}

	public AccountID getTarget() {
		return unaliased(transactionBody.getDeleteAccountID()).toGrpcAccountId();
	}

	public AccountID getTransferAccount() {
		return unaliased(transactionBody.getTransferAccountID()).toGrpcAccountId();
	}
}
