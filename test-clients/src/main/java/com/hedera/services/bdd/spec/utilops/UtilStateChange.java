package com.hedera.services.bdd.spec.utilops;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.inventory.NewSpecKey;
import com.hederahashgraph.api.proto.java.ContractStateChange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HUNDRED_HBARS;

public class UtilStateChange {

	public static final String secp256k1SourceKey = "secp256k1Alias";
	public static final KeyShape secp256k1Shape = KeyShape.SECP256K1;
	private static final Map<String, Boolean> specToInitializedEthereumContract = new HashMap<>();
	private static final Map<String, Boolean> specToBeenExecuted = new HashMap<>();

	public static List<ContractStateChange> stateChangesToGrpc(List<StateChange> stateChanges, HapiApiSpec spec) {
		final List<ContractStateChange> additions = new ArrayList<>();

		for (StateChange stateChange : stateChanges) {
			final var addition = ContractStateChange.newBuilder()
					.setContractID(TxnUtils.asContractId(stateChange.getContractID(), spec));

			for (StorageChange storageChange : stateChange.getStorageChanges()) {
				var newStorageChange = com.hederahashgraph.api.proto.java.StorageChange.newBuilder()
						.setSlot(storageChange.getSlot())
						.setValueRead(storageChange.getValueRead());


				if (storageChange.getValueWritten() != null) {
					newStorageChange.setValueWritten(storageChange.getValueWritten());
				}

				addition.addStorageChanges(newStorageChange.build());
			}

			additions.add(addition.build());
		}

		return additions;
	}

	public static void initializeEthereumAccountForSpec(final HapiApiSpec spec) {
		final var newSpecKey = new NewSpecKey(secp256k1SourceKey).shape(secp256k1Shape);
		final var cryptoTransfer = new HapiCryptoTransfer(HapiCryptoTransfer.tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS));

		newSpecKey.execFor(spec);
		cryptoTransfer.execFor(spec);

		specToInitializedEthereumContract.putIfAbsent(spec.getName(), true);
	}

	public static boolean isEthereumAccountCreatedForSpec(final String spec) {
		return specToInitializedEthereumContract.containsKey(spec);
	}

	public static void markSpecAsBeenExecuted(final String spec) {
		specToBeenExecuted.putIfAbsent(spec, true);
	}

	public static boolean hasSpecBeenExecuted(final String spec) {
		if(specToBeenExecuted.containsKey(spec)) {
			return specToBeenExecuted.get(spec);
		} else {
			return false;
		}
	}
}
