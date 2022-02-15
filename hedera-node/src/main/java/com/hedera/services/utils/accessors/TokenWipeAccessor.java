/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.swirlds.common.SwirldTransaction;

import java.util.List;

public class TokenWipeAccessor extends PlatformTxnAccessor {
	final AliasManager aliasManager;
	final TokenWipeAccountTransactionBody body;

	public TokenWipeAccessor(
			final SwirldTransaction txn,
			final AliasManager aliasManager) throws InvalidProtocolBufferException {
		super(txn);
		this.aliasManager = aliasManager;
		this.body = getTxn().getTokenWipe();
	}

	public Id accountToWipe() {
		return aliasManager.unaliased(body.getAccount()).toId();
	}

	public Id targetToken() {
		return Id.fromGrpcToken(body.getToken());
	}

	public List<Long> serialNums() {
		return body.getSerialNumbersList();
	}

	public long amount() {
		return body.getAmount();
	}
}
