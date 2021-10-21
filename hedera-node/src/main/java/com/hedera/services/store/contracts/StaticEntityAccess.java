package com.hedera.services.store.contracts;

import com.google.common.primitives.Bytes;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.tuweni.units.bigints.UInt256;

public class StaticEntityAccess implements EntityAccess {
	private final MerkleMap<EntityNum, MerkleAccount> accounts;
	private final VirtualMap<ContractKey, ContractValue> storage;
	private final VirtualMap<VirtualBlobKey, VirtualBlobValue> blobs;

	public StaticEntityAccess(final StateView stateView) {
		blobs = stateView.storage();
		storage = stateView.contractStorage();
		accounts = stateView.accounts();
	}

	@Override
	public void spawn(AccountID id, long balance, HederaAccountCustomizer customizer) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void customize(AccountID id, HederaAccountCustomizer customizer) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void adjustBalance(AccountID id, long adjustment) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public long getBalance(AccountID id) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public boolean isDeleted(AccountID id) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public boolean isExtant(AccountID id) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public MerkleAccount lookup(AccountID id) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void put(AccountID id, UInt256 key, UInt256 value) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public UInt256 get(AccountID id, UInt256 key) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public Bytes store(AccountID id, Bytes code) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public Bytes fetch(AccountID id) {
		throw new AssertionError("Not implemented");
	}
}
