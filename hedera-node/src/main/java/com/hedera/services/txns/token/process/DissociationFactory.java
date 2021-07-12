package com.hedera.services.txns.token.process;

import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;

@FunctionalInterface
public interface DissociationFactory {
	Dissociation loadFrom(TypedTokenStore tokenStore, Account account, Id tokenId);
}
