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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.Objects;

public class StaticEntityAccess implements EntityAccess {
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final MerkleMap<EntityNum, MerkleAccount> accounts;
	private final VirtualMap<ContractKey, ContractValue> storage;
	private final VirtualMap<VirtualBlobKey, VirtualBlobValue> blobs;

	public StaticEntityAccess(
			final StateView stateView,
			final OptionValidator validator,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;

		this.blobs = stateView.storage();
		this.storage = stateView.contractStorage();
		this.accounts = stateView.accounts();
	}

	@Override
	public void begin() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void commit() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void rollback() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String currentManagedChangeSet() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void spawn(EntityNum id, long balance, HederaAccountCustomizer customizer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void customize(final EntityNum id, final HederaAccountCustomizer customizer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void adjustBalance(final EntityNum id, final long adjustment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getAutoRenew(final EntityNum id) {
		return accounts.get(id).getAutoRenewSecs();
	}

	@Override
	public long getBalance(final EntityNum id) {
		return accounts.get(id).getBalance();
	}

	@Override
	public long getExpiry(final EntityNum id) {
		return accounts.get(id).getExpiry();
	}

	@Override
	public JKey getKey(final EntityNum id) {
		return accounts.get(id).getAccountKey();
	}

	@Override
	public String getMemo(final EntityNum id) {
		return accounts.get(id).getMemo();
	}

	@Override
	public EntityId getProxy(EntityNum id) {
		return accounts.get(id).getProxy();
	}

	@Override
	public boolean isDetached(final EntityNum id) {
		if (!dynamicProperties.autoRenewEnabled()) {
			return false;
		}
		final var account = accounts.get(id);
		Objects.requireNonNull(account);
		return !account.isSmartContract()
				&& account.getBalance() == 0
				&& !validator.isAfterConsensusSecond(account.getExpiry());
	}

	@Override
	public boolean isDeleted(final EntityNum id) {
		return accounts.get(id).isDeleted();
	}

	@Override
	public boolean isExtant(final EntityNum id) {
		return accounts.get(id) != null;
	}

	@Override
	public void putStorage(final EntityNum id, final UInt256 key, final UInt256 value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public UInt256 getStorage(final EntityNum id, final UInt256 key) {
		final var contractKey = new ContractKey(id.longValue(), key.toArray());
		ContractValue value = storage.get(contractKey);
		return value == null ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(value.getValue()));
	}

	@Override
	public void storeCode(final EntityNum id, final Bytes code) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bytes fetchCode(final EntityNum id) {
		final var blobKey = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, id.intValue());
		var bytes = blobs
				.get(blobKey);

		return bytes == null ? Bytes.EMPTY : Bytes.of(bytes.getData());
	}
}
