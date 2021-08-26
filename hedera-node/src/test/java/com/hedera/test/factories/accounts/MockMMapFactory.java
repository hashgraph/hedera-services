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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.merkle.map.MerkleMap;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

public class MockMMapFactory {
	private final MerkleMap mock = mock(MerkleMap.class);

	private MockMMapFactory() {}

	public static MockMMapFactory newAccounts() {
		return new MockMMapFactory();
	}

	public MockMMapFactory withAccount(String id, MerkleAccount meta) {
		final var account = PermHashInteger.fromAccountId(asAccount(id));
		given(mock.get(account)).willReturn(meta);
		return this;
	}
	public MockMMapFactory withContract(String id, MerkleAccount meta) {
		final var contract = PermHashInteger.fromContractId(asContract(id));
		given(mock.get(contract)).willReturn(meta);
		return this;
	}

	public MerkleMap<PermHashInteger, MerkleAccount> get() {
		return (MerkleMap<PermHashInteger, MerkleAccount>)mock;
	}
}
