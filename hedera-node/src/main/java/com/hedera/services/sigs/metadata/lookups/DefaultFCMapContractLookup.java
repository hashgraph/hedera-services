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

import com.hedera.services.sigs.metadata.ContractSigningMetadata;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.exception.AdminKeyNotExistException;
import com.hedera.services.legacy.exception.InvalidContractIDException;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromContractId;

/**
 * Contract signing metadata lookup backed by a {@code FCMap<MapKey, MapValue}.
 *
 * <b>NOTE:</b> The conditions on the lookup imply that that no contract
 * without an admin key can ever be validly referenced by a transaction.
 *
 * @author Michael Tinker
 */
public class DefaultFCMapContractLookup implements ContractSigMetaLookup {
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public DefaultFCMapContractLookup(Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts) {
		this.accounts = accounts;
	}

	/**
	 * Returns metadata for the given smart contract's signing activity if
	 * such metadata exists in a permissible account in the backing {@code FCMap}.
	 * <b>NOTE:</b> in particular, only accounts with an admin key are permissible.
	 *
	 * @param id the smart contract to recover signing metadata for.
	 * @return the desired metadata.
	 * @throws InvalidContractIDException if there is no non-deleted account representing the given contract.
	 * @throws AdminKeyNotExistException if the representing account exists but has no signing key.
	 */
	@Override
	public ContractSigningMetadata lookup(ContractID id) throws Exception {
		MerkleAccount contract = accounts.get().get(fromContractId(id));
		if (contract == null || contract.isAccountDeleted() || !contract.isSmartContract()) {
			throw new InvalidContractIDException("Invalid contract!", id);
		} else if (contract.getKey() == null) {
			throw new AdminKeyNotExistException("Contract should never be referenced by a txn (missing key)!", id);
		} else if (contract.getKey() instanceof JContractIDKey) {
			throw new AdminKeyNotExistException("Contract should never be referenced by a txn (no admin key)!", id);
		} else {
			return new ContractSigningMetadata(contract.getKey());
		}
	}
}
