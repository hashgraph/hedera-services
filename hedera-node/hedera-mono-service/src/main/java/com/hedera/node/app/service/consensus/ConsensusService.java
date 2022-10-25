package com.hedera.node.app.service.consensus;

import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public interface ConsensusService extends Service {
    @Override
    @Nonnull
    ConsensusPreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
