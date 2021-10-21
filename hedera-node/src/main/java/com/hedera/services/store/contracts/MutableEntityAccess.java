package com.hedera.services.store.contracts;

import com.google.common.primitives.Bytes;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.virtualmap.VirtualMap;
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
