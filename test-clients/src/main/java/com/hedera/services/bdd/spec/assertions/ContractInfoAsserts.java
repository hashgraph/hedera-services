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

import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.junit.Assert;
import static com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;

public class ContractInfoAsserts extends BaseErroringAssertsProvider<ContractInfo> {
	public static ContractInfoAsserts infoKnownFor(String contract) {
		return contractWith().knownInfoFor(contract);
	}
	public static ContractInfoAsserts contractWith() { return new ContractInfoAsserts(); }

	public ContractInfoAsserts knownInfoFor(String contract) {
		registerProvider((spec, o) -> {
			ContractInfo actual = (ContractInfo)o;
			Assert.assertEquals("Bad contract id!",
					spec.registry().getContractId(contract), actual.getContractID());
			Assert.assertEquals("Bad account id!",
					TxnUtils.equivAccount(spec.registry().getContractId(contract)), actual.getAccountID());
			Assert.assertEquals("Bad admin key!",
					spec.registry().getKey(contract), actual.getAdminKey());
			ContractInfo otherExpectedInfo = spec.registry().getContractInfo(contract);
			Assert.assertEquals("Bad Solidity id!",
					otherExpectedInfo.getContractAccountID(), actual.getContractAccountID());
			Assert.assertEquals("Bad auto renew period!",
					otherExpectedInfo.getAutoRenewPeriod(), actual.getAutoRenewPeriod());
			Assert.assertEquals("Bad memo!",
					otherExpectedInfo.getMemo(), actual.getMemo());
		});
		return this;
	}

	public ContractInfoAsserts nonNullContractId() {
		registerProvider((spec, o) -> {
			Assert.assertTrue("Null contractId!", object2ContractInfo(o).hasContractID());
		});
		return this;
	}

	public ContractInfoAsserts solidityAddress(String contract) {
		registerProvider((spec, o) -> {
			Assert.assertEquals(
					"Bad Solidity address!",
					TxnUtils.solidityIdFrom(spec.registry().getContractId(contract)),
					TxnUtils.solidityIdFrom(object2ContractInfo(o).getContractID()));
		});
		return this;
	}

	public ContractInfoAsserts memo(String expectedMemo) {
		registerProvider((spec, o) -> {
			Assert.assertEquals("Bad memo!", expectedMemo, object2ContractInfo(o).getMemo());
		});
		return this;
	}

	public ContractInfoAsserts expiry(long expectedExpiry) {
		registerProvider((spec, o) -> {
			Assert.assertEquals("Bad expiry time!", expectedExpiry, object2ContractInfo(o).getExpirationTime().getSeconds());
		});
		return this;
	}

	public ContractInfoAsserts propertiesInheritedFrom(String contract) {
		registerProvider((spec, o) -> {
			ContractInfo expected = spec.registry().getContractInfo(contract);
			ContractInfo actual = object2ContractInfo(o);
			Assert.assertEquals("Bad expiry time!",
					expected.getExpirationTime(),
					actual.getExpirationTime());
			Assert.assertEquals("Bad auto renew period!",
					expected.getAutoRenewPeriod(),
					actual.getAutoRenewPeriod());
			Assert.assertEquals("Bad admin key!",
					expected.getAdminKey(),
					actual.getAdminKey());
			Assert.assertEquals("Bad memo!",
					expected.getMemo(),
					actual.getMemo());
		});
		return this;
	}

	public ContractInfoAsserts adminKey(String expectedKeyName) {
		registerProvider((spec, o) -> {
			Key expectedKey = spec.registry().getKey(expectedKeyName);
			Assert.assertEquals("Bad admin key!", expectedKey, object2ContractInfo(o).getAdminKey());
		});
		return this;
	}

	public ContractInfoAsserts nonNullAccountId() {
		registerProvider((spec, o) -> {
			Assert.assertTrue("Null accountId!", object2ContractInfo(o).hasAccountID());
		});
		return this;
	}

	public ContractInfoAsserts nonNullExpiration() {
		registerProvider((spec, o) -> {
			Assert.assertTrue("Null expiration time!", object2ContractInfo(o).hasExpirationTime());
		});
		return this;
	}

	private ContractInfo object2ContractInfo(Object o) {
		return (ContractInfo) o;
	}
}
