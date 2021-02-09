package com.hedera.test.factories.accounts;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;

import static com.hedera.test.utils.IdUtils.asContract;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.factories.accounts.MerkleAccountFactory.newAccount;
import static com.hedera.test.factories.txns.SignedTxnFactory.*;

public class MockFCMapFactory {
	public static final long NODE_BALANCE = 1_000_000_000L;
	public static final long GENESIS_BALANCE = 50_000_000_000_000L;

	private final FCMap mock = mock(FCMap.class);

	private MockFCMapFactory() {}

	public static MockFCMapFactory newAccounts() {
		return new MockFCMapFactory();
	}
	public static MockFCMapFactory newAccountsWithDefaults() throws Exception {
		return newAccounts()
				.withAccount(DEFAULT_NODE_ID, newAccount().balance(NODE_BALANCE).get())
				.withAccount(DEFAULT_PAYER_ID, newAccount().balance(GENESIS_BALANCE).accountKeys(DEFAULT_PAYER_KT).get());
	}

	public MockFCMapFactory withAccount(String id, MerkleAccount meta) {
		MerkleEntityId account = MerkleEntityId.fromAccountId(asAccount(id));
		given(mock.get(account)).willReturn(meta);
		return this;
	}
	public MockFCMapFactory withContract(String id, MerkleAccount meta) {
		MerkleEntityId contract = MerkleEntityId.fromContractId(asContract(id));
		given(mock.get(contract)).willReturn(meta);
		return this;
	}

	public FCMap<MerkleEntityId, MerkleAccount> get() {
		return (FCMap<MerkleEntityId, MerkleAccount>)mock;
	}
}
