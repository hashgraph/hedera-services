package com.hedera.services.store.contracts;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class SizeLimitedStorage {
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> storage;

	private Map<Long, Long> currentUsages = new HashMap<>();
	private Map<Long, AtomicLong> usageDeltas = new HashMap<>();
	private Map<Long, TreeSet<ContractKey>> addedKeys = new TreeMap<>();
	private Map<Long, TreeSet<ContractKey>> removedKeys = new TreeMap<>();
	private Map<ContractKey, ContractValue> addedMappings = new HashMap<>();

	public SizeLimitedStorage(
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<VirtualMap<ContractKey, ContractValue>> storage
	) {
		this.dynamicProperties = dynamicProperties;
		this.accounts = accounts;
		this.storage = storage;
	}

	public void reset() {
		throw new AssertionError("Not implemented");
	}

	public void commit() {
		throw new AssertionError("Not implemented");
	}

	public UInt256 getStorage(final AccountID id, final UInt256 key) {
		throw new AssertionError("Not implemented");
	}

	public void putStorage(final AccountID id, final UInt256 key, final UInt256 value) {
		throw new AssertionError("Not implemented");
	}
}
