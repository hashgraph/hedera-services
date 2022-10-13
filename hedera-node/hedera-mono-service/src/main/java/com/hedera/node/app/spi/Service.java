package com.hedera.node.app.spi;

import com.hedera.node.app.spi.state.States;

import javax.annotation.Nonnull;

/**
 * A definition of an interface that will be implemented by each conceptual "service" like crypto-service,
 * token-service etc.,
 */
public interface Service {
    /**
     * Creates and returns a new {@link PreTransactionHandler}
     *
     * @return A new {@link PreTransactionHandler}
     */
    @Nonnull
    PreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
