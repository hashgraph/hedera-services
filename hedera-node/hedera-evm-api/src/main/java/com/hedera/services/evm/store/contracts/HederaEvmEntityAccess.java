package com.hedera.services.evm.store.contracts;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public interface HederaEvmEntityAccess {
	long getBalance(Address address);

	boolean isTokenAccount(Address address);

	ByteString alias(Address address);

	boolean isExtant(Address address);

	UInt256 getStorage(Address address, UInt256 key);

	/**
	 * Returns the bytecode for the contract with the given account id; or null if there is no byte
	 * present for this contract.
	 *
	 * @param address the account's address  of the target contract
	 * @return the target contract's bytecode, or null if it is not present
	 */

	Bytes fetchCodeIfPresent(Address address);
}
