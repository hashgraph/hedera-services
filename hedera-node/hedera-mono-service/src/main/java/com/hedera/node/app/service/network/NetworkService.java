package com.hedera.node.app.service.network;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import org.jetbrains.annotations.NotNull;

public interface NetworkService extends Service {
    @NotNull
    @Override
    NetworkPreTransactionHandler createPreTransactionHandler(@NotNull States states);
}
