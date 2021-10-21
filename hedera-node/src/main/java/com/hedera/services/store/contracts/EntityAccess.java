package com.hedera.services.store.contracts;

import com.google.common.primitives.Bytes;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.units.bigints.UInt256;

public interface EntityAccess {
	/* --- Account access --- */
	void spawn(AccountID id, long balance, HederaAccountCustomizer customizer);
	void customize(AccountID id, HederaAccountCustomizer customizer);
	void adjustBalance(AccountID id, long adjustment);
	long getBalance(AccountID id);
	boolean isDeleted(AccountID id);
	boolean isExtant(AccountID id);
	MerkleAccount lookup(AccountID id);

	/* --- Storage access --- */
	void put(AccountID id,UInt256 key, UInt256 value);
	UInt256 get(AccountID id, UInt256 key);

	/* --- Bytecode access --- */
	Bytes store(AccountID id, Bytes code);
	Bytes fetch(AccountID id);
}
