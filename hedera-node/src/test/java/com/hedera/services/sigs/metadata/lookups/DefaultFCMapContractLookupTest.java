package com.hedera.services.sigs.metadata.lookups;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.exception.AdminKeyNotExistException;
import com.hedera.services.legacy.exception.InvalidContractIDException;
import com.swirlds.fcmap.FCMap;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.runner.RunWith;

import static com.hedera.test.factories.accounts.MockFCMapFactory.*;
import static com.hedera.test.factories.accounts.MapValueFactory.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RunWith(JUnitPlatform.class)
public class DefaultFCMapContractLookupTest {
	private final String id = "0.0.1337";
	private final ContractID contract = IdUtils.asContract(id);
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private DefaultFCMapContractLookup subject;

	@Test
	public void failsOnMissingAccount() {
		// given:
		accounts = newAccounts().get();
		subject = new DefaultFCMapContractLookup(() -> accounts);

		// expect:
		assertThrows(InvalidContractIDException.class, () -> {
			subject.lookup(contract);
		});
	}

	@Test
	public void failsOnDeletedAccount() {
		// given:
		accounts = newAccounts().withAccount(id, newContract().deleted(true).get()).get();
		subject = new DefaultFCMapContractLookup(() -> accounts);

		// expect:
		assertThrows(InvalidContractIDException.class, () -> {
			subject.lookup(contract);
		});
	}

	@Test
	public void failsNormalAccountInsteadOfSmartContract() {
		// given:
		accounts = newAccounts().withAccount(id, newAccount().get()).get();
		subject = new DefaultFCMapContractLookup(() -> accounts);

		// expect:
		assertThrows(InvalidContractIDException.class, () -> {
			subject.lookup(contract);
		});
	}

	@Test
	public void failsOnNullAccountKeys() {
		// given:
		accounts = newAccounts().withAccount(id, newContract().get()).get();
		subject = new DefaultFCMapContractLookup(() -> accounts);

		// expect:
		assertThrows(AdminKeyNotExistException.class, () -> {
			subject.lookup(contract);
		});
	}

	@Test
	public void failsOnContractIdKey() {
		// given:
		accounts = newAccounts().withAccount(id, newContract().accountKeys(new JContractIDKey(contract)).get()).get();
		subject = new DefaultFCMapContractLookup(() -> accounts);

		// expect:
		assertThrows(AdminKeyNotExistException.class, () -> {
			subject.lookup(contract);
		});
	}

	@Test
	public void returnsLegalKey() throws Exception {
		// given:
		JKey desiredKey = new JKeyList();
		accounts = newAccounts().withAccount(id, newContract().accountKeys(desiredKey).get()).get();
		subject = new DefaultFCMapContractLookup(() -> accounts);

		// expect:
		Assert.assertTrue(subject.lookup(contract).hasAdminKey());
		assertEquals(desiredKey, subject.lookup(contract).getKey());
	}
}
