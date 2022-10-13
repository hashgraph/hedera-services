package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.spi.state.States;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of the {@link CryptoService} interface.
 */
public class CryptoServiceImpl implements CryptoService {
	@NotNull
	@Override
	public CryptoPreTransactionHandler createPreTransactionHandler(@NotNull final States states) {
		final var store = new AccountStore(states);
		return new CryptoPreTransactionHandlerImpl(store);
	}
}
