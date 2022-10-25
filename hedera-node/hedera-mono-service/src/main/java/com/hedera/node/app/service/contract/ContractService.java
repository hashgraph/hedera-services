package com.hedera.node.app.service.contract;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import org.jetbrains.annotations.NotNull;

public interface ContractService extends Service {
    @NotNull
    @Override
    ContractPreTransactionHandler createPreTransactionHandler(@NotNull States states);
}
