package com.hedera.services.ledger.accounts;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.ledger.SigImpactHistorian;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class StackedContractAliases extends AbstractContractAliases {
	private final ContractAliases wrappedAliases;

	private Map<Address, Address> updatedAliases = null;

	public StackedContractAliases(ContractAliases wrappedAliases) {
		this.wrappedAliases = wrappedAliases;
	}

	@Override
	public void commit(@Nullable SigImpactHistorian observer) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void unlinkIfUsed(Address alias) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public void linkIfUnused(Address alias, Address address) {
		if (!isMirror(address)) {
			throw new IllegalArgumentException("Cannot link alias " + alias + " to non-mirror address " + address);
		}
		throw new AssertionError("Not implemented");
	}

	@Override
	public Address resolveForEvm(Address addressOrAlias) {
		if (isMirror(addressOrAlias)) {
			return addressOrAlias;
		}
		throw new AssertionError("Not implemented");
	}

	@VisibleForTesting
	Map<Address, Address> getUpdatedAliases() {
		return updatedAliases;
	}
}
