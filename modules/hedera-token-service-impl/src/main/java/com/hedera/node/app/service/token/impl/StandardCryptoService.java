package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Standard implementation of the {@link CryptoService} {@link com.hedera.node.app.spi.Service}. */
public final class StandardCryptoService implements CryptoService {

	@NonNull
	@Override
	public CryptoPreTransactionHandler createPreTransactionHandler(@NonNull final States states) {
		throw new UnsupportedOperationException("Not yet implemented");
	}
}
