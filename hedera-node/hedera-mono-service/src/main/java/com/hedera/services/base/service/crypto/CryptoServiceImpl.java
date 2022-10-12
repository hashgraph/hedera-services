package com.hedera.services.base.service.crypto;

import com.hedera.services.base.state.States;
import com.hedera.services.base.store.AccountStore;
import org.jetbrains.annotations.NotNull;

public class CryptoServiceImpl implements CryptoService{
	@NotNull
	@Override
	public CryptoPreTransactionHandler createPreTransactionHandler(@NotNull final States states) {
		final var store = new AccountStore(states);
		return new CryptoPreTransactionHandlerImpl(store);
	}
}
