package com.hedera.services.state.validation;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.fcmap.FCMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class BasedLedgerValidator implements LedgerValidator {
	private final long expectedFloat;

	private final HederaNumbers hederaNums;
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public BasedLedgerValidator(
			HederaNumbers hederaNums,
			@CompositeProps PropertySource properties,
			GlobalDynamicProperties dynamicProperties
	) {
		this.expectedFloat = properties.getLongProperty("ledger.totalTinyBarFloat");

		this.hederaNums = hederaNums;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void validate(FCMap<MerkleEntityId, MerkleAccount> accounts) {
		var totalFloat = new AtomicReference<>(BigInteger.ZERO);
		MiscUtils.forEach(accounts, (id, account) -> {
			if (id.getRealm() != hederaNums.realm()) {
				throw new IllegalStateException(String.format("Invalid realm in account %s", id.toAbbrevString()));
			}
			if (id.getShard() != hederaNums.shard()) {
				throw new IllegalStateException(String.format("Invalid shard in account %s", id.toAbbrevString()));
			}
			if (id.getNum() < 1 || id.getNum() > dynamicProperties.maxAccountNum()) {
				throw new IllegalStateException(String.format("Invalid num in account %s", id.toAbbrevString()));
			}
			totalFloat.set(totalFloat.get().add(BigInteger.valueOf(account.getBalance())));
		});
		try {
			final var actualFloat = totalFloat.get().longValueExact();
			if (actualFloat != expectedFloat) {
				throw new IllegalStateException("Wrong ℏ float, expected " + expectedFloat + " but was " + actualFloat);
			}
		} catch (ArithmeticException ae) {
			throw new IllegalStateException("Wrong ℏ float, expected " + expectedFloat + " but overflowed instead");
		}
	}
}
