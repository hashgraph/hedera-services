package com.hedera.node.app.service.util;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import org.jetbrains.annotations.NotNull;

public interface UtilService extends Service {
    @NotNull
    @Override
    UtilPreTransactionHandler createPreTransactionHandler(@NotNull States states);
}
