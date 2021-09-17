package com.hedera.services.store.contracts.world;

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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public interface HederaWorldUpdater extends WorldUpdater  {

	/**
	 * Allocates new Contract address based on the realm and shard of the sponsor
	 * IMPORTANT - The Id must be reclaimed if the MessageFrame reverts
	 *
	 * @param sponsor sponsor of the new contract
	 * @return newly generated contract {@link Address}
	 */
	Address allocateNewContractAddress(Address sponsor);

}
