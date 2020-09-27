package com.hedera.services.sigs.metadata.lookups;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.metadata.ContractSigningMetadata;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.exception.AdminKeyNotExistException;
import com.hedera.services.legacy.exception.InvalidContractIDException;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.state.merkle.MerkleEntityId.fromContractId;

public class DefaultFCMapContractLookup implements ContractSigMetaLookup {
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public DefaultFCMapContractLookup(Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts) {
		this.accounts = accounts;
	}

	@Override
	public ContractSigningMetadata lookup(ContractID id) throws Exception {
		MerkleAccount contract = accounts.get().get(fromContractId(id));
		if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
			throw new InvalidContractIDException("Invalid contract!", id);
		} else if (contract.getKey() == null) {
			throw new AdminKeyNotExistException("Contract should never be referenced by a txn (missing key)!", id);
		} else if (contract.getKey() instanceof JContractIDKey) {
			throw new AdminKeyNotExistException("Contract should never be referenced by a txn (no admin key)!", id);
		} else {
			return new ContractSigningMetadata(contract.getKey(), contract.isReceiverSigRequired());
		}
	}

	@Override
	public SafeLookupResult<ContractSigningMetadata> safeLookup(ContractID id) {
		var contract = accounts.get().get(fromContractId(id));
		if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
			return SafeLookupResult.failure(INVALID_CONTRACT);
		} else {
			JKey key;
			if ((key = contract.getKey()) == null || key instanceof JContractIDKey) {
				return SafeLookupResult.failure(INVALID_CONTRACT);
			} else {
				return new SafeLookupResult<>(new ContractSigningMetadata(key, contract.isReceiverSigRequired()));
			}
		}
	}
}
