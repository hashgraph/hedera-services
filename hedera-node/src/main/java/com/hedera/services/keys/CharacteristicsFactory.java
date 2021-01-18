package com.hedera.services.keys;

import com.hedera.services.files.HederaFs;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;

public class CharacteristicsFactory {
	private final HederaFs hfs;

	public CharacteristicsFactory(HederaFs hfs) {
		this.hfs = hfs;
	}

	public KeyActivationCharacteristics inferredFor(TransactionBody txn) {
		return DEFAULT_ACTIVATION_CHARACTERISTICS;
	}
}
