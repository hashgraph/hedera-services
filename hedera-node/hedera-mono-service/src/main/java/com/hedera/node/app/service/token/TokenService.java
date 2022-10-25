package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;

import javax.annotation.Nonnull;

/**
 * <p>Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/token_service.proto">Token
 * Service</a>.
 */
public interface TokenService extends Service {
    /**
     * Creates the token service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding token service pre-handler
     */
    @Override
    @Nonnull
    CryptoPreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
