package com.hedera.services.bdd.spec.utilops;

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
