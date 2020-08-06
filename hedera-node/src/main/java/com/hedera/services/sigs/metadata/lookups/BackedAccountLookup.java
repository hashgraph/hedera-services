package com.hedera.services.sigs.metadata.lookups;

import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;

public class BackedAccountLookup implements AccountSigMetaLookup {
	private final BackingAccounts<AccountID, MerkleAccount> accounts;

	public BackedAccountLookup(BackingAccounts<AccountID, MerkleAccount> accounts) {
		this.accounts = accounts;
	}

	@Override
	public AccountSigningMetadata lookup(AccountID id) throws Exception {
		if (accounts.contains(id)) {
			var account = accounts.getRef(id);
			return new AccountSigningMetadata(account.getKey(), account.isReceiverSigRequired());
		} else {
			throw new InvalidAccountIDException("Invalid account!", id);
		}
	}
}
