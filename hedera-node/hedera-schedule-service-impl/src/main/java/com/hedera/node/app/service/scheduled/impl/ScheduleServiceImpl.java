package com.hedera.node.app.service.scheduled.impl;

import com.hedera.node.app.service.scheduled.SchedulePreTransactionHandler;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ScheduleServiceImpl implements ScheduleService {
    @NonNull
    @Override
    public SchedulePreTransactionHandler createPreTransactionHandler(@NonNull States states, @NonNull PreHandleContext ctx) {
        return null;
    }
}
