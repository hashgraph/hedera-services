package com.hedera.node.app.service.mono.token.impl;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

/** An implementation of the {@link CryptoService} interface. */
public class TokenServiceImpl implements TokenService {
    @NonNull
    @Override
    public TokenPreTransactionHandler createPreTransactionHandler(@NonNull final States states, @NonNull final PreHandleContext ctx) {
        Objects.requireNonNull(states);
        Objects.requireNonNull(ctx);
        final var accountStore = new AccountStore(states);
        final var tokenStore = new TokenStore(states);
        return new TokenPreTransactionHandlerImpl(accountStore, tokenStore, ctx);
    }
}
