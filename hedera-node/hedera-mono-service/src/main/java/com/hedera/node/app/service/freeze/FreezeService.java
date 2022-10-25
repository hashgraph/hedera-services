package com.hedera.node.app.service.freeze;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import org.jetbrains.annotations.NotNull;

public interface FreezeService extends Service {
    @NotNull
    @Override
    FreezePreTransactionHandler createPreTransactionHandler(@NotNull States states);
}
