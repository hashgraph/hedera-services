package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ContractID;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.List;

/**
 * Hedera adapted interface for a view over the accounts of the world state and methods for persisting state changes
 */
public interface HederaMutableWorldState extends WorldState, WorldView {

	/**
	 * Allocates new Id address based on the realm and shard of the sponsor
	 * IMPORTANT - The Id must be reclaimed if the MessageFrame reverts
	 *
	 * @param sponsor sponsor of the new contract
	 * @return newly generated Id
	 */
	Address newContractAddress(Address sponsor);

	/**
	 * Reclaims the last created {@link Id}
	 */
	void reclaimContractId();

	/**
	 * Creates an updater for this mutable world view.
	 *
	 * @return a new updater for this mutable world view. On commit, change made to this updater will
	 * become visible on this view.
	 */
	HederaWorldUpdater updater();

	/**
	 * Returns the list of ContractIDs created by the current transaction
	 * Clears the collected list after execution.
	 *
	 * @return the list of ContractIDs created by this transaction.
	 */
	List<ContractID> persistProvisionalContractCreations();

	/**
	 * Customizes sponsored accounts
	 */
	void customizeSponsoredAccounts();
}
