/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.node.app.service.schedule.impl;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleGetInfoHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ScheduleService} {@link Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleCreateHandler scheduleCreateHandler;

    private final ScheduleDeleteHandler scheduleDeleteHandler;

    private final ScheduleGetInfoHandler scheduleGetInfoHandler;

    private final ScheduleSignHandler scheduleSignHandler;

    public ScheduleServiceImpl() {
        this.scheduleCreateHandler = new ScheduleCreateHandler();
        this.scheduleDeleteHandler = new ScheduleDeleteHandler();
        this.scheduleGetInfoHandler = new ScheduleGetInfoHandler();
        this.scheduleSignHandler = new ScheduleSignHandler();
    }

    @NonNull
    public ScheduleCreateHandler getScheduleCreateHandler() {
        return scheduleCreateHandler;
    }

    @NonNull
    public ScheduleDeleteHandler getScheduleDeleteHandler() {
        return scheduleDeleteHandler;
    }

    @NonNull
    public ScheduleGetInfoHandler getScheduleGetInfoHandler() {
        return scheduleGetInfoHandler;
    }

    @NonNull
    public ScheduleSignHandler getScheduleSignHandler() {
        return scheduleSignHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(scheduleCreateHandler, scheduleDeleteHandler, scheduleSignHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(scheduleGetInfoHandler);
    }
}
