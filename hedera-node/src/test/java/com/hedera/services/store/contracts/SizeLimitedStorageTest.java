package com.hedera.services.store.contracts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.hedera.services.store.contracts.SizeLimitedStorage.TREE_SET_FACTORY;
import static com.hedera.services.store.contracts.SizeLimitedStorage.ZERO_VALUE;
import static com.hedera.services.store.contracts.SizeLimitedStorage.incorporateKvImpact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SizeLimitedStorageTest {
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private VirtualMap<ContractKey, ContractValue> storage;

	private Map<Long, TreeSet<ContractKey>> updatedKeys = new TreeMap<>();
	private Map<Long, TreeSet<ContractKey>> removedKeys = new TreeMap<>();
	private Map<ContractKey, ContractValue> newMappings = new HashMap<>();

	private SizeLimitedStorage subject;

	@BeforeEach
	void setUp() {
		subject = new SizeLimitedStorage(dynamicProperties, () -> accounts, () -> storage);
	}

	@Test
	void updatesAreBufferedAndReturned() {
		givenAccount(firstAccount, firstKvPairs);

		subject.putStorage(firstAccount, aLiteralKey, aLiteralValue);

		assertEquals(firstKvPairs + 1, subject.usageSoFar(firstAccount));
		assertEquals(aLiteralValue, subject.getStorage(firstAccount, aLiteralKey));
	}

	@Test
	void unbufferedUpdatesAreReturnedDirectly() {
		given(storage.get(firstAKey)).willReturn(aValue);

		assertEquals(aLiteralValue, subject.getStorage(firstAccount, aLiteralKey));
		assertEquals(UInt256.ZERO, subject.getStorage(firstAccount, bLiteralKey));
	}

	@Test
	void removedKeysAreRespected() {
		givenAccount(firstAccount, firstKvPairs);
		given(storage.get(firstAKey)).willReturn(aValue);
		given(storage.containsKey(firstAKey)).willReturn(true);

		assertEquals(aLiteralValue, subject.getStorage(firstAccount, aLiteralKey));

		subject.putStorage(firstAccount, aLiteralKey, bLiteralValue);
		assertEquals(bLiteralValue, subject.getStorage(firstAccount, aLiteralKey));
		assertEquals(firstKvPairs, subject.usageSoFar(firstAccount));

		subject.putStorage(firstAccount, aLiteralKey, UInt256.ZERO);
		assertEquals(UInt256.ZERO, subject.getStorage(firstAccount, aLiteralKey));
		assertEquals(firstKvPairs - 1, subject.usageSoFar(firstAccount));
	}

	@Test
	void incorporatesNewAddition() {
		final var kvImpact = incorporateKvImpact(
				firstAKey, aValue,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(1, kvImpact);
		assertEquals(newMappings.get(firstAKey), aValue);
		assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
		assertEquals(updatedKeys.get(firstAKey.getContractId()).first(), firstAKey);
	}

	@Test
	void incorporatesNewUpdate() {
		given(storage.containsKey(firstAKey)).willReturn(true);
		final var kvImpact = incorporateKvImpact(
				firstAKey, aValue,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(0, kvImpact);
		assertEquals(newMappings.get(firstAKey), aValue);
		assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
		assertEquals(updatedKeys.get(firstAKey.getContractId()).first(), firstAKey);
	}

	@Test
	void incorporatesRecreatingUpdate() {
		given(storage.containsKey(firstAKey)).willReturn(true);
		removedKeys.computeIfAbsent(firstAKey.getContractId(), TREE_SET_FACTORY).add(firstAKey);
		final var kvImpact = incorporateKvImpact(
				firstAKey, aValue,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(1, kvImpact);
		assertEquals(newMappings.get(firstAKey), aValue);
		assertTrue(updatedKeys.containsKey(firstAKey.getContractId()));
		assertEquals(updatedKeys.get(firstAKey.getContractId()).first(), firstAKey);
		assertFalse(removedKeys.get(firstAKey.getContractId()).contains(firstAKey));
	}

	@Test
	void incorporatesOverwriteOfPendingUpdate() {
		given(storage.containsKey(firstAKey)).willReturn(true);
		newMappings.put(firstAKey, aValue);
		final var kvImpact = incorporateKvImpact(
				firstAKey, bValue,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(0, kvImpact);
		assertEquals(newMappings.get(firstAKey), bValue);
	}

	@Test
	void ignoresNoopZero() {
		final var kvImpact = incorporateKvImpact(
				firstAKey, ZERO_VALUE,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(0, kvImpact);
	}

	@Test
	void incorporatesErasingExtant() {
		given(storage.containsKey(firstAKey)).willReturn(true);
		final var kvImpact = incorporateKvImpact(
				firstAKey, ZERO_VALUE,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(-1, kvImpact);
		assertTrue(removedKeys.containsKey(firstAKey.getContractId()));
		assertEquals(removedKeys.get(firstAKey.getContractId()).first(), firstAKey);
	}

	@Test
	void incorporatesErasingPendingAndAlreadyPresent() {
		given(storage.containsKey(firstAKey)).willReturn(true);
		updatedKeys.computeIfAbsent(firstAKey.getContractId(), TREE_SET_FACTORY).add(firstAKey);
		newMappings.put(firstAKey, aValue);
		final var kvImpact = incorporateKvImpact(
				firstAKey, ZERO_VALUE,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(-1, kvImpact);
		assertTrue(removedKeys.containsKey(firstAKey.getContractId()));
		assertEquals(removedKeys.get(firstAKey.getContractId()).first(), firstAKey);
		assertTrue(updatedKeys.get(firstAKey.getContractId()).isEmpty());
		assertFalse(newMappings.containsKey(firstAKey));
	}

	@Test
	void incorporatesErasingPendingAndNotAlreadyPresent() {
		updatedKeys.computeIfAbsent(firstAKey.getContractId(), TREE_SET_FACTORY).add(firstAKey);
		newMappings.put(firstAKey, aValue);
		final var kvImpact = incorporateKvImpact(
				firstAKey, ZERO_VALUE,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(-1, kvImpact);
		assertFalse(removedKeys.containsKey(firstAKey.getContractId()));
		assertTrue(updatedKeys.get(firstAKey.getContractId()).isEmpty());
		assertFalse(newMappings.containsKey(firstAKey));
	}

	@Test
	void aPendingChangeMustBeReflectedInAnAdditionSet() {
		newMappings.put(firstAKey, aValue);
		assertThrows(NullPointerException.class, () -> incorporateKvImpact(
				firstAKey, ZERO_VALUE,
				updatedKeys, removedKeys, newMappings,
				storage));
	}

	@Test
	void incorporatesErasingNotAlreadyPending() {
		given(storage.containsKey(firstAKey)).willReturn(true);
		final var kvImpact = incorporateKvImpact(
				firstAKey, ZERO_VALUE,
				updatedKeys, removedKeys, newMappings,
				storage);

		assertEquals(-1, kvImpact);
		assertTrue(removedKeys.containsKey(firstAKey.getContractId()));
		assertEquals(removedKeys.get(firstAKey.getContractId()).first(), firstAKey);
	}

	/* --- Internal helpers --- */
	private void givenAccount(final AccountID id, final int initialKvPairs) {
		final var key = EntityNum.fromAccountId(id);
		final var account = mock(MerkleAccount.class);
		given(account.getNumContractKvPairs()).willReturn(initialKvPairs);
		given(accounts.get(key)).willReturn(account);
	}

	private static final AccountID firstAccount = IdUtils.asAccount("0.0.1234");
	private static final AccountID nextAccount = IdUtils.asAccount("0.0.2345");
	private static final UInt256 aLiteralKey = UInt256.fromHexString("0xaabbcc");
	private static final UInt256 bLiteralKey = UInt256.fromHexString("0xbbccdd");
	private static final UInt256 aLiteralValue = UInt256.fromHexString("0x1234aa");
	private static final UInt256 bLiteralValue = UInt256.fromHexString("0x1234bb");
	private static final ContractKey firstAKey = ContractKey.from(firstAccount, aLiteralKey);
	private static final ContractValue aValue = ContractValue.from(aLiteralValue);
	private static final ContractValue bValue = ContractValue.from(bLiteralValue);
	private static final int firstKvPairs = 5;
}
