package com.hedera.node.app.service.evm.store.models;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class MockEntityAccess implements HederaEvmEntityAccess {
	@Override
	public boolean isUsable(Address address) {
		return false;
	}

	@Override
	public long getBalance(Address address) {
		return 100;
	}

	@Override
	public boolean isTokenAccount(Address address) {
		return false;
	}

	@Override
	public ByteString alias(Address address) {
		return null;
	}

	@Override
	public boolean isExtant(Address address) {
		return false;
	}

	@Override
	public Bytes getStorage(Address address, Bytes key) {
		return null;
	}

	@Override
	public Bytes fetchCodeIfPresent(Address address) {
		return null;
	}
}
