package com.hedera.node.app.spi;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.state.States;

import javax.annotation.Nonnull;

public interface Service {
    @Nonnull
    PreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
