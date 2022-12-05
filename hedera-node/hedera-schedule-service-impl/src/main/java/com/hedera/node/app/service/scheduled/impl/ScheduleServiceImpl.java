package com.hedera.node.app.service.scheduled.impl;

import com.hedera.node.app.service.scheduled.SchedulePreTransactionHandler;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.spi.CallContext;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

public class ScheduleServiceImpl implements ScheduleService {
    private final CallContext callContext;
    public ScheduleServiceImpl(final CallContext callContext){
        this.callContext = callContext;
    }
    @NonNull
    @Override
    public SchedulePreTransactionHandler createPreTransactionHandler(@NonNull States states, @NonNull PreHandleContext ctx) {
        Objects.requireNonNull(states);
        Objects.requireNonNull(ctx);
        return new SchedulePreTransactionHandlerImpl(callContext, ctx);
    }
}
