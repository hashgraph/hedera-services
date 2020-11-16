package com.hedera.services.bdd.spec.persistence;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;

import java.util.Optional;

import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY;
import static com.hedera.services.bdd.spec.persistence.PemKey.RegistryForms.under;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;

public class Account {
	private static final Long UNSPECIFIED_BALANCE = null;

	private Long balance = UNSPECIFIED_BALANCE;
	private PemKey key = UNUSED_KEY;

	public void registerWhatIsKnown(HapiApiSpec spec, String name, Optional<EntityId> entityId) {
		if (key == UNUSED_KEY) {
			throw new IllegalStateException(String.format("Account '%s' has no given key!", name));
		}
		key.registerWith(spec, under(name));
		entityId.ifPresent(id -> {
			spec.registry().saveAccountId(name, id.asAccount());
		});
	}

	public HapiSpecOperation createOp(String name) {
		var op = cryptoCreate(name)
				.key(name)
				.advertisingCreation();
		if (balance != UNSPECIFIED_BALANCE) {
			op.balance(balance);
		}
		return op;
	}

	public PemKey getKey() {
		return key;
	}

	public void setKey(PemKey key) {
		this.key = key;
	}

	public Long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}
}
