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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

@Singleton
public class MutableEntityAccess implements EntityAccess {
	private final HederaLedger ledger;
	private final Supplier<VirtualMap<ContractKey, ContractValue>> storage;
	private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

	@Inject
	public MutableEntityAccess(
			final HederaLedger ledger,
			final Supplier<VirtualMap<ContractKey, ContractValue>> storage,
			final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode
	) {
		this.ledger = ledger;
		this.storage = storage;
		this.bytecode = bytecode;
	}

	@Override
	public void spawn(AccountID id, long balance, HederaAccountCustomizer customizer) {
		ledger.spawn(id, balance, customizer);
	}

	@Override
	public void customize(AccountID id, HederaAccountCustomizer customizer) {
		ledger.customizePotentiallyDeleted(id, customizer);
	}

	@Override
	public void adjustBalance(AccountID id, long adjustment) {
		ledger.adjustBalance(id, adjustment);
	}

	@Override
	public long getAutoRenew(AccountID id) {
		return ledger.autoRenewPeriod(id);
	}

	@Override
	public long getBalance(AccountID id) {
		return ledger.getBalance(id);
	}

	@Override
	public long getExpiry(AccountID id) {
		return ledger.expiry(id);
	}

	@Override
	public JKey getKey(AccountID id) {
		return ledger.key(id);
	}

	@Override
	public String getMemo(AccountID id) {
		return ledger.memo(id);
	}

	@Override
	public EntityId getProxy(AccountID id) {
		return ledger.proxy(id);
	}

	@Override
	public boolean isDeleted(AccountID id) {
		return ledger.isDeleted(id);
	}

	@Override
	public boolean isExtant(AccountID id) {
		return ledger.exists(id);
	}

	@Override
	public void put(AccountID id, UInt256 key, UInt256 value) {
		final var contractKey = new ContractKey(id.getAccountNum(), key.toArray());

		if (value.isZero()) {
			storage.get().put(contractKey, new ContractValue());
		} else {
			storage.get().put(contractKey, new ContractValue(value.toArray()));
		}
	}

	@Override
	public UInt256 get(AccountID id, UInt256 key) {
		final var contractKey = new ContractKey(id.getAccountNum(), key.toArray());
		ContractValue value = storage.get().get(contractKey);
		return value == null ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(value.getValue()));
	}

	@Override
	public void store(AccountID id, Bytes code) {
		final var key = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, (int) id.getAccountNum());
		final var value = new VirtualBlobValue(code.toArray());

		bytecode.get().put(key, value);
	}

	@Override
	public Bytes fetch(AccountID id) {
		final var blobKey = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, (int) id.getAccountNum());
		var bytes = bytecode.get()
				.get(blobKey);

		return bytes == null ? Bytes.EMPTY : Bytes.of(bytes.getData());
	}
}
