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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractStateChange;

import java.util.ArrayList;
import java.util.List;

public class UtilStateChange {
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
}
