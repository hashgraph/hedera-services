package com.hedera.node.app.service.schedule;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import org.jetbrains.annotations.NotNull;

public interface ScheduleService extends Service {
    @NotNull
    @Override
    SchedulePreTransactionHandler createPreTransactionHandler(@NotNull States states);
}
