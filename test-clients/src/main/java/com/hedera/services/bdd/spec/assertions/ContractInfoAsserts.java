package com.hedera.services.bdd.spec.assertions;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.Key;

import static com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractInfoAsserts extends BaseErroringAssertsProvider<ContractInfo> {
	public static ContractInfoAsserts infoKnownFor(String contract) {
		return contractWith().knownInfoFor(contract);
	}

	public static ContractInfoAsserts contractWith() {
		return new ContractInfoAsserts();
	}

	public ContractInfoAsserts knownInfoFor(String contract) {
		registerProvider((spec, o) -> {
			ContractInfo actual = (ContractInfo) o;
			assertEquals(spec.registry().getContractId(contract), actual.getContractID(),
					"Bad contract id!");
			assertEquals(TxnUtils.equivAccount(spec.registry().getContractId(contract)),
					actual.getAccountID(),
					"Bad account id!");
			ContractInfo otherExpectedInfo = spec.registry().getContractInfo(contract);
			assertEquals(
					otherExpectedInfo.getContractAccountID(), actual.getContractAccountID(),
					"Bad Solidity id!");
			assertEquals(spec.registry().getKey(contract), actual.getAdminKey(),
					"Bad admin key!");
			assertTrue(object2ContractInfo(o).getExpirationTime().getSeconds() != 0,
					"Expiry must not be null!");
			assertEquals(otherExpectedInfo.getAutoRenewPeriod(), actual.getAutoRenewPeriod(),
					"Bad auto renew period!");
			assertEquals(otherExpectedInfo.getMemo(), actual.getMemo(),
					"Bad memo!");
		});
		return this;
	}

	public ContractInfoAsserts nonNullContractId() {
		registerProvider((spec, o) -> {
			assertTrue(object2ContractInfo(o).hasContractID(), "Null contractId!");
		});
		return this;
	}

	public ContractInfoAsserts solidityAddress(String contract) {
		registerProvider((spec, o) -> {
			assertEquals(
					TxnUtils.solidityIdFrom(spec.registry().getContractId(contract)),
					TxnUtils.solidityIdFrom(object2ContractInfo(o).getContractID()),
					"Bad Solidity address!");
		});
		return this;
	}

	public ContractInfoAsserts memo(String expectedMemo) {
		registerProvider((spec, o) -> {
			assertEquals(expectedMemo, object2ContractInfo(o).getMemo(), "Bad memo!");
		});
		return this;
	}

	public ContractInfoAsserts expiry(long expectedExpiry) {
		registerProvider((spec, o) -> {
			assertEquals(expectedExpiry, object2ContractInfo(o).getExpirationTime().getSeconds(),
					"Bad expiry time!");
		});
		return this;
	}

	public ContractInfoAsserts propertiesInheritedFrom(String contract) {
		registerProvider((spec, o) -> {
			ContractInfo expected = spec.registry().getContractInfo(contract);
			ContractInfo actual = object2ContractInfo(o);
			assertEquals(
					expected.getExpirationTime(),
					actual.getExpirationTime(),
					"Bad expiry time!");
			assertEquals(
					expected.getAutoRenewPeriod(),
					actual.getAutoRenewPeriod(),
					"Bad auto renew period!");
			assertEquals(expected.getAdminKey(),
					actual.getAdminKey(),
					"Bad admin key!");
			assertEquals(
					expected.getMemo(),
					actual.getMemo(),
					"Bad memo!");
		});
		return this;
	}

	public ContractInfoAsserts adminKey(String expectedKeyName) {
		registerProvider((spec, o) -> {
			Key expectedKey = spec.registry().getKey(expectedKeyName);
			assertEquals(expectedKey, object2ContractInfo(o).getAdminKey(), "Bad admin key!");
		});
		return this;
	}

	public ContractInfoAsserts immutableContractKey(String name) {
		registerProvider((spec, o) -> {
			final var actualKey = object2ContractInfo(o).getAdminKey();
			assertTrue(actualKey.hasContractID(),
					"Expected a contract admin key, got " + actualKey);
			if (TxnUtils.isIdLiteral(name)) {
				assertEquals(HapiPropertySource.asContract(name), actualKey.getContractID(),
						"Wrong immutable contract key");
			} else {
				assertEquals(spec.registry().getContractId(name), actualKey.getContractID(),
						"Wrong immutable contract key");
			}
		});
		return this;
	}

	public ContractInfoAsserts autoRenew(long expectedAutoRenew) {
		registerProvider((spec, o) -> {
			assertEquals(expectedAutoRenew, object2ContractInfo(o).getAutoRenewPeriod().getSeconds(), "Bad" +
					" autoRenew!");
		});
		return this;
	}

	public ContractInfoAsserts numKvPairs(int expectedKvPairs) {
		/* EVM storage maps 32-byte keys to 32-byte values */
		final long numStorageBytes = expectedKvPairs * 64L;
		registerProvider((spec, o) -> {
			assertEquals(numStorageBytes, object2ContractInfo(o).getStorage(), "Bad storage size!");
		});
		return this;
	}

	private ContractInfo object2ContractInfo(Object o) {
		return (ContractInfo) o;
	}
}
