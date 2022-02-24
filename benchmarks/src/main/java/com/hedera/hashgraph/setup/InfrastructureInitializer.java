package com.hedera.hashgraph.setup;

/*-
 * ‌
 * Hedera Services JMH benchmarks
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;

public class InfrastructureInitializer {
	private final int initNumContracts;
	private final int initNumKvPairs;

	public InfrastructureInitializer(final int initContracts, final int initKvPairs) {
		this.initNumContracts = initContracts;
		this.initNumKvPairs = initKvPairs;
	}

	public void setup(final StorageInfrastructure infrastructure) {
		final var curAccounts = infrastructure.accounts().get();
		final var curStorage = infrastructure.storage().get();

		// Uniform distribution of K/V pairs across contracts
		final var perContractKvPairs = initNumKvPairs / initNumContracts;

		final var perCreationPrint = initNumContracts / 10;
		for (int i = 0; i < initNumContracts; i++) {
			final var contractId = AccountID.newBuilder().setAccountNum(i + 1L).build();
			for (int j = 0; j < perContractKvPairs; j++) {
				final var evmKey = EvmKeyValueSource.uniqueKey(j);
				final var vmKey = ContractKey.from(contractId, evmKey);
				final var vmValue = ContractValue.from(evmKey);
				curStorage.put(vmKey, vmValue);
			}

			final var contract = new MerkleAccount();
			contract.setSmartContract(true);
			contract.setNumContractKvPairs(perContractKvPairs);
			curAccounts.put(EntityNum.fromAccountId(contractId), contract);
			final var created = i + 1;
			if (created % perCreationPrint == 0) {
				System.out.println("  -> " + created + " contracts now created ("
						+ (created * perContractKvPairs) + " K/V pairs)");
			}
		}
	}
}
