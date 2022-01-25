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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.Objects;

import static com.hedera.services.state.merkle.internals.BitPackUtils.codeFromNum;
import static com.hedera.services.utils.EntityNum.fromAccountId;

public class StaticEntityAccess implements EntityAccess {
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final MerkleMap<EntityNum, MerkleAccount> accounts;
	private final VirtualMap<ContractKey, ContractValue> storage;
	private final VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode;

	public StaticEntityAccess(
			final StateView stateView,
			final OptionValidator validator,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;

		this.bytecode = stateView.storage();
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
	public void spawn(AccountID id, long balance, HederaAccountCustomizer customizer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void customize(final AccountID id, final HederaAccountCustomizer customizer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void adjustBalance(AccountID id, long adjustment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getAutoRenew(AccountID id) {
		return accounts.get(fromAccountId(id)).getAutoRenewSecs();
	}

	@Override
	public long getBalance(AccountID id) {
		return accounts.get(fromAccountId(id)).getBalance();
	}

	@Override
	public long getExpiry(AccountID id) {
		return accounts.get(fromAccountId(id)).getExpiry();
	}

	@Override
	public JKey getKey(AccountID id) {
		return accounts.get(fromAccountId(id)).getAccountKey();
	}

	@Override
	public String getMemo(AccountID id) {
		return accounts.get(fromAccountId(id)).getMemo();
	}

	@Override
	public EntityId getProxy(AccountID id) {
		return accounts.get(fromAccountId(id)).getProxy();
	}

	@Override
	public boolean isDetached(AccountID id) {
		if (!dynamicProperties.autoRenewEnabled()) {
			return false;
		}
		final var account = accounts.get(fromAccountId(id));
		Objects.requireNonNull(account);
		return !account.isSmartContract()
				&& account.getBalance() == 0
				&& !validator.isAfterConsensusSecond(account.getExpiry());
	}

	@Override
	public boolean isDeleted(AccountID id) {
		return accounts.get(fromAccountId(id)).isDeleted();
	}

	@Override
	public boolean isExtant(AccountID id) {
		return accounts.get(fromAccountId(id)) != null;
	}

	@Override
	public void putStorage(AccountID id, UInt256 key, UInt256 value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public UInt256 getStorage(AccountID id, UInt256 key) {
		final var contractKey = new ContractKey(id.getAccountNum(), key.toArray());
		ContractValue value = storage.get(contractKey);
		return value == null ? UInt256.ZERO : UInt256.fromBytes(Bytes32.wrap(value.getValue()));
	}

	@Override
	public void flushStorage() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void storeCode(AccountID id, Bytes code) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bytes fetchCodeIfPresent(final AccountID id) {
		return explicitCodeFetch(bytecode, id);
	}

	static Bytes explicitCodeFetch(
			final VirtualMap<VirtualBlobKey, VirtualBlobValue> bytecode,
			final AccountID id
	) {
		final var key = new VirtualBlobKey(VirtualBlobKey.Type.CONTRACT_BYTECODE, codeFromNum(id.getAccountNum()));
		final var value = bytecode.get(key);
		return (value != null) ? Bytes.of(value.getData()) : null;
	}

	@Override
	public void recordNewKvUsageTo(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		throw new UnsupportedOperationException();
	}
}
