package com.hedera.services.ledger.accounts;

import com.hedera.services.ledger.SigImpactHistorian;
import org.hyperledger.besu.datatypes.Address;

import javax.annotation.Nullable;

public interface ContractAliases {
	void commit(@Nullable SigImpactHistorian observer);

	void unlinkIfUsed(Address alias);

	void linkIfUnused(Address alias, Address address);

	Address resolveForEvm(Address addressOrAlias);
}
